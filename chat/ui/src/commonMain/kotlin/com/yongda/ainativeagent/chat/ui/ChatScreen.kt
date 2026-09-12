package com.yongda.ainativeagent.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yongda.ainativeagent.chat.ui.components.ChatEmptyState
import com.yongda.ainativeagent.chat.ui.components.ChatTopBar
import com.yongda.ainativeagent.chat.ui.components.ErrorBar
import com.yongda.ainativeagent.chat.ui.components.InputBar
import com.yongda.ainativeagent.chat.ui.components.MessageList
import com.yongda.ainativeagent.chat.ui.components.ModelSelectorSheet
import com.yongda.ainativeagent.chat.ui.components.SidebarDrawerContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectModel: (String) -> Unit = {},
    onNewChat: (() -> Unit)? = null,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showModelSheet by remember { mutableStateOf(false) }
    val selectedModelName = ChatModels.nameOf(state.modelId)
    val markdownCache = remember { MarkdownRenderCache() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarDrawerContent(
                onNewChat = { onNewChat?.invoke() },
                onCloseDrawer = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        // IME + 导航栏边距由底部 InputBar 内部处理（navigationBarsPadding().imePadding()），
        // 此处不再叠加 imePadding，避免键盘弹出时重复计入导航栏高度。
        Column(modifier = modifier.fillMaxSize()) {
            ChatTopBar(
                onOpenSidebar = { scope.launch { drawerState.open() } },
                onNewChat = { onNewChat?.invoke() },
            )

            val isEmpty = state.messages.isEmpty() && !state.isStreaming

            if (isEmpty) {
                ChatEmptyState(
                    modelName = selectedModelName,
                    onSelectPrompt = onSend,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                MessageList(
                    state = state,
                    markdownCache = markdownCache,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onRetry = onRetry,
                )
            }

            state.error?.let { error ->
                ErrorBar(error = error, onRetry = onRetry)
            }

            InputBar(
                isStreaming = state.isStreaming,
                modelName = selectedModelName,
                onOpenModelSelector = { showModelSheet = true },
                onSend = onSend,
                onCancel = onCancel,
            )
        }
    }

    if (showModelSheet) {
        ModelSelectorSheet(
            selectedModelId = state.modelId,
            onSelectModel = onSelectModel,
            onDismiss = { showModelSheet = false },
        )
    }
}
