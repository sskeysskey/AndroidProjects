package com.sskeysskey.onews

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID

// 文章模型：增加 source_id 等新字段
data class Article(
    @SerializedName("topic") val topic: String,
    @SerializedName("article") val articleContent: String,
    @SerializedName("topic_eng") val topicEng: String? = null,
    @SerializedName("article_eng") val articleEng: String? = null,
    @SerializedName("images") val images: List<String> = emptyList(),
    @SerializedName("url") val url: String? = null,
    @SerializedName("source_id") val sourceId: String? = null,
    val id: UUID = UUID.randomUUID(),
    val isRead: Boolean = false,
    val timestamp: String = ""
)

data class ArticleWithSource(
    val article: Article,
    val sourceName: String
)

class NewsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NewsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NewsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class NewsViewModel(application: Application) : AndroidViewModel(application) {
    private val gson = Gson()
    private val documentsDirectory: File = application.filesDir
    private val badgeManager = AppBadgeManager(application)
    private val prefs = application.getSharedPreferences("ONewsPrefs", Context.MODE_PRIVATE)
    private val readPrefs = application.getSharedPreferences("ReadRecords", Context.MODE_PRIVATE)

    // 官方推荐的排序顺序
    private val preferredOrder = listOf(
        "ft", "wsjcn", "nytimes", "bloomberg", "rfi", "nikkei", "dw",
        "wsj", "economist", "reuters", "washpost", "mittr", "bbc"
    )

    private val _sources = MutableStateFlow<List<NewsSource>>(emptyList())
    val sources = _sources.asStateFlow()

    private val _readRecords = MutableStateFlow<Map<String, Long>>(emptyMap())

    val allArticlesSortedForDisplay = combine(_sources, _readRecords) { sources, _ ->
        sources.flatMap { source ->
            source.articles.map { article ->
                ArticleWithSource(article = article, sourceName = source.name)
            }
        }.sortedWith(compareByDescending<ArticleWithSource> { it.article.timestamp }.thenBy { it.article.topic })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalUnreadCount = combine(_sources, _readRecords) { sources, _ ->
        sources.sumOf { source -> source.articles.count { !it.isRead } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadReadRecords()
        viewModelScope.launch {
            totalUnreadCount.collect { count ->
                badgeManager.updateBadge(count)
            }
        }
    }

    private fun loadReadRecords() {
        val map = mutableMapOf<String, Long>()
        readPrefs.all.forEach { (key, value) ->
            if (value is Long) map[key] = value
        }
        _readRecords.value = map
    }

    private fun saveReadRecords() {
        val editor = readPrefs.edit()
        editor.clear()
        _readRecords.value.forEach { (key, value) -> editor.putLong(key, value) }
        editor.apply()
    }

    fun loadNews() {
        viewModelScope.launch(Dispatchers.IO) {
            val subscribed = SubscriptionManager.subscribedSources.value
            if (subscribed.isEmpty()) {
                withContext(Dispatchers.Main) { _sources.value = emptyList() }
                return@launch
            }

            val allFiles = documentsDirectory.listFiles { _, name ->
                name.startsWith("onews_") && name.endsWith(".json")
            } ?: run {
                withContext(Dispatchers.Main) { _sources.value = emptyList() }
                return@launch
            }

            // 获取映射表
            val mappingsJson = prefs.getString("source_mappings", "{}")
            val typeMap = object : TypeToken<Map<String, String>>() {}.type
            val sourceMappings: Map<String, String> = gson.fromJson(mappingsJson, typeMap) ?: emptyMap()

            val allArticlesBySourceId = mutableMapOf<String, MutableList<Article>>()
            val articleType = object : TypeToken<Map<String, List<Article>>>() {}.type

            for (file in allFiles) {
                val timestamp = file.name.substringAfter("onews_").substringBefore(".json")
                try {
                    val jsonString = file.readText()
                    val decoded: Map<String, List<Article>> = gson.fromJson(jsonString, articleType)

                    decoded.forEach { (_, articles) ->
                        // 核心修改：使用 source_id 进行判断
                        val sourceId = articles.firstOrNull()?.sourceId
                        if (sourceId != null && subscribed.contains(sourceId)) {
                            val normalized = articles.map { a ->
                                val safeId = a.id ?: UUID.randomUUID()
                                val safeImages = a.images ?: emptyList()
                                a.copy(id = safeId, images = safeImages, timestamp = timestamp)
                            }
                            allArticlesBySourceId.getOrPut(sourceId) { mutableListOf() }.addAll(normalized)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val tempSources = allArticlesBySourceId.map { (sourceId, articles) ->
                val rawMappingName = sourceMappings[sourceId] ?: sourceId
                val nameParts = rawMappingName.split("|")
                val cnName = nameParts.firstOrNull() ?: rawMappingName
                val enName = if (nameParts.size > 1) nameParts[1] else cnName

                val articlesWithReadStatus = articles.map { article ->
                    if (_readRecords.value.containsKey(article.topic)) article.copy(isRead = true) else article
                }

                val sortedArticles = articlesWithReadStatus.sortedWith(compareByDescending<Article> { it.timestamp }.thenBy { it.topic })
                NewsSource(sourceId = sourceId, name = cnName, nameEn = enName, articles = sortedArticles)
            }.sortedWith(Comparator { s1, s2 ->
                val idx1 = preferredOrder.indexOf(s1.sourceId).let { if (it == -1) Int.MAX_VALUE else it }
                val idx2 = preferredOrder.indexOf(s2.sourceId).let { if (it == -1) Int.MAX_VALUE else it }
                if (idx1 != idx2) idx1.compareTo(idx2) else s1.name.compareTo(s2.name)
            })

            withContext(Dispatchers.Main) {
                _sources.value = tempSources
            }
        }
    }

    fun markAsRead(articleID: UUID) {
        viewModelScope.launch(Dispatchers.Main) {
            val newSources = _sources.value.map { source ->
                val newArticles = source.articles.map { article ->
                    if (article.id == articleID && !article.isRead) {
                        _readRecords.value = _readRecords.value + (article.topic to Date().time)
                        saveReadRecords()
                        article.copy(isRead = true)
                    } else article
                }
                source.copy(articles = newArticles)
            }
            _sources.value = newSources
        }
    }

    fun markAsUnread(articleID: UUID) {
        viewModelScope.launch(Dispatchers.Main) {
            val newSources = _sources.value.map { source ->
                val newArticles = source.articles.map { article ->
                    if (article.id == articleID && article.isRead) {
                        _readRecords.value = _readRecords.value - article.topic
                        saveReadRecords()
                        article.copy(isRead = false)
                    } else article
                }
                source.copy(articles = newArticles)
            }
            _sources.value = newSources
        }
    }

    fun markAllAboveAsRead(articleID: UUID, inVisibleList: List<Article>) {
        viewModelScope.launch {
            val pivotIndex = inVisibleList.indexOfFirst { it.id == articleID }
            if (pivotIndex > 0) {
                inVisibleList.subList(0, pivotIndex).forEach { article ->
                    if (!article.isRead) markAsRead(article.id)
                }
            }
        }
    }

    fun markAllBelowAsRead(articleID: UUID, inVisibleList: List<Article>) {
        viewModelScope.launch {
            val pivotIndex = inVisibleList.indexOfFirst { it.id == articleID }
            if (pivotIndex != -1 && pivotIndex < inVisibleList.size - 1) {
                inVisibleList.subList(pivotIndex + 1, inVisibleList.size).forEach { article ->
                    if (!article.isRead) markAsRead(article.id)
                }
            }
        }
    }

    fun findNextUnread(afterId: UUID, inSource: String?): ArticleWithSource? {
        val list: List<ArticleWithSource> = if (inSource != null) {
            _sources.value.find { it.name == inSource }
                ?.articles?.filter { !it.isRead }?.map { ArticleWithSource(it, inSource) } ?: emptyList()
        } else {
            allArticlesSortedForDisplay.value.filter { !it.article.isRead }
        }

        if (list.isEmpty()) return null
        val currentIndex = list.indexOfFirst { it.article.id == afterId }
        return if (currentIndex == -1) list.first() else list[(currentIndex + 1) % list.size]
    }
}