package com.yongda.ainativeagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.yongda.ainativeagent.tool.battery.AndroidBatteryLevelTool
import com.yongda.ainativeagent.tool.battery.BatteryLevelTool
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolResults

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 开启 edge-to-edge：内容绘制到透明的状态栏/导航栏之下。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 电量工具只持有 applicationContext，避免泄漏 Activity。
        val batteryTool = AndroidBatteryLevelTool(applicationContext)
        setContent {
            // 系统栏图标明暗跟随主题：浅色主题用深色图标，深色主题用浅色图标。
            // 与 App() 内部 ChatTheme 使用同一个 isSystemInDarkTheme() 信号保持一致。
            val darkTheme = isSystemInDarkTheme()
            DisposableEffect(darkTheme) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
                onDispose { }
            }
            App(apiKey = BuildConfig.DEEPSEEK_API_KEY, batteryTool = batteryTool)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(apiKey = "", batteryTool = PreviewBatteryTool)
}

private object PreviewBatteryTool : BatteryLevelTool {
    override suspend fun execute(argumentsJson: String): ToolExecutionResult =
        ToolResults.failure("BATTERY_UNAVAILABLE", "Battery status is unavailable")
}
