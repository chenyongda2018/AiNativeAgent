package com.yongda.ainativeagent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yongda.ainativeagent.chat.ui.ChatModels
import com.yongda.ainativeagent.chat.ui.ChatScreen
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import com.yongda.ainativeagent.chat.vm.ChatViewModel
import com.yongda.ainativeagent.chat.vm.PrintlnToolTrace
import com.yongda.ainativeagent.chat.vm.SingleToolChatExecutor
import com.yongda.ainativeagent.llm.deepseek.DeepSeekProvider
import com.yongda.ainativeagent.tool.battery.BatteryLevelTool

/**
 * App 入口：装配 DeepSeek provider + 单工具执行器 + ChatViewModel + 纯 UI 的 ChatScreen。
 *
 * @param apiKey DeepSeek API key，由宿主（androidApp 的 BuildConfig）注入，不入库。
 * @param batteryTool 由平台组合根注入的电量工具抽象；commonMain 不直接访问 Android API。
 */
@Composable
fun App(apiKey: String, batteryTool: BatteryLevelTool) {
    ChatTheme(darkTheme = isSystemInDarkTheme()) {
        // 背景填充整个屏幕（含状态栏/导航栏区域），系统栏边距由内部 ChatTopBar / InputBar 各自处理。
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ChatTheme.colors.background,
        ) {
            val vm: ChatViewModel = viewModel {
                ChatViewModel(
                    engineFactory = { modelId ->
                        SingleToolChatExecutor(
                            provider = DeepSeekProvider.withApiKey(apiKey, model = modelId),
                            batteryTool = batteryTool,
                            trace = PrintlnToolTrace(),
                        )
                    },
                    initialModelId = ChatModels.default.id,
                )
            }
            val state by vm.state.collectAsStateWithLifecycle()
            ChatScreen(
                state = state,
                onSend = vm::send,
                onCancel = vm::cancel,
                onRetry = vm::retry,
                onSelectModel = vm::selectModel,
            )
        }
    }
}
