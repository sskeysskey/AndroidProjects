package com.sskeysskey.onews

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class ArticleFilterMode { Unread, Read }

// --- 新增：用于包装搜索结果的数据类 ---
data class SearchResult(
    val articleWithSource: ArticleWithSource,
    val isTitleMatch: Boolean = false,
    val isContentMatch: Boolean = false,
    val contentSnippet: AnnotatedString? = null
)

// 单一来源的文章列表
@Composable
fun ArticleListView(
    navController: NavController,
    sourceName: String,
    viewModel: NewsViewModel
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val source = sources.find { it.name == sourceName }

    val articles = source?.articles ?: emptyList()

    ArticleListContent(
        navController = navController,
        title = sourceName.replace("_", " "),
        articlesWithSource = articles.map { ArticleWithSource(it, sourceName) },
        viewModel = viewModel,
        isAllArticles = false
    )
}

// 所有来源的文章列表
@Composable
fun AllArticlesListView(
    navController: NavController,
    viewModel: NewsViewModel
) {
    val allArticles by viewModel.allArticlesSortedForDisplay.collectAsStateWithLifecycle()

    ArticleListContent(
        navController = navController,
        title = "ALL",
        articlesWithSource = allArticles,
        viewModel = viewModel,
        isAllArticles = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleListContent(
    navController: NavController,
    title: String,
    articlesWithSource: List<ArticleWithSource>,
    viewModel: NewsViewModel,
    isAllArticles: Boolean
) {
    var filterMode by remember { mutableStateOf(ArticleFilterMode.Unread) }
    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    // --- 图片下载状态 ---
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context, coroutineScope) }
    var isDownloadingImages by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0.0f) }
    var downloadProgressText by remember { mutableStateOf("") }

    // --- 新增：折叠状态管理 ---
    // 记录被折叠的日期（timestamp）
    var collapsedGroups by remember { mutableStateOf(setOf<String>()) }

    val primaryColor = MaterialTheme.colorScheme.primary

    // --- 修改：过滤逻辑扩展到正文并生成 SearchResult ---
    val filteredArticles = remember(articlesWithSource, filterMode, searchText, isSearching) {
        val baseList = if (searchText.isNotBlank() && isSearching) {
            articlesWithSource.mapNotNull { item ->
                val titleMatch = item.article.topic.contains(searchText, ignoreCase = true)
                val contentMatch = item.article.articleContent.contains(searchText, ignoreCase = true)

                if (titleMatch || contentMatch) {
                    val snippet = if (contentMatch) {
                        generateSnippet(item.article.articleContent, searchText, primaryColor)
                    } else null

                    SearchResult(
                        articleWithSource = item,
                        isTitleMatch = titleMatch,
                        isContentMatch = contentMatch,
                        contentSnippet = snippet
                    )
                } else {
                    null
                }
            }
        } else {
            articlesWithSource.filter {
                if (filterMode == ArticleFilterMode.Unread) !it.article.isRead else it.article.isRead
            }.map { 
                SearchResult(articleWithSource = it) 
            }
        }
        baseList.groupBy { it.articleWithSource.article.timestamp }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isSearching = !isSearching
                            if (!isSearching) searchText = ""
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                )
            },
            bottomBar = {
                if (!isSearching) {
                    val unreadCount = articlesWithSource.count { !it.article.isRead }
                    val readCount = articlesWithSource.count { it.article.isRead }
                    SegmentedControl(
                        filterMode = filterMode,
                        unreadCount = unreadCount,
                        readCount = readCount,
                        onFilterChanged = { filterMode = it }
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                if (isSearching) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("搜索标题或正文关键字") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true
                    )
                }

                if (filteredArticles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有文章", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredArticles.keys.sortedDescending().forEach { timestamp ->
                            val groupArticles = filteredArticles[timestamp] ?: emptyList()
                            val isCollapsed = collapsedGroups.contains(timestamp)

                            // 日期标题和折叠按钮
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            collapsedGroups = if (isCollapsed) {
                                                collapsedGroups - timestamp
                                            } else {
                                                collapsedGroups + timestamp
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${formatTimestamp(timestamp)} (${groupArticles.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = if (isCollapsed) "展开" else "折叠",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            // 如果没有折叠，则显示该日期下的文章列表
                            if (!isCollapsed) {
                                items(groupArticles, key = { it.articleWithSource.article.id }) { searchResult ->
                                    val item = searchResult.articleWithSource
                                    ArticleRowCard(
                                        searchResult = searchResult,
                                        sourceName = if (isAllArticles) item.sourceName else null,
                                        isSearching = isSearching,
                                        onClick = {
                                            val fromContext = if (isAllArticles) "all" else "source"
                                            val navigateAction = {
                                                navController.navigate("article_container/${item.article.id}/${item.sourceName}/$fromContext")
                                            }

                                            // --- 新增：点击时检查并下载图片 ---
                                            if (item.article.images.isEmpty() || updateManager.checkIfImagesExistForArticle(item.article.timestamp, item.article.images)) {
                                                navigateAction()
                                            } else {
                                                coroutineScope.launch {
                                                    isDownloadingImages = true
                                                    downloadProgress = 0f
                                                    downloadProgressText = "准备中..."
                                                    try {
                                                        updateManager.downloadImagesForArticle(item.article.timestamp, item.article.images) { current, total ->
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

// --- 新增：生成带有高亮的节选段落 ---
private fun generateSnippet(content: String, query: String, highlightColor: Color): AnnotatedString {
    val cleanContent = content.replace("\n", " ").replace(Regex("\\s+"), " ")
    val index = cleanContent.indexOf(query, ignoreCase = true)
    if (index == -1) return AnnotatedString("")

    // 截取关键词前后各一部分字符
    val start = max(0, index - 20)
    val end = min(cleanContent.length, index + query.length + 40)
    
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < cleanContent.length) "..." else ""
    
    val snippetText = prefix + cleanContent.substring(start, end) + suffix
    val queryStartIndex = snippetText.indexOf(query, ignoreCase = true)

    return buildAnnotatedString {
        append(snippetText)
        if (queryStartIndex != -1) {
            addStyle(
                style = SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.Bold
                ),
                start = queryStartIndex,
                end = queryStartIndex + query.length
            )
        }
    }
}

@Composable
private fun SegmentedControl(
    filterMode: ArticleFilterMode,
    unreadCount: Int,
    readCount: Int,
    onFilterChanged: (ArticleFilterMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val selectedContainer = MaterialTheme.colorScheme.primary
        val selectedContent = MaterialTheme.colorScheme.onPrimary
        val unselectedContainer = MaterialTheme.colorScheme.surfaceVariant
        val unselectedContent = MaterialTheme.colorScheme.onSurfaceVariant

        val unreadSelected = filterMode == ArticleFilterMode.Unread
        val readSelected = filterMode == ArticleFilterMode.Read

        FilledTonalButton(
            onClick = { onFilterChanged(ArticleFilterMode.Unread) },
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (unreadSelected) selectedContainer else unselectedContainer,
                contentColor = if (unreadSelected) selectedContent else unselectedContent
            )
        ) {
            Text("Unread ($unreadCount)")
        }

        FilledTonalButton(
            onClick = { onFilterChanged(ArticleFilterMode.Read) },
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (readSelected) selectedContainer else unselectedContainer,
                contentColor = if (readSelected) selectedContent else unselectedContent
            )
        ) {
            Text("Read ($readCount)")
        }
    }
}

// --- 修改：接收 SearchResult 并展示正文标记和节选 ---
@Composable
private fun ArticleRowCard(
    searchResult: SearchResult,
    sourceName: String?,
    isSearching: Boolean,
    onClick: () -> Unit
) {
    val article = searchResult.articleWithSource.article

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sourceName != null) {
                    Text(
                        text = sourceName.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // 如果是搜索状态且命中了正文，显示“正文”标记
                if (isSearching && searchResult.isContentMatch) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "正文",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            if (sourceName != null) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = article.topic,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (article.isRead) Color.Gray else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // 显示正文高亮节选
            if (isSearching && searchResult.isContentMatch && searchResult.contentSnippet != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = searchResult.contentSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyMMdd", Locale.CHINA)
        val date = inputFormat.parse(timestamp)
        val outputFormat = SimpleDateFormat("yyyy年M月d日, EEEE", Locale.CHINA)
        date?.let { outputFormat.format(it) } ?: timestamp
    } catch (e: Exception) {
        timestamp
    }
}