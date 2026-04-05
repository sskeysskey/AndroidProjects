package com.sskeysskey.onews

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AudioPlayerView(playerManager: AudioPlayerManager) {
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isSynthesizing by playerManager.isSynthesizing.collectAsState()
    val progress by playerManager.progress.collectAsState()
    val currentTime by playerManager.currentTimeString.collectAsState()
    val duration by playerManager.durationString.collectAsState()
    val isAutoPlayEnabled by playerManager.isAutoPlayEnabled
    val playbackRate by playerManager.playbackRate

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 进度条和时间
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentTime, style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = progress,
                    onValueChange = { playerManager.seek(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(duration, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 连播开关
                IconButton(onClick = { playerManager.setAutoPlayEnabled(!isAutoPlayEnabled) }) {
                    Icon(
                        imageVector = if (isAutoPlayEnabled) Icons.Default.Repeat else Icons.Default.RepeatOne,
                        contentDescription = "Toggle Autoplay",
                        tint = if (isAutoPlayEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }

                // 播放/暂停
                IconButton(
                    onClick = { playerManager.playPause() },
                    enabled = !isSynthesizing,
                    modifier = Modifier.size(56.dp)
                ) {
                    if (isSynthesizing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 下一首
                IconButton(onClick = { playerManager.onNextRequested() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

class AudioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    // 在 Service 创建时初始化 Player 和 MediaSession
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    // 当 UI (Activity) 连接到此服务时，返回 MediaSession Token
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // 当应用从最近任务列表中移除时，清理资源
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0) {
            // 如果没有在播放，就停止服务
            stopSelf()
        }
    }

    // 清理资源
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

class AudioPlayerManager(private val context: Context) : ViewModel(), TextToSpeech.OnInitListener {

    // --- 状态测试 ---
    private val _isPlaybackActive = MutableStateFlow(false)
    val isPlaybackActive = _isPlaybackActive.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing = _isSynthesizing.asStateFlow()

    private val _progress = MutableStateFlow(0.0f)
    val progress = _progress.asStateFlow()

    private val _currentTimeString = MutableStateFlow("00:00")
    val currentTimeString = _currentTimeString.asStateFlow()

    private val _durationString = MutableStateFlow("00:00")
    val durationString = _durationString.asStateFlow()

    private val _isAutoPlayEnabled = mutableStateOf(true)
    val isAutoPlayEnabled: State<Boolean> = _isAutoPlayEnabled

    private val _playbackRate = mutableFloatStateOf(1.0f)
    val playbackRate: State<Float> = _playbackRate

    // --- 回调 ---
    var onNextRequested: () -> Unit = {}
    var onPlaybackFinished: () -> Unit = {}

    // --- 内部属性 ---
    private var tts: TextToSpeech? = null
    private var mediaController: MediaController? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>

    private var currentArticleTitle: String = "正在播放的文章"
    private var progressUpdateJob: Job? = null

    init {
        tts = TextToSpeech(context, this)
        val sessionToken =
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture.addListener({
            mediaController = mediaControllerFuture.get()
            setupPlayerListener()
        }, MoreExecutors.directExecutor())
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 默认设置为中文，可以根据文本内容动态改变
            tts?.language = Locale.CHINESE
        } else {
            println("TTS Initialization failed.")
        }
    }

    // --- 公共控制方法 ---

    fun startPlayback(text: String, title: String?) {
        if (text.isBlank()) {
            handleError("文本内容为空，无法播放。")
            return
        }

        prepareForNextTransition()

        currentArticleTitle = title ?: "正在播放的文章"

        viewModelScope.launch {
            _isSynthesizing.value = true
            _isPlaybackActive.value = true

            val processedText = preprocessText(text)
            val tempFile = File(context.cacheDir, "tts_audio.wav")
            val utteranceId = UUID.randomUUID().toString()

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        viewModelScope.launch {
                            _isSynthesizing.value = false
                            playAudioFile(tempFile)
                        }
                    }
                }
                override fun onError(id: String?) { handleError("TTS合成失败") }
                override fun onStop(id: String?, interrupted: Boolean) {
                    if (interrupted) {
                        _isSynthesizing.value = false
                    }
                }
            })

            val params = Bundle()
            tts?.synthesizeToFile(processedText, params, tempFile, utteranceId)
        }
    }

    fun playPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun stop() {
        tts?.stop()
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _isPlaybackActive.value = false
        _isPlaying.value = false
        _isSynthesizing.value = false
        _progress.value = 0.0f
        _currentTimeString.value = "00:00"
        _durationString.value = "00:00"
        progressUpdateJob?.cancel()
    }

    fun seek(to: Float) {
        mediaController?.let {
            val duration = it.duration
            if (duration > 0) {
                it.seekTo((duration * to).toLong())
            }
        }
    }

    fun setAutoPlayEnabled(enabled: Boolean) {
        _isAutoPlayEnabled.value = enabled
        // 可以在这里更新通知或MediaSession元数据
    }

    fun setPlaybackRate(rate: Float) {
        _playbackRate.floatValue = rate
        mediaController?.setPlaybackParameters(PlaybackParameters(rate))
    }

    fun prepareForNextTransition() {
        stop()
    }

    // --- 私有方法 ---

    private fun playAudioFile(file: File) {
        val mediaItem = MediaItem.Builder()
            .setUri(file.toURI().toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(currentArticleTitle)
                    .setArtist(if (_isAutoPlayEnabled.value) "自动连播" else "单次播放")
                    .build()
            )
            .build()

        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare()
        mediaController?.play()
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressUpdater()
                } else {
                    stopProgressUpdater()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    finishNaturally()
                }
            }
        })
    }

    private fun finishNaturally() {
        _isPlaying.value = false
        stopProgressUpdater()
        onPlaybackFinished()
        if (_isAutoPlayEnabled.value) {
            onNextRequested()
        }
    }

    private fun startProgressUpdater() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (true) {
                val player = mediaController ?: break
                if (player.isPlaying) {
                    val duration = player.duration.coerceAtLeast(1)
                    val position = player.currentPosition
                    _progress.value = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    _currentTimeString.value = formatTime(position)
                    _durationString.value = formatTime(duration)
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdater() {
        progressUpdateJob?.cancel()
    }

    private fun handleError(message: String) {
        println("错误: $message")
        stop()
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
        MediaController.releaseFuture(mediaControllerFuture)
    }

    // --- 文本预处理逻辑 (从 Swift 翻译) ---

    private fun preprocessText(text: String): String {
        val textWithoutCommas = removeCommasFromNumbers(text)
        val normalized = normalizeDash(textWithoutCommas)
        val decimalBeforePercentWordFixed = insertDotForDecimalBeforePercentageWords(normalized)
        val processedSpecialTerms = processEnglishText(decimalBeforePercentWordFixed)
        val withYearFixed = replaceYearMentionsForChinese(processedSpecialTerms)

        val pattern = "(?<!年)([\\u4e00-\\u9fa5])(\\s*[A-Za-z]+\\s*)([\\u4e00-\\u9fa5])(?!年)".toRegex()
        return pattern.replace(withYearFixed, "$1, $2, $3")
    }

    private fun removeCommasFromNumbers(text: String): String {
        val pattern = "(\\d),(\\d{3})".toRegex()
        var result = text
        while (pattern.containsMatchIn(result)) {
            result = pattern.replace(result, "$1$2")
        }
        return result
    }

    private fun normalizeDash(text: String): String {
        val dashes = listOf("--", "—", "―", "–", "－", "‑", "‒", "〜", "~", "～", "----")
        var t = text
        dashes.forEach { d -> t = t.replace(d, "-") }
        while (t.contains("--")) {
            t = t.replace("--", "-")
        }
        return t
    }

    private fun insertDotForDecimalBeforePercentageWords(text: String): String {
        val pattern = "(?<!\\d)(\\d+)\\.(\\d+)\\s*(个百分点|百分比|百分点)".toRegex()
        return pattern.replace(text) { matchResult ->
            val (intPart, fracPart, unit) = matchResult.destructured
            "${intPart}点${fracPart}${unit}"
        }
    }

    private fun processEnglishText(input: String): String {
        var processed = input
            .replace("“", "").replace("”", "").replace("\"", "")

        processed = normalizeDash(processed)

        // 年龄段处理
        val ageRangePattern = "(?<!\\d)(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s*(岁|岁龄|年龄段)".toRegex()
        processed = ageRangePattern.replace(processed) {
            val (lStr, rStr, unit) = it.destructured
            val l = lStr.toIntOrNull()
            val r = rStr.toIntOrNull()
            if (l != null && r != null && l in 10..99 && r in 10..99) {
                "${toChineseUpperForAge(l)}到${toChineseUpperForAge(r)}$unit"
            } else {
                it.value
            }
        }

        // 学年处理: YYYY-YY学年
        val academicYearPattern = "(?<!\\d)(\\d{4})\\s*-\\s*(\\d{2})(?=\\s*学年)".toRegex()
        processed = academicYearPattern.replace(processed) {
            val (leftYear, rightYear) = it.destructured
            "${formatDigitsToChinesePerChar(leftYear)}到${formatDigitsToChinesePerChar(rightYear)}"
        }

        // 年代处理: YYYY-YY年代
        val decadeRangePattern = "(?<!\\d)(\\d{4})\\s*-\\s*(\\d{2})(?=\\s*年代)".toRegex()
        processed = decadeRangePattern.replace(processed) {
            val (leftYear, rightSuffix) = it.destructured
            "${formatDigitsToChinesePerChar(leftYear)}到${formatDigitsToChinesePerChar(rightSuffix)}"
        }

        // 数字范围 + 量词
        val units = "[人名位个只辆架件次年条份所家台篇场例天月周小时分钟秒]"
        val numberRangeWithUnitPattern = "(?<!\\d)(\\d{1,6})\\s*-\\s*(\\d{1,6})\\s*($units)".toRegex()
        processed = numberRangeWithUnitPattern.replace(processed) {
            val (left, right, unit) = it.destructured
            val l = left.toIntOrNull()
            val r = right.toIntOrNull()
            if (l != null && r != null) {
                "${readChineseNumber(l)}到${readChineseNumber(r)}$unit"
            } else {
                it.value
            }
        }

        // 一般数值范围
        val generalRangePattern = "(?<!\\d)(\\d{1,6})\\s*-\\s*(\\d{1,6})(?!\\d)".toRegex()
        processed = generalRangePattern.replace(processed, "$1到$2")

        // 术语替换
        val replacements = mapOf(
            "API" to "A.P.I", "URL" to "U.R.L", "HTTP" to "H.T.T.P", "JSON" to "Jason",
            "HTML" to "H.T.M.L", "CSS" to "C.S.S", "JS" to "J.S", "AI" to "A.I",
            "OpenAI" to "Open.A.I", "SDK" to "S.D.K", "iOS" to "i O S", "PSA" to "P.S.A",
            "Jeep" to "吉普", "EV" to "电动车", "iPhone" to "i Phone", "iPad" to "i Pad",
            "macOS" to "mac O S", "UI" to "U.I", "GUI" to "G.U.I", "CLI" to "C.L.I",
            "SQL" to "S.Q.L", "NASA" to "NASA", "JPEG" to "J.PEG", "PNG" to "P.N.G",
            "PDF" to "P.D.F", "ID" to "I.D", "vs" to "对阵", "etc" to "等等",
            "i.e" to "也就是说", "e.g" to "举例来说", "&" to "和", "+" to "加",
            "=" to "等于", "@" to "at", "~" to "到", "/" to "每", "DJI" to "大疆",
            "Insta360" to "Insta三六零", "Airbnb" to "Air.B.N.B", "参加" to "餐加",
            "K-12" to "K十二", "K12" to "K十二"
        )
        replacements.forEach { (key, value) ->
            processed = processed.replace(key, value)
        }

        return processed
    }

    private fun replaceYearMentionsForChinese(text: String): String {
        var result = text
        // 范围年份: 2017-2020年
        val rangeWithYearPattern = "(?<!\\d)(\\d{4})(?:\\s*年)?\\s*-\\s*(\\d{4})(?=\\s*(?:年|年代))".toRegex()
        result = rangeWithYearPattern.replace(result) {
            val (leftYear, rightYear) = it.destructured
            "${formatDigitsToChinesePerChar(leftYear)}到${formatDigitsToChinesePerChar(rightYear)}"
        }

        // 单个年份: 2024年
        val singleYearPattern = "(?<!\\d)(\\d{4})(?=\\s*(?:年|年代))".toRegex()
        result = singleYearPattern.replace(result) {
            formatDigitsToChinesePerChar(it.groupValues[1])
        }
        return result
    }

    private fun formatDigitsToChinesePerChar(digits: String): String {
        val map = mapOf('0' to "零", '1' to "一", '2' to "二", '3' to "三", '4' to "四", '5' to "五", '6' to "六", '7' to "七", '8' to "八", '9' to "九")
        return digits.map { map[it] }.joinToString("")
    }

    private fun toChineseUpperForAge(n: Int): String {
        val upper = listOf("零","壹","贰","叁","肆","伍","陆","柒","捌","玖")
        if (n < 10) return upper[n]
        val tens = n / 10
        val ones = n % 10
        return when {
            ones == 0 -> if (tens == 1) "十" else upper[tens] + "十"
            tens == 1 -> "十" + upper[ones]
            else -> upper[tens] + "十" + upper[ones]
        }
    }

    private fun readChineseNumber(n: Int): String {
        val digits = listOf("零","一","二","三","四","五","六","七","八","九")
        if (n < 10) return digits[n]
        if (n < 20) return "十" + if (n % 10 == 0) "" else digits[n % 10]
        if (n < 100) return digits[n / 10] + "十" + if (n % 10 == 0) "" else digits[n % 10]
        // ... 更多实现可以根据 Swift 版本精确翻译
        return n.toString() // 简化版
    }
}