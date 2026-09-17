package com.yongda.ainativeagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {

    // Activity 作用域的权限宿主：launcher 必须在 STARTED 之前注册，故在 onCreate 构造。
    private lateinit var permissionHost: AndroidPermissionHost

    override fun onCreate(savedInstanceState: Bundle?) {
        // 开启 edge-to-edge：内容绘制到透明的状态栏/导航栏之下。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 进程级 Agent 依赖图在 Application 作用域长期存活；本 Activity 只把权限宿主 attach 到稳定网关，
        // 不再自行创建工具/控制器，避免 retained ViewModel 捕获随重建失效的 Activity 作用域对象。
        val graph = application as AiNativeAgentApp
        permissionHost = AndroidPermissionHost(this, graph.rationaleController)
        graph.permissionGateway.attach(permissionHost)

        setContent {
            // 系统栏图标明暗跟随主题；与 App() 内部 ChatTheme 使用同一个 isSystemInDarkTheme() 信号保持一致。
            val darkTheme = isSystemInDarkTheme()
            DisposableEffect(darkTheme) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
                onDispose { }
            }
            App(
                apiKey = BuildConfig.DEEPSEEK_API_KEY,
                registry = graph.toolRegistry,
                confirmationController = graph.confirmationController,
                rationaleController = graph.rationaleController,
                timeContextProvider = ::currentTimeContext,
            )
        }
    }

    override fun onDestroy() {
        // 撤下附着：唤醒可能挂起的权限申请并清空网关引用，避免泄漏与卡死。
        permissionHost.cancelPending()
        (application as AiNativeAgentApp).permissionGateway.detach(permissionHost)
        super.onDestroy()
    }
}

/**
 * 每轮生成前提供设备当前时间 / 时区上下文，供模型可靠换算「今天 / 明天」等相对时间。
 *
 * 顶层函数（非 Activity 成员）：`::currentTimeContext` 是无绑定引用，不会捕获 [MainActivity]，
 * 从而不会被 retained [com.yongda.ainativeagent.chat.vm.ChatViewModel] 间接长期持有导致 Activity 泄漏。
 */
private fun currentTimeContext(): String {
    val now = ZonedDateTime.now()
    val iso = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
    val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.SIMPLIFIED_CHINESE)
    return "【设备当前时间】$iso（时区 ${now.zone.id}，$weekday）。" +
        "处理相对时间以此为基准，调用日历工具时须传带时区偏移的具体时间。"
}
