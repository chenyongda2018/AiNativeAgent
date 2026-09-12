package com.yongda.ainativeagent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
        Surface(
            modifier = Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding(),
            color = ChatTheme.colors.background,
        ) {
            val vm: ChatViewModel = viewModel { ChatViewModel(DeepSeekProvider.withApiKey(apiKey)) }
            val state by vm.state.collectAsStateWithLifecycle()
            ChatScreen(
                state = state,
                onSend = vm::send,
                onCancel = vm::cancel,
                onRetry = vm::retry,
            )
        }
    }
}
