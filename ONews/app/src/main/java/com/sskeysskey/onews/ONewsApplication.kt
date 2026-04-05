package com.sskeysskey.onews

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.util.UUID
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun AppBackgroundImage() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.welcome_background), // 你需要添加这张背景图
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun AppNavigation() {
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current

    // 创建一个可以在所有屏幕间共享的 ViewModel 实例
    val newsViewModel: NewsViewModel = viewModel(
        factory = NewsViewModelFactory(context.applicationContext as Application)
    )

    NavHost(navController = navController, startDestination = "source_list") {

        composable("source_list") {
            SourceList(
                navController = navController,
                viewModel = newsViewModel
            )
        }

        composable("add_source/{isFirstTimeSetup}",
            arguments = listOf(navArgument("isFirstTimeSetup") { type = NavType.BoolType })
        ) { backStackEntry ->
            val isFirstTimeSetup = backStackEntry.arguments?.getBoolean("isFirstTimeSetup") ?: false
            AddSourceView(
                navController = navController,
                isFirstTimeSetup = isFirstTimeSetup,
                onComplete = {
                    // 如果是首次设置，完成时直接跳转到主列表
                    navController.navigate("source_list") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }

        composable("all_articles") {
            AllArticlesListView(
                navController = navController,
                viewModel = newsViewModel
            )
        }

        composable("article_list/{sourceName}",
            arguments = listOf(navArgument("sourceName") { type = NavType.StringType })
        ) { backStackEntry ->
            val sourceName = backStackEntry.arguments?.getString("sourceName") ?: ""
            ArticleListView(
                navController = navController,
                sourceName = sourceName,
                viewModel = newsViewModel
            )
        }

        composable("article_container/{articleId}/{sourceName}/{from}",
            arguments = listOf(
                navArgument("articleId") { type = NavType.StringType },
                navArgument("sourceName") { type = NavType.StringType },
                navArgument("from") { type = NavType.StringType } // "source" or "all"
            )
        ) { backStackEntry ->
            val articleId = UUID.fromString(backStackEntry.arguments?.getString("articleId"))
            val sourceName = backStackEntry.arguments?.getString("sourceName") ?: ""
            val fromContext = backStackEntry.arguments?.getString("from") ?: "all"

            ArticleContainer(
                initialArticleId = articleId,
                navigationContext = if (fromContext == "source") fromContext else "all",
                viewModel = newsViewModel,
                navController = navController
            )
        }
    }
}

class AppBadgeManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId = 1 // 固定 ID，用于更新同一个通知

    @SuppressLint("MissingPermission") // <-- 已添加
    fun updateBadge(count: Int) {
        // 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 在实际应用中，这里应该触发一个权限请求流程
                println("没有发送通知的权限，无法更新角标。")
                return
            }
        }

        if (count > 0) {
            val notification = NotificationCompat.Builder(context, ONewsApplication.BADGE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("ONews")
                .setContentText("您有 $count 篇未读文章")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 替换为你的通知图标
                .setNumber(count) // 这是在启动器图标上显示数字的关键
                .setOngoing(true) // 使通知不可清除
                .setShowWhen(false)
                .build()
            notificationManager.notify(notificationId, notification)
        } else {
            // 如果数量为0，则取消通知
            notificationManager.cancel(notificationId)
        }
        println("应用角标已更新为: $count")
    }
}

class ONewsApplication : Application() {

    companion object {
        const val BADGE_NOTIFICATION_CHANNEL_ID = "onews_badge_channel"
    }

    override fun onCreate() {
        super.onCreate()
        // 初始化订阅管理（关键修复）
        SubscriptionManager.init(applicationContext)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val badgeChannel = NotificationChannel(
                BADGE_NOTIFICATION_CHANNEL_ID,
                "App Badge Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel used to show the unread count on the app icon."
                setShowBadge(true) // 允许此渠道的通知显示角标
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(badgeChannel)
        }
    }
}