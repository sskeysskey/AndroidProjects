package com.sskeysskey.onews

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// 新闻源模型：增加 sourceId 和 nameEn
data class NewsSource(
    val id: UUID = UUID.randomUUID(),
    val sourceId: String,
    val name: String,
    val nameEn: String = "",
    val articles: List<Article>,
    val unreadCount: Int = articles.count { !it.isRead }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceList(
    navController: NavController,
    viewModel: NewsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resourceManager = remember { UpdateManager(context, scope) }

    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val totalUnreadCount by viewModel.totalUnreadCount.collectAsStateWithLifecycle()
    val allArticles by viewModel.allArticlesSortedForDisplay.collectAsStateWithLifecycle()
    
    val isSyncing by resourceManager.isSyncing.collectAsState()
    val syncMessage by resourceManager.syncMessage.collectAsState()

    var showErrorAlert by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // --- 搜索状态 --- 使用 rememberSaveable 保持状态
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var collapsedGroups by remember { mutableStateOf(setOf<String>()) }
    val primaryColor = MaterialTheme.colorScheme.primary

    // --- 图片下载状态 ---
    var isDownloadingImages by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0.0f) }
    var downloadProgressText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadNews()
        scope.launch {
            try {
                resourceManager.checkAndDownloadUpdates()
                viewModel.loadNews()
            } catch (_: Exception) { }
        }
    }

    val lastError by resourceManager.lastErrorMessage.collectAsState()
    LaunchedEffect(lastError) {
        lastError?.let {
            errorMessage = it
            showErrorAlert = true
        }
    }

    // 搜索过滤逻辑
    val filteredArticles = remember(allArticles, searchText, isSearching) {
        if (searchText.isNotBlank() && isSearching) {
            allArticles.mapNotNull { item ->
                val titleMatch = item.article.topic.contains(searchText, ignoreCase = true)
                val contentMatch = item.article.articleContent.contains(searchText, ignoreCase = true)

                if (titleMatch || contentMatch) {
                    val tSnippet = if (titleMatch) highlightFullText(item.article.topic, searchText, primaryColor) else null
                    val cSnippet = if (contentMatch) generateSnippet(item.article.articleContent, searchText, primaryColor) else null

                    SearchResult(
                        articleWithSource = item,
                        isTitleMatch = titleMatch,
                        isContentMatch = contentMatch,
                        titleSnippet = tSnippet,
                        contentSnippet = cSnippet
                    )
                } else null
            }.sortedByDescending { it.isTitleMatch }
             .groupBy { it.articleWithSource.article.timestamp }
        } else {
            emptyMap()
        }
    }

    val sortedTimestamps = remember(filteredArticles) {
        filteredArticles.keys.sortedWith(
            compareByDescending<String> { timestamp ->
                filteredArticles[timestamp]?.any { it.isTitleMatch } == true
            }.thenByDescending { it }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackgroundImage()
        Box(modifier = Modifier.fillMaxSize().background(WelcomeBackgroundOverlay))

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    actions = {
                        IconButton(onClick = {
                            isSearching = !isSearching
                            if (!isSearching) {
                                searchText = ""
                                keyboardController?.hide()
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        IconButton(onClick = { navController.navigate("add_source/false") }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Source", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        resourceManager.checkAndDownloadUpdates()
                                        viewModel.loadNews()
                                    } catch (_: Exception) { }
                                }
                            },
                            enabled = !isSyncing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            if (isSearching) {
                // 搜索界面
                Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }

                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("搜索所有文章", color = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                            cursorColor = Color.White
                        )
                    )

                    if (searchText.isNotBlank() && filteredArticles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("没有匹配的文章", color = Color.White.copy(alpha = 0.7f))
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sortedTimestamps.forEach { timestamp ->
                                val groupArticles = filteredArticles[timestamp] ?: emptyList()
                                val isCollapsed = collapsedGroups.contains(timestamp)

                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.1f))
                                            .clickable {
                                                collapsedGroups = if (isCollapsed) collapsedGroups - timestamp else collapsedGroups + timestamp
                                            }
                                            .padding(horizontal = 8.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatTimestamp(timestamp)} (${groupArticles.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = if (isCollapsed) "展开" else "折叠",
                                            tint = Color.White
                                        )
                                    }
                                }

                                if (!isCollapsed) {
                                    items(groupArticles, key = { it.articleWithSource.article.id }) { searchResult ->
                                        val item = searchResult.articleWithSource
                                        ArticleRowCard(
                                            searchResult = searchResult,
                                            sourceName = item.sourceName,
                                            isSearching = isSearching,
                                            onClick = {
                                                val navigateAction = {
                                                    navController.navigate("article_container/${item.article.id}/${item.sourceName}/all")
                                                }
                                                if (item.article.images.isEmpty() || resourceManager.checkIfImagesExistForArticle(item.article.timestamp, item.article.images)) {
                                                    navigateAction()
                                                } else {
                                                    scope.launch {
                                                        isDownloadingImages = true
                                                        downloadProgress = 0f
                                                        downloadProgressText = "准备中..."
                                                        try {
                                                            resourceManager.downloadImagesForArticle(item.article.timestamp, item.article.images) { current, total ->
                                                                downloadProgress = if (total > 0) current.toFloat() / total else 0f
                                                                downloadProgressText = "已下载 $current / $total"
                                                            }
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        } finally {
                                                            isDownloadingImages = false
                                                            navigateAction()
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 原有的新闻源列表界面
                if (sources.isEmpty() && !isSyncing) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("您还没有订阅任何新闻源", color = Color.White.copy(alpha = 0.9f))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { navController.navigate("add_source/false") }) {
                                Text("点击这里添加")
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp)) {
                        item {
                            SourceRow(
                                name = "ALL",
                                unreadCount = totalUnreadCount,
                                onClick = { navController.navigate("all_articles") },
                                isAll = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(sources, key = { it.id }) { source ->
                            SourceRow(
                                name = source.name,
                                unreadCount = source.unreadCount,
                                onClick = { navController.navigate("article_list/${source.name}") }
                            )
                        }
                    }
                }
            }
        }

        if (isSyncing) SyncOverlay(resourceManager)

        if (showErrorAlert) {
            AlertDialog(
                onDismissRequest = { showErrorAlert = false },
                title = { Text("错误") },
                text = { Text(errorMessage) },
                confirmButton = { Button(onClick = { showErrorAlert = false }) { Text("好的") } }
            )
        }

        // 图片下载遮罩层
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
}

@Composable
private fun SourceRow(name: String, unreadCount: Int, onClick: () -> Unit, isAll: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = if (isAll) 20.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name.replace("_", " "),
            color = Color.White,
            fontSize = if (isAll) 28.sp else 18.sp,
            fontWeight = if (isAll) FontWeight.Bold else FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = unreadCount.toString(),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = if (isAll) 28.sp else 18.sp,
            fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SyncOverlay(resourceManager: UpdateManager) {
    val isDownloading by resourceManager.isDownloading.collectAsState()
    val syncMessage by resourceManager.syncMessage.collectAsState()
    val progress by resourceManager.downloadProgress.collectAsState()
    val progressText by resourceManager.progressText.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 50.dp)
        ) {
            if (isDownloading) {
                Text(syncMessage, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(progressText, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            } else {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text(syncMessage, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceView(
    navController: NavController,
    isFirstTimeSetup: Boolean,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 存储 Pair<SourceId, DisplayName>
    var allAvailableSources by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val subscribedSources by SubscriptionManager.subscribedSources.collectAsState()

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val documentsDirectory = context.filesDir
                val allFiles = documentsDirectory.listFiles { _, name ->
                    name.startsWith("onews_") && name.endsWith(".json")
                }

                if (allFiles.isNullOrEmpty()) {
                    throw Exception("在目录中没有找到任何 'onews_*.json' 文件。\n请先返回主页同步资源。")
                }

                val prefs = context.getSharedPreferences("ONewsPrefs", Context.MODE_PRIVATE)
                val mappingsJson = prefs.getString("source_mappings", "{}")
                val typeMap = object : TypeToken<Map<String, String>>() {}.type
                val sourceMappings: Map<String, String> = Gson().fromJson(mappingsJson, typeMap) ?: emptyMap()

                val union = mutableMapOf<String, String>()
                val gson = Gson()
                val articleType = object : TypeToken<Map<String, List<Article>>>() {}.type

                for (file in allFiles) {
                    try {
                        val jsonString = file.readText()
                        val decoded: Map<String, List<Article>> = gson.fromJson(jsonString, articleType)

                        decoded.forEach { (fileKeyName, articles) ->
                            val sourceId = articles.firstOrNull()?.sourceId
                            if (!sourceId.isNullOrEmpty()) {
                                val rawMappingName = sourceMappings[sourceId] ?: fileKeyName
                                val cnName = rawMappingName.split("|").firstOrNull() ?: rawMappingName
                                union[sourceId] = cnName
                            }
                        }
                    } catch (e: Exception) {
                        println("解析 ${file.name} 失败: ${e.localizedMessage}")
                    }
                }

                val preferredOrder = listOf("ft", "wsjcn", "nytimes", "bloomberg", "rfi", "nikkei", "dw", "wsj", "economist", "reuters", "washpost", "mittr", "bbc")

                withContext(Dispatchers.Main) {
                    allAvailableSources = union.entries.map { Pair(it.key, it.value) }
                        .sortedWith(Comparator { s1, s2 ->
                            val idx1 = preferredOrder.indexOf(s1.first).let { if (it == -1) Int.MAX_VALUE else it }
                            val idx2 = preferredOrder.indexOf(s2.first).let { if (it == -1) Int.MAX_VALUE else it }
                            if (idx1 != idx2) idx1.compareTo(idx2) else s1.second.compareTo(s2.second)
                        })
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.message
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加新闻源") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White)
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            AppBackgroundImage()
            Box(modifier = Modifier.fillMaxSize().background(WelcomeBackgroundOverlay))

            when {
                isLoading -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator(color = Color.White) }
                errorMessage != null -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(20.dp)) { Text(errorMessage!!, color = Color.White, textAlign = TextAlign.Center) }
                else -> {
                    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(allAvailableSources) { source ->
                                SourceItemRow(
                                    sourceName = source.second, // 显示中文名
                                    isSubscribed = subscribedSources.contains(source.first), // 判断 sourceId
                                    onToggle = { SubscriptionManager.toggleSubscription(source.first) }
                                )
                            }
                        }
                        Button(
                            onClick = { if (isFirstTimeSetup) onComplete() else navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            enabled = subscribedSources.isNotEmpty()
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItemRow(sourceName: String, isSubscribed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = sourceName, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (isSubscribed) {
            Text("已添加", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
            Icon(Icons.Default.RemoveCircle, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp).clickable(onClick = onToggle))
        } else {
            Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp).clickable(onClick = onToggle))
        }
    }
}

@SuppressLint("StaticFieldLeak")
object SubscriptionManager {
    private const val PREFS_NAME = "ONewsPrefs"
    private const val SUBSCRIBED_SOURCES_KEY = "subscribedNewsSources"
    private lateinit var prefs: SharedPreferences

    private val _subscribedSources = MutableStateFlow<Set<String>>(emptySet())
    val subscribedSources = _subscribedSources.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSubscribedSources()
    }

    private fun loadSubscribedSources() {
        _subscribedSources.value = prefs.getStringSet(SUBSCRIBED_SOURCES_KEY, emptySet()) ?: emptySet()
    }

    private fun saveSubscribedSources() {
        prefs.edit().putStringSet(SUBSCRIBED_SOURCES_KEY, _subscribedSources.value).apply()
    }

    fun isSubscribed(to: String): Boolean = _subscribedSources.value.contains(to)

    fun addSubscription(sourceId: String) {
        _subscribedSources.value = _subscribedSources.value + sourceId
        saveSubscribedSources()
    }

    fun removeSubscription(sourceId: String) {
        _subscribedSources.value = _subscribedSources.value - sourceId
        saveSubscribedSources()
    }

    fun toggleSubscription(sourceId: String) {
        if (isSubscribed(sourceId)) removeSubscription(sourceId) else addSubscription(sourceId)
    }
}