package com.sskeysskey.onews

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {

    // 权限请求
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                println("通知权限已授予")
            } else {
                println("通知权限被拒绝")
            }
        }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationPermission()

        setContent {
            ONewsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val prefs =
                        remember { context.getSharedPreferences("ONewsPrefs", MODE_PRIVATE) }
                    
                    // 使用 0, 1, 2 分别代表：0-欢迎页，1-首次添加源页，2-主应用
                    var setupStage by remember {
                        mutableIntStateOf(
                            if (prefs.getBoolean("hasCompletedInitialSetup", false)) 2 else 0
                        )
                    }

                    when (setupStage) {
                        0 -> {
                            // 欢迎屏幕
                            WelcomeScreen(onComplete = {
                                // 点击加号后，不直接进主应用，而是进入阶段 1（添加新闻源界面）
                                setupStage = 1
                            })
                        }
                        1 -> {
                            // 拦截物理返回键：如果用户在添加源界面什么都不做强制返回
                            // 则标记设置完成并进入主页（此时主页会显示“您还没有订阅任何新闻源”）
                            BackHandler {
                                prefs.edit().putBoolean("hasCompletedInitialSetup", true).apply()
                                setupStage = 2
                            }
                            
                            // AddSourceView 需要一个 navController，这里提供一个临时的
                            val dummyNavController = rememberNavController()
                            
                            AddSourceView(
                                navController = dummyNavController,
                                isFirstTimeSetup = true,
                                onComplete = {
                                    // 用户选好新闻源并点击“确定”后，标记设置完成并进入主应用
                                    prefs.edit().putBoolean("hasCompletedInitialSetup", true).apply()
                                    setupStage = 2
                                }
                            )
                        }
                        2 -> {
                            // 已完成初始设置，显示主应用
                            AppNavigation()
                        }
                    }
                }
            }
        }
    }
}