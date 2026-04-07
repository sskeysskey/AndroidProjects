package com.sskeysskey.onews

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val clipboardManager = LocalClipboardManager.current

    // 控制分享弹窗和微信引导弹窗的状态
    var showShareSheet by remember { mutableStateOf(false) }
    var showWeChatGuide by remember { mutableStateOf(false) }

    // 分别监听标题和正文的字体缩放比例
    val titleFontSizeScale by viewModel.titleFontSizeScale.collectAsState()
    val bodyFontSizeScale by viewModel.bodyFontSizeScale.collectAsState()
    
    // 控制字体调整弹窗的状态
    var showFontSizeSheet by remember { mutableStateOf(false) }

    // 准备分享的文本：前7段 + iOS同款后缀
    val textToShare = remember(paragraphs) {
        val topParagraphs = paragraphs.take(7).joinToString("\n\n")
        val footer = "\n\n...\n\n阅读全文请前往Google Play免费下载“国外消息“APP"
        topParagraphs + footer
    }

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
                    // 添加字体调整按钮
                    IconButton(onClick = { showFontSizeSheet = true }) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Adjust Font Size")
                    }

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
                    IconButton(onClick = { 
                        // 1. 复制内容到剪贴板
                        clipboardManager.setText(AnnotatedString(textToShare))
                        // 2. 弹出分享菜单
                        showShareSheet = true
                    }) {
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
            
            // 应用标题字体缩放比例
            Text(
                text = article.topic, 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineMedium.fontSize * titleFontSizeScale,
                lineHeight = MaterialTheme.typography.headlineMedium.lineHeight * titleFontSizeScale
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(sourceName.replace("_", " "), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))

            // 渲染图文交织的内容
            contentItems.forEach { item ->
                when (item) {
                    is ContentItem.TextItem -> {
                        // 应用正文字体缩放比例
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp * bodyFontSizeScale, // 基础大小乘以正文缩放比例
                            lineHeight = 28.sp * bodyFontSizeScale, // 行高也等比例缩放
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

    // 1. 添加字体调整的 BottomSheet
    if (showFontSizeSheet) {
        ModalBottomSheet(onDismissRequest = { showFontSizeSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("字体大小调整", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                
                // 标题字体调整滑块
                Text("标题大小", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("A", fontSize = 14.sp, color = Color.Gray)
                    Slider(
                        value = titleFontSizeScale,
                        onValueChange = { viewModel.updateTitleFontSizeScale(it) },
                        valueRange = 0.8f..1.8f,
                        steps = 9,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    )
                    Text("A", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // 正文字体调整滑块
                Text("正文大小", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("A", fontSize = 14.sp, color = Color.Gray)
                    Slider(
                        value = bodyFontSizeScale,
                        onValueChange = { viewModel.updateBodyFontSizeScale(it) },
                        valueRange = 0.8f..1.8f,
                        steps = 9,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    )
                    Text("A", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 实时预览文字
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Gray.copy(alpha = 0.1f), shape = MaterialTheme.shapes.medium)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "预览：这是一个测试标题",
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * titleFontSizeScale,
                        lineHeight = MaterialTheme.typography.headlineMedium.lineHeight * titleFontSizeScale
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "预览：这是一段测试正文。通过拖动上方的滑块，您可以分别调整标题和正文的显示大小，以获得最佳的阅读体验。",
                        fontSize = 16.sp * bodyFontSizeScale,
                        lineHeight = 28.sp * bodyFontSizeScale,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }

    // 2. 自定义分享菜单 BottomSheet
    if (showShareSheet) {
        ModalBottomSheet(onDismissRequest = { showShareSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("分享至", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 微信按钮
                    ShareIconBtn(
                        icon = Icons.Default.ChatBubble, 
                        color = Color(0xFF4CAF50), 
                        title = "微信"
                    ) {
                        showShareSheet = false
                        showWeChatGuide = true
                    }
                    
                    Spacer(modifier = Modifier.width(40.dp))
                    
                    // 更多按钮 (调用系统分享)
                    ShareIconBtn(
                        icon = Icons.Default.MoreHoriz, 
                        color = Color.Gray, 
                        title = "更多"
                    ) {
                        showShareSheet = false
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, textToShare)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "分享至")
                        context.startActivity(shareIntent)
                    }
                }
            }
        }
    }

    // 3. 微信手动粘贴引导页 BottomSheet
    if (showWeChatGuide) {
        ModalBottomSheet(onDismissRequest = { showWeChatGuide = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Copied",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(60.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("文章内容已复制", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "由于微信限制，请手动去微信粘贴文章内容",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.tencent.mm")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            Toast.makeText(context, "未安装微信", Toast.LENGTH_SHORT).show()
                        }
                        showWeChatGuide = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("打开微信")
                }
            }
        }
    }
}

// 辅助按钮视图
@Composable
fun ShareIconBtn(icon: ImageVector, color: Color, title: String, action: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = action)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}