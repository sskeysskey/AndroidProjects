package com.sskeysskey.onews

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import androidx.core.content.edit

data class ServerVersion(
    @SerializedName("version") val version: String,
    @SerializedName("files") val files: List<FileInfo>,
    @SerializedName("source_mappings") val sourceMappings: Map<String, String>? = null,
)

data class FileInfo(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("md5") val md5: String?,
)

interface ApiService {
    @GET("check_version")
    suspend fun getServerVersion(): ServerVersion

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>
}

object ApiClient {
    private const val BASE_URL = "http://106.15.183.158:5001/api/ONews/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}

class UpdateManager(
    private val context: Context,
    private val externalScope: CoroutineScope,
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow("启动中...")
    val syncMessage = _syncMessage.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0.0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _progressText = MutableStateFlow("")
    val progressText = _progressText.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage = _lastErrorMessage.asStateFlow()

    private val documentsDirectory: File = context.filesDir
    private val apiService = ApiClient.apiService

    // 修改点：添加 suspend 关键字，并移除 externalScope.launch
    suspend fun checkAndDownloadUpdates() {
        safelyRunSync(
            startMessage = "正在检查更新...",
            onErrorMessage = "无法连接服务器，请检查网络或点击刷新按钮。"
        ) {
            val serverVersion = apiService.getServerVersion()

            serverVersion.sourceMappings?.let { mappings ->
                val prefs = context.getSharedPreferences("ONewsPrefs", Context.MODE_PRIVATE)
                val json = Gson().toJson(mappings)
                prefs.edit { putString("source_mappings", json) }
            }

            val localFiles = getLocalFiles()

            _syncMessage.value = "正在清理旧资源..."
            val validServerFiles = serverVersion.files.map { it.name }.toSet()
            val filesToDelete = localFiles.filterNot { validServerFiles.contains(it) }
            val oldNewsItemsToDelete = filesToDelete.filter {
                it.startsWith("onews_") || it.startsWith("news_images_")
            }

            if (oldNewsItemsToDelete.isNotEmpty()) {
                oldNewsItemsToDelete.forEach { itemName ->
                    File(documentsDirectory, itemName).deleteRecursively()
                }
            }

            val downloadTasks = mutableListOf<Pair<FileInfo, Boolean>>()
            val jsonFilesFromServer = serverVersion.files.filter { it.type == "json" }
            // 移除全量下载 images 的逻辑，仅处理 json 文件

            jsonFilesFromServer.forEach { jsonInfo ->
                val localFile = File(documentsDirectory, jsonInfo.name)
                if (localFile.exists()) {
                    val serverMD5 = jsonInfo.md5
                    val localMD5 = calculateMD5(localFile)
                    if (serverMD5 != null && serverMD5 != localMD5) {
                        downloadTasks.add(jsonInfo to false)
                    }
                } else {
                    downloadTasks.add(jsonInfo to false)
                }
            }

            if (downloadTasks.isEmpty()) {
                _syncMessage.value = "已是最新"
                delay(500)
                return@safelyRunSync
            }

            _isDownloading.value = true
            val totalTasks = downloadTasks.count()

            downloadTasks.forEachIndexed { index, task ->
                _progressText.value = "${index + 1}/$totalTasks"
                _downloadProgress.value = (index + 1).toFloat() / totalTasks.toFloat()

                val (fileInfo, _) = task
                if (fileInfo.type == "json") {
                    _syncMessage.value = "正在下载文件: ${fileInfo.name}..."
                    downloadSingleFile(fileInfo.name)
                }
            }

            _isDownloading.value = false
            _syncMessage.value = "更新完成！"
            _progressText.value = ""
            delay(1000)
        }
    }

    // --- 新增：按需下载图片相关方法 ---

    fun checkIfImagesExistForArticle(timestamp: String, imageNames: List<String>): Boolean {
        val sanitizedNames = imageNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (sanitizedNames.isEmpty()) return true

        val directoryName = "news_images_$timestamp"
        val localDir = File(documentsDirectory, directoryName)

        for (imageName in sanitizedNames) {
            val localFile = File(localDir, imageName)
            if (!localFile.exists()) return false
        }
        return true
    }

    suspend fun downloadImagesForArticle(
        timestamp: String,
        imageNames: List<String>,
        onProgress: (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val sanitizedNames = imageNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (sanitizedNames.isEmpty()) return@withContext

        val directoryName = "news_images_$timestamp"
        val localDir = File(documentsDirectory, directoryName)
        localDir.mkdirs()

        val imagesToDownload = sanitizedNames.filter { !File(localDir, it).exists() }
        if (imagesToDownload.isEmpty()) return@withContext

        val total = imagesToDownload.size
        withContext(Dispatchers.Main) { onProgress(0, total) }

        imagesToDownload.forEachIndexed { index, imageName ->
            val downloadPath = "$directoryName/$imageName"
            try {
                val url = buildDownloadUrlForFilename(downloadPath)
                val response = apiService.downloadFile(url)
                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        val destinationFile = File(localDir, imageName)
                        body.byteStream().use { input ->
                            FileOutputStream(destinationFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) { onProgress(index + 1, total) }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun preDownloadImagesForArticleSilently(timestamp: String, imageNames: List<String>) = withContext(Dispatchers.IO) {
        try {
            downloadImagesForArticle(timestamp, imageNames) { _, _ -> }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- 保留原有方法 ---

    // 修改点：添加 suspend 关键字，并移除 externalScope.launch
    suspend fun checkAndDownloadAllNewsManifests() {
        safelyRunSync(
            startMessage = "正在获取新闻清单列表...",
            onErrorMessage = "无法连接服务器，请检查网络或点击刷新按钮。"
        ) {
            val serverVersion = apiService.getServerVersion()

            serverVersion.sourceMappings?.let { mappings ->
                val prefs = context.getSharedPreferences("ONewsPrefs", Context.MODE_PRIVATE)
                val json = Gson().toJson(mappings)
                prefs.edit { putString("source_mappings", json) }
            }

            val allJsonInfos = serverVersion.files
                .filter { it.type == "json" && it.name.startsWith("onews_") }
                .sortedBy { it.name }

            if (allJsonInfos.isEmpty()) {
                _syncMessage.value = "服务器上未找到清单。"
                delay(500)
                return@safelyRunSync
            }

            val tasksToDownload = allJsonInfos.filter { jsonInfo ->
                val localFile = File(documentsDirectory, jsonInfo.name)
                if (localFile.exists()) {
                    val serverMD5 = jsonInfo.md5
                    val localMD5 = calculateMD5(localFile)
                    serverMD5 != null && serverMD5 != localMD5
                } else {
                    true
                }
            }

            if (tasksToDownload.isEmpty()) {
                _syncMessage.value = "新闻清单已是最新。"
                delay(500)
                return@safelyRunSync
            }

            _isDownloading.value = true
            val total = tasksToDownload.count()
            tasksToDownload.forEachIndexed { index, info ->
                _progressText.value = "${index + 1}/$total"
                _downloadProgress.value = (index + 1).toFloat() / total.toFloat()
                _syncMessage.value = "正在下载: ${info.name}..."
                downloadSingleFile(info.name)
            }

            _isDownloading.value = false
            _syncMessage.value = "清单更新完成！"
            _progressText.value = ""
            delay(500)
        }
    }

    private fun buildDownloadUrlForFilename(rawFilename: String): String {
        val encoded = java.net.URLEncoder.encode(rawFilename, Charsets.UTF_8.name())
        return "download?filename=$encoded"
    }

    private suspend fun downloadSingleFile(filename: String) = withContext(Dispatchers.IO) {
        try {
            val url = buildDownloadUrlForFilename(filename)
            val response = apiService.downloadFile(url)
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    val destinationFile = File(documentsDirectory, filename)
                    try {
                        destinationFile.parentFile?.mkdirs()
                        body.byteStream().use { input ->
                            FileOutputStream(destinationFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        _lastErrorMessage.value = "写入文件失败: $filename"
                    }
                } ?: run {
                    _lastErrorMessage.value = "下载失败（空响应体）: $filename"
                }
            } else {
                _lastErrorMessage.value = "下载失败(${response.code()}): $filename"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _lastErrorMessage.value = "无法连接服务器，下载失败: $filename"
        }
    }

    private fun getLocalFiles(): List<String> {
        return documentsDirectory.listFiles()?.map { it.name } ?: emptyList()
    }

    private fun calculateMD5(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var numRead: Int
                while (fis.read(buffer).also { numRead = it } != -1) {
                    md.update(buffer, 0, numRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun safelyRunSync(
        startMessage: String,
        onErrorMessage: String,
        block: suspend () -> Unit,
    ) {
        try {
            _lastErrorMessage.value = null
            _isSyncing.value = true
            _isDownloading.value = false
            _syncMessage.value = startMessage
            _progressText.value = ""
            _downloadProgress.value = 0.0f

            block()

        } catch (e: Exception) {
            e.printStackTrace()
            _lastErrorMessage.value = onErrorMessage
            _syncMessage.value = onErrorMessage
        } finally {
            _isDownloading.value = false
            delay(300)
            _isSyncing.value = false
        }
    }
}