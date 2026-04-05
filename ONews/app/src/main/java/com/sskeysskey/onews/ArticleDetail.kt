package com.sskeysskey.onews

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // <-- 已添加
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetail(
    article: Article,
    sourceName: String,
    viewModel: NewsViewModel,
    audioPlayerManager: AudioPlayerManager,
    navController: NavController,
    requestNextArticle: () -> Unit
) {
    val scrollState = rememberScrollState()
    val paragraphs = remember(article.articleContent) {
        article.articleContent.split("\n").filter { it.isNotBlank() }
    }
    val isPlaying by audioPlayerManager.isPlaying.collectAsState()

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
                        if (isPlaying) {
                            audioPlayerManager.stop()
                        } else {
                            audioPlayerManager.startPlayback(article.articleContent, article.topic)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play Audio", tint = if(isPlaying) MaterialTheme.colorScheme.primary else LocalContentColor.current)
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

            // 图片和段落
            val context = LocalContext.current
            val documentsDirectory = context.filesDir
            val imageDir = File(documentsDirectory, "news_images_${article.timestamp}")

            article.images.forEach { imageName ->
                val imageFile = File(imageDir, imageName)
                if (imageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = imageName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }

            paragraphs.forEach { paragraph ->
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 28.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = requestNextArticle,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("读取下一篇")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}