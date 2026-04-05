package com.sskeysskey.onews

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

private sealed class ContentItem {
    data class TextItem(val text: String) : ContentItem()
    data class ImageItem(val file: File, val name: String) : ContentItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetail(
    article: Article,
    sourceName: String,
    viewModel: NewsViewModel,
    audioPlayerManager: AudioPlayerManager,
    navController: NavController,
    isAudioPlayerVisible: Boolean,
    requestNextArticle: () -> Unit
) {
    val scrollState = rememberScrollState()
    val paragraphs = remember(article.articleContent) {
        article.articleContent.split("\n").filter { it.isNotBlank() }
    }
    
    // 监听播放器是否处于激活状态（包含播放、暂停、缓冲等）
    val isPlaybackActive by audioPlayerManager.isPlaybackActive.collectAsState()

    val context = LocalContext.current

    // 构建图文交织的内容列表
    val contentItems = remember(article.id) {
        val documentsDirectory = context.filesDir
        val imageDir = File(documentsDirectory, "news_images_${article.timestamp}")

        // 收集实际存在的图片文件
        val existingImages = article.images.mapNotNull { imageName ->
            val file = File(imageDir, imageName)
            if (file.exists()) Pair(imageName, file) else null
        }

        val totalImages = existingImages.size
        val totalParagraphs = paragraphs.size

        when {
            totalImages == 0 -> {
                // 无图片，纯文本
                paragraphs.map { ContentItem.TextItem(it) }
            }
            totalParagraphs == 0 -> {
                // 无文本，纯图片
                existingImages.map { ContentItem.ImageItem(it.second, it.first) }
            }
            else -> {
                val items = mutableListOf<ContentItem>()

                // ★ 新规则：第一张图片放在文章开头（第一段文本之前）
                items.add(ContentItem.ImageItem(existingImages[0].second, existingImages[0].first))

                // 剩余图片
                val remainingImages = existingImages.drop(1)
                val totalRemainingImages = remainingImages.size

                if (totalRemainingImages == 0) {
                    // 只有一张图片，已经放在开头了，后面全是段落
                    paragraphs.forEach { items.add(ContentItem.TextItem(it)) }
                } else {
                    // 剩余图片按照原来的均匀分布规则插入段落之间
                    val imagesToDistribute = min(totalRemainingImages, totalParagraphs)
                    val step = totalParagraphs.toFloat() / (imagesToDistribute + 1)

                    val insertMap = mutableMapOf<Int, MutableList<Int>>()
                    for (i in 0 until imagesToDistribute) {
                        val pos = if (imagesToDistribute >= totalParagraphs) {
                            i
                        } else {
                            val rawPos = ((i + 1) * step).roundToInt() - 1
                            rawPos.coerceIn(0, totalParagraphs - 1)
                        }
                        insertMap.getOrPut(pos) { mutableListOf() }.add(i)
                    }

                    for (i in paragraphs.indices) {
                        items.add(ContentItem.TextItem(paragraphs[i]))
                        insertMap[i]?.forEach { imgIdx ->
                            items.add(ContentItem.ImageItem(remainingImages[imgIdx].second, remainingImages[imgIdx].first))
                        }
                    }

                    // 多余的图片追加到末尾
                    for (i in imagesToDistribute until totalRemainingImages) {
                        items.add(ContentItem.ImageItem(remainingImages[i].second, remainingImages[i].first))
                    }
                }

                items.toList()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sourceName.replace("_", " ")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isPlaybackActive) {
                            // 如果当前处于激活状态，点击则彻底关闭音频播放
                            audioPlayerManager.stop()
                        } else {
                            // 否则开始播放
                            audioPlayerManager.startPlayback(article.articleContent, article.topic)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Toggle Audio",
                            // 激活状态下图标变为主题色，否则为默认颜色
                            tint = if (isPlaybackActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { /* 分享逻辑 */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(article.topic, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(sourceName.replace("_", " "), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))

            // 渲染图文交织的内容
            contentItems.forEach { item ->
                when (item) {
                    is ContentItem.TextItem -> {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 28.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    is ContentItem.ImageItem -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.file)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = requestNextArticle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("阅读下一篇文章")
            }

            // 当音频播放面板可见时，增加额外的底部间距，避免内容和按钮被遮挡
            Spacer(modifier = Modifier.height(if (isAudioPlayerVisible) 200.dp else 24.dp))
        }
    }
}