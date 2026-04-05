package com.sskeysskey.onews

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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

// ==================== UI ====================

@Composable
fun AudioPlayerView(playerManager: AudioPlayerManager) {
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isSynthesizing by playerManager.isSynthesizing.collectAsState()
    val progress by playerManager.progress.collectAsState()
    val currentTime by playerManager.currentTimeString.collectAsState()
    val duration by playerManager.durationString.collectAsState()
    val isAutoPlayEnabled by playerManager.isAutoPlayEnabled
    val playbackRate by playerManager.playbackRate
    val ttsReady by playerManager.isTtsReady.collectAsState()
    val chunkInfo by playerManager.chunkInfo.collectAsState()

    // 倍速下拉菜单状态
    var showSpeedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 右上角的关闭按钮
            IconButton(
                onClick = { playerManager.stop() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Audio Player",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {

                // TTS 不可用时显示提示
                if (!ttsReady) {
                    Text(
                        "⚠️ 中文语音引擎未就绪，请在系统设置中安装中文语音数据",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // 片段指示器（仅多片段时显示）
                if (chunkInfo.isNotEmpty()) {
                    Text(
                        "片段 $chunkInfo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // 进度条和时间
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(currentTime, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = progress,
                        onValueChange = { playerManager.seek(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
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

                    // 倍速选择按钮 + 下拉菜单
                    Box {
                        TextButton(onClick = { showSpeedMenu = true }) {
                            Text(
                                text = "${playbackRate}x",
                                fontWeight = FontWeight.Bold,
                                color = if (playbackRate != 1.0f)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { rate ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${rate}x",
                                            fontWeight = if (rate == playbackRate) FontWeight.Bold else FontWeight.Normal,
                                            color = if (rate == playbackRate)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        playerManager.setPlaybackRate(rate)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // 播放/暂停
                    IconButton(
                        onClick = { playerManager.playPause() },
                        enabled = !isSynthesizing && ttsReady,
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

                    // 下一篇文章
                    IconButton(onClick = { playerManager.onNextRequested() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next")
                    }
                }
            }
        }
    }
}

// ==================== Service ====================

@androidx.annotation.OptIn(UnstableApi::class)
class AudioPlaybackService : MediaSessionService() {

    companion object {
        /** 供 AudioPlayerManager 注册的回调，通知栏"下一条"按钮触发时调用 */
        var onNextArticleRequested: (() -> Unit)? = null
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()

        // 用 ForwardingPlayer 包装，使通知栏的"下一条"按文章跳转，禁用"上一条"
        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> false
                    else -> super.isCommandAvailable(command)
                }
            }

            // 始终显示"下一条"按钮（用于跳转到下一篇文章）
            override fun hasNextMediaItem(): Boolean = true

            override fun seekToNext() {
                onNextArticleRequested?.invoke()
            }

            override fun seekToNextMediaItem() {
                onNextArticleRequested?.invoke()
            }

            // 禁用"上一条"
            override fun hasPreviousMediaItem(): Boolean = false
            override fun seekToPrevious() { /* no-op */ }
            override fun seekToPreviousMediaItem() { /* no-op */ }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

// ==================== ViewModel ====================

class AudioPlayerManager(private val context: Context) : ViewModel(), TextToSpeech.OnInitListener {

    companion object {
        // 每个分块的最大字符数，越小则首次播放越快，但分块数越多
        private const val STREAMING_CHUNK_SIZE = 100
    }

    // --- 状态 ---
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

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady = _isTtsReady.asStateFlow()

    // ★ 新增：片段信息（如 "2/5"）
    private val _chunkInfo = MutableStateFlow("")
    val chunkInfo = _chunkInfo.asStateFlow()

    // --- 回调 ---
    var onNextRequested: () -> Unit = {}
    var onPlaybackFinished: () -> Unit = {}

    // --- 内部属性 ---
    private var tts: TextToSpeech? = null
    private var mediaController: MediaController? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>

    private var currentArticleTitle: String = "正在播放的文章"
    private var progressUpdateJob: Job? = null

    // ★ 新增：流式播放状态
    private var isStreamingMode = false          // 是否还有分块在合成中
    private var totalChunksToSynthesize = 0      // 总分块数
    private var chunksAddedToPlaylist = 0         // 已加入播放列表的分块数
    private var waitingForNextChunk = false       // 播放器是否在等待下一个分块

    init {
        tts = TextToSpeech(context, this)
        val sessionToken =
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture.addListener({
            mediaController = mediaControllerFuture.get()
            setupPlayerListener()
            // 注册通知栏"下一条"按钮的回调
            AudioPlaybackService.onNextArticleRequested = {
                viewModelScope.launch { onNextRequested() }
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            var result = tts?.setLanguage(Locale.CHINA)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts?.setLanguage(Locale.TAIWAN)
            }

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts?.setLanguage(Locale("zh"))
            }

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                println("TTS: 中文语音不可用 (result=$result)，尝试引导安装语音数据...")
                _isTtsReady.value = false

                try {
                    val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(installIntent)
                } catch (e: Exception) {
                    println("TTS: 无法打开语音数据安装界面: ${e.message}")
                }
            } else {
                println("TTS: 中文语音初始化成功 (result=$result)")
                _isTtsReady.value = true
            }
        } else {
            println("TTS: 引擎初始化失败 (status=$status)")
            _isTtsReady.value = false
        }
    }

    // --- 公共控制方法 ---

    fun startPlayback(text: String, title: String?) {
        if (text.isBlank()) {
            handleError("文本内容为空，无法播放。")
            return
        }

        if (!_isTtsReady.value) {
            handleError("中文语音引擎未就绪，请先在系统设置中安装中文语音数据。")
            return
        }

        prepareForNextTransition()

        currentArticleTitle = title ?: "正在播放的文章"

        viewModelScope.launch {
            _isSynthesizing.value = true
            _isPlaybackActive.value = true

            val processedText = preprocessText(text)
            val chunks = splitTextIntoChunks(processedText, STREAMING_CHUNK_SIZE)

            // 初始化流式播放状态
            totalChunksToSynthesize = chunks.size
            chunksAddedToPlaylist = 0
            isStreamingMode = chunks.size > 1
            waitingForNextChunk = false

            _chunkInfo.value = if (chunks.size > 1) "1/${chunks.size}" else ""

            synthesizeAndPlayStreaming(chunks)
        }
    }

    /**
     * ★ 核心改动：流式合成并播放。
     * 第一个分块合成完毕后立即开始播放，后续分块在后台继续合成
     * 并追加到 ExoPlayer 播放列表，播放器会自动无缝衔接。
     */
    private fun synthesizeAndPlayStreaming(chunks: List<String>) {
        var firstChunkPlayed = false

        mediaController?.clearMediaItems()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                val index = id?.removePrefix("chunk_")?.toIntOrNull() ?: return
                viewModelScope.launch {
                    val chunkFile = File(context.cacheDir, "tts_chunk_$index.wav")
                    val mediaItem = MediaItem.Builder()
                        .setUri(chunkFile.toURI().toString())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(currentArticleTitle)
                                .setArtist(if (_isAutoPlayEnabled.value) "自动连播" else "单次播放")
                                .build()
                        )
                        .build()

                    mediaController?.addMediaItem(mediaItem)
                    chunksAddedToPlaylist++

                    // 所有分块都已加入播放列表，退出流式模式
                    if (chunksAddedToPlaylist == totalChunksToSynthesize) {
                        isStreamingMode = false
                    }

                    if (!firstChunkPlayed) {
                        // 第一个分块就绪，立即开始播放
                        firstChunkPlayed = true
                        _isSynthesizing.value = false
                        mediaController?.prepare()
                        mediaController?.playbackParameters =
                            PlaybackParameters(_playbackRate.floatValue)
                        mediaController?.play()
                    } else if (waitingForNextChunk) {
                        // 播放器已播完所有已有项目，正在等新分块 → 继续播放
                        waitingForNextChunk = false
                        _isSynthesizing.value = false
                        mediaController?.let { mc ->
                            mc.seekTo(mc.mediaItemCount - 1, 0)
                            mc.prepare()
                            mc.play()
                        }
                    }
                }
            }

            override fun onError(id: String?) {
                viewModelScope.launch { handleError("TTS合成失败") }
            }

            override fun onStop(id: String?, interrupted: Boolean) {
                if (interrupted) {
                    viewModelScope.launch { _isSynthesizing.value = false }
                }
            }
        })

        // 将所有分块加入 TTS 合成队列（TTS 引擎会按顺序逐个处理）
        for ((index, chunk) in chunks.withIndex()) {
            val chunkFile = File(context.cacheDir, "tts_chunk_$index.wav")
            tts?.synthesizeToFile(chunk, Bundle(), chunkFile, "chunk_$index")
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
        _chunkInfo.value = ""
        isStreamingMode = false
        waitingForNextChunk = false
        totalChunksToSynthesize = 0
        chunksAddedToPlaylist = 0
        progressUpdateJob?.cancel()
        cleanupChunkFiles()
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
    }

    fun setPlaybackRate(rate: Float) {
        _playbackRate.floatValue = rate
        mediaController?.setPlaybackParameters(PlaybackParameters(rate))
    }

    fun prepareForNextTransition() {
        stop()
    }

    // --- 私有方法 ---

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
                    if (isStreamingMode) {
                        // 还有分块在合成中，等待下一个分块就绪后继续
                        waitingForNextChunk = true
                        _isSynthesizing.value = true
                    } else {
                        finishNaturally()
                    }
                }
            }

            // ★ 新增：当 ExoPlayer 切换到下一个播放列表项时，更新片段指示器
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (totalChunksToSynthesize > 1) {
                    val currentIndex = mediaController?.currentMediaItemIndex ?: 0
                    _chunkInfo.value = "${currentIndex + 1}/$totalChunksToSynthesize"
                }
            }
        })
    }

    private fun finishNaturally() {
        _isPlaying.value = false
        _chunkInfo.value = ""
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

    /** 清理缓存中的临时音频分块文件 */
    private fun cleanupChunkFiles() {
        try {
            context.cacheDir.listFiles()?.filter {
                it.name.startsWith("tts_chunk_") || it.name == "tts_audio.wav"
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        cleanupChunkFiles()
        MediaController.releaseFuture(mediaControllerFuture)
    }

    // --- 文本分段 ---

    private fun splitTextIntoChunks(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)

        val chunks = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[。！？；\n.!?;])"))
        val currentChunk = StringBuilder()

        for (sentence in sentences) {
            if (sentence.isEmpty()) continue
            if (currentChunk.length + sentence.length > maxLen && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
                currentChunk.clear()
            }
            if (sentence.length > maxLen) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk.clear()
                }
                var start = 0
                while (start < sentence.length) {
                    val end = minOf(start + maxLen, sentence.length)
                    chunks.add(sentence.substring(start, end))
                    start = end
                }
            } else {
                currentChunk.append(sentence)
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }
        return chunks
    }

    // --- 文本预处理逻辑 ---

    private fun preprocessText(text: String): String {
        return normalizeDash(text)
    }

    /**
    * 统一各种 Unicode 破折号/波浪号为标准短横线，
    * 避免 TTS 遇到生僻字符时出现异常。
    */
    private fun normalizeDash(text: String): String {
        val dashes = listOf("--", "—", "―", "–", "－", "‑", "‒", "〜", "~", "～", "----")
        var t = text
        dashes.forEach { d -> t = t.replace(d, "-") }
        while (t.contains("--")) {
            t = t.replace("--", "-")
        }
        return t
    }
}