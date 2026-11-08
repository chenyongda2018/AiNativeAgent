package com.yongda.ainativeagent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import com.yongda.ainativeagent.tool.calendar.CalendarConfirmationController
import com.yongda.ainativeagent.tool.calendar.PermissionRationaleController
import com.yongda.ainativeagent.tool.core.ToolRegistry
import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.UIKit.UIViewController

/**
 * iOS 应用入口控制器，由 Swift 宿主通过 UIViewControllerRepresentable 调用。
 *
 * @param apiKey 本地注入的 DeepSeek API Key。若为空则展示配置指引，不启动正常聊天流程。
 */
fun MainViewController(apiKey: String): UIViewController = ComposeUIViewController {
    if (apiKey.isBlank()) {
        MissingApiKeyView()
    } else {
        val registry = remember { ToolRegistry(emptyList()) }
        val confirmationController = remember { CalendarConfirmationController() }
        val rationaleController = remember { PermissionRationaleController() }

        App(
            apiKey = apiKey,
            registry = registry,
            confirmationController = confirmationController,
            rationaleController = rationaleController,
            timeContextProvider = { getIosTimeContext() },
            systemPrompt = IOS_CHAT_SYSTEM_PROMPT,
        )
    }
}

/**
 * 获取当前 iOS 设备的时间和时区说明，供模型处理相对时间。
 */
private fun getIosTimeContext(): String {
    val now = NSDate()
    val formatter = NSISO8601DateFormatter()
    val isoTime = formatter.stringFromDate(now)
    val tzName = NSTimeZone.localTimeZone.name
    return "当前设备时间: $isoTime，时区: $tzName"
}

/**
 * 当未配置 API Key 时显示的提示界面，符合项目暖石与 Terracotta 设计规范。
 */
@Composable
private fun MissingApiKeyView() {
    ChatTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ChatTheme.colors.background,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ChatTheme.colors.surfaceRaised,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "未配置 API Key",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChatTheme.colors.brand,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "当前应用未检测到有效 DEEPSEEK_API_KEY。\n\n" +
                                "请在环境变量中设置 DEEPSEEK_API_KEY，或者在项目根目录 local.properties 中配置：\n" +
                                "DEEPSEEK_API_KEY=sk-xxxx\n\n" +
                                "配置后重新运行 prepare-local-config.sh 并构建即可开始对话。",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = ChatTheme.colors.textPrimary,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}
