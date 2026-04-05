package com.sskeysskey.onews

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun WelcomeScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resourceManager = remember { UpdateManager(context, scope) }

    var showAddSourceView by remember { mutableStateOf(false) }
    var showErrorAlert by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val isSyncing by resourceManager.isSyncing.collectAsState()
    val syncMessage by resourceManager.syncMessage.collectAsState()
    val lastError by resourceManager.lastErrorMessage.collectAsState()

    // 首次进入尝试拉取清单；失败则提示但不崩溃
    LaunchedEffect(Unit) {
        try {
            resourceManager.checkAndDownloadAllNewsManifests()
        } catch (_: Exception) {
            // 所有异常在 UpdateManager 内部已处理
        }
    }

    // 一旦 UpdateManager 给出错误提示，就弹窗一次
    LaunchedEffect(lastError) {
        lastError?.let {
            errorMessage = it
            showErrorAlert = true
        }
    }

    val hasLocal = remember { hasAnyLocalNews(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackgroundImage()
        Box(modifier = Modifier.fillMaxSize().background(WelcomeBackgroundOverlay))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "欢迎来到 ONews",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (hasLocal) {
                    "检测到本地已有数据\n您可以直接开始添加新闻源"
                } else {
                    "点击右下方按钮\n开始添加您感兴趣的新闻源"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            if (!hasLocal) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "若服务器未启动，首次可能拉取失败，您可稍后点击刷新重试。",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }
        }

        // 按钮区域
        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            if (!isSyncing) {
                // 刷新按钮
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            try {
                                resourceManager.checkAndDownloadUpdates()
                            } catch (_: Exception) {
                                // 错误通过 lastErrorMessage 呈现
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomStart),
                    shape = CircleShape,
                    containerColor = Color.Black.copy(alpha = 0.3f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                }

                // 添加按钮：即使无网络也允许进入添加源（从本地 JSON 合并）
                AddSourceFab(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    onClick = { onComplete() }
                )
            }
        }

        // 同步遮罩
        if (isSyncing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = syncMessage, color = Color.White, fontSize = 18.sp)
                }
            }
        }

        // 错误弹窗
        if (showErrorAlert) {
            AlertDialog(
                onDismissRequest = { showErrorAlert = false },
                title = { Text("提示") },
                text = { Text(errorMessage) },
                confirmButton = {
                    Button(onClick = { showErrorAlert = false }) {
                        Text("好的")
                    }
                }
            )
        }
    }
}

private fun hasAnyLocalNews(context: android.content.Context): Boolean {
    val dir: File = context.filesDir
    return dir.listFiles()?.any { it.name.startsWith("onews_") && it.name.endsWith(".json") } == true
}

@Composable
private fun AddSourceFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    var ripple by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (ripple) 1.4f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "fab-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (ripple) 0f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    LaunchedEffect(Unit) {
        // 延迟一小段时间再开始动画，避免初始状态闪烁
        delay(100)
        ripple = true
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 光晕效果
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = (1 - alpha) * 0.5f))
        )

        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Source", tint = Color.White)
        }
    }
}