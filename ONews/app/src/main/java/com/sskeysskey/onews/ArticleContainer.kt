package com.sskeysskey.onews

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun ArticleContainer(
    initialArticleId: UUID,
    navigationContext: String, // "source" or "all"
    viewModel: NewsViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context, coroutineScope) }

    val audioPlayerManager: AudioPlayerManager = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(AudioPlayerManager::class.java)) {
                    return AudioPlayerManager(context.applicationContext as Application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    val allArticles by viewModel.allArticlesSortedForDisplay.collectAsState()
    val findArticle = { id: UUID -> allArticles.find { it.article.id == id } }

    var currentArticleWithSource by remember { mutableStateOf(findArticle(initialArticleId)) }
    var showNoNextToast by remember { mutableStateOf(false) }

    // --- 新增：图片下载状态 ---
    var isDownloadingImages by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0.0f) }
    var downloadProgressText by remember { mutableStateOf("") }

    LaunchedEffect(currentArticleWithSource) {
        currentArticleWithSource?.let {
            if (!it.article.isRead) {
                delay(2000)
                viewModel.markAsRead(it.article.id)
            }

            // --- 新增：静默预下载下一篇文章的图片 ---
            val next = viewModel.findNextUnread(
                it.article.id,
                if (navigationContext == "source") it.sourceName else null
            )
            if (next != null && next.article.images.isNotEmpty()) {
                updateManager.preDownloadImagesForArticleSilently(next.article.timestamp, next.article.images)
            }
        }
    }

    // 切换到下一篇文章的通用逻辑
    val switchToNextArticle = { shouldAutoplayNext: Boolean ->
        val next = viewModel.findNextUnread(
            currentArticleWithSource!!.article.id,
            if (navigationContext == "source") currentArticleWithSource!!.sourceName else null
        )
        if (next != null) {
            val proceedToNext = {
                currentArticleWithSource = next
                if (shouldAutoplayNext) {
                    audioPlayerManager.startPlayback(next.article.articleContent, next.article.topic)
                }
            }

            if (next.article.images.isEmpty() || updateManager.checkIfImagesExistForArticle(next.article.timestamp, next.article.images)) {
                proceedToNext()
            } else {
                coroutineScope.launch {
                    isDownloadingImages = true
                    downloadProgress = 0f
                    downloadProgressText = "准备中..."
                    try {
                        updateManager.downloadImagesForArticle(next.article.timestamp, next.article.images) { current, total ->
                            downloadProgress = if (total > 0) current.toFloat() / total else 0f
                            downloadProgressText = "已下载 $current / $total"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isDownloadingImages = false
                        proceedToNext()
                    }
                }
            }
        } else {
            showNoNextToast = true
        }
    }

    LaunchedEffect(audioPlayerManager, navigationContext, currentArticleWithSource) {
        audioPlayerManager.onNextRequested = {
            switchToNextArticle(true)
        }
    }

    val isPlaybackActive by audioPlayerManager.isPlaybackActive.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        currentArticleWithSource?.let { item ->
            // 使用 key 使得文章切换时重建 ArticleDetail（重置滚动位置到顶部）
            key(item.article.id) {
                ArticleDetail(
                    article = item.article,
                    sourceName = item.sourceName,
                    viewModel = viewModel,
                    audioPlayerManager = audioPlayerManager,
                    navController = navController,
                    isAudioPlayerVisible = isPlaybackActive,
                    requestNextArticle = {
                        audioPlayerManager.stop()
                        switchToNextArticle(false)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = isPlaybackActive,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            AudioPlayerView(playerManager = audioPlayerManager)
        }

        if (showNoNextToast) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
                action = { Button(onClick = { showNoNextToast = false }) { Text("OK") } }
            ) {
                Text("该分组内已无更多文章")
            }
            LaunchedEffect(Unit) {
                delay(2000)
                showNoNextToast = false
            }
        }

        // --- 新增：图片下载遮罩层 ---
        if (isDownloadingImages) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("正在加载图片", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(0.6f),
                        color = Color.White,
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(downloadProgressText, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayerManager.stop()
        }
    }
}