package com.sskeysskey.onews

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

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
                    var hasCompletedInitialSetup by remember {
                        mutableStateOf(prefs.getBoolean("hasCompletedInitialSetup", false))
                    }

                    if (hasCompletedInitialSetup) {
                        // 如果已完成初始设置，显示主应用
                        AppNavigation()
                    } else {
                        // 否则，显示欢迎屏幕
                        WelcomeScreen(onComplete = {
                            prefs.edit().putBoolean("hasCompletedInitialSetup", true).apply()
                            hasCompletedInitialSetup = true
                        })
                    }
                }
            }
        }
    }
}