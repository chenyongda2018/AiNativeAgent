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
import com.yongda.ainativeagent.llm.deepseek.DeepSeekProvider

/**
 * App 入口：装配 DeepSeek provider + ChatViewModel + 纯 UI 的 ChatScreen。
 *
 * @param apiKey DeepSeek API key，由宿主（androidApp 的 BuildConfig）注入，不入库。
 */
@Composable
fun App(apiKey: String) {
    ChatTheme(darkTheme = isSystemInDarkTheme()) {
        // 背景填充整个屏幕（含状态栏/导航栏区域），系统栏边距由内部 ChatTopBar / InputBar 各自处理。
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ChatTheme.colors.background,
        ) {
            val vm: ChatViewModel = viewModel {
                ChatViewModel(
                    providerFactory = { modelId -> DeepSeekProvider.withApiKey(apiKey, model = modelId) },
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
