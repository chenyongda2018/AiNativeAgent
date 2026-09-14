package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yongda.ainativeagent.chat.ui.ChatRole
import com.yongda.ainativeagent.chat.ui.ChatUiState
import com.yongda.ainativeagent.chat.ui.MarkdownRenderCache
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import kotlinx.coroutines.launch

@Composable
internal fun MessageList(
    state: ChatUiState,
    markdownCache: MarkdownRenderCache,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val totalMessageCount = state.messages.size + if (state.streamingMessage == null) 0 else 1
    val lastMessageId = state.streamingMessage?.id ?: state.messages.lastOrNull()?.id
    val showScrollToLatest by remember { derivedStateOf { listState.canScrollForward } }

    LaunchedEffect(lastMessageId) {
        if (totalMessageCount > 0) listState.animateScrollToItem(totalMessageCount - 1)
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = state.messages,
                key = { it.id },
                contentType = { it.role },
            ) { message ->
                when (message.role) {
                    ChatRole.USER -> UserMessageItem(message)
                    ChatRole.ASSISTANT -> AssistantMessageItem(message, markdownCache, onRetry)
                }
            }
            state.streamingMessage?.let { streaming ->
                item(key = streaming.id, contentType = streaming.role) {
                    when (streaming.role) {
                        ChatRole.USER -> UserMessageItem(streaming)
                        ChatRole.ASSISTANT -> AssistantMessageItem(streaming, markdownCache, onRetry)
                    }
                }
            }
        }

        if (showScrollToLatest) {
            Surface(
                onClick = {
                    scope.launch {
                        val lastIndex = listState.layoutInfo.totalItemsCount - 1
                        if (lastIndex < 0) return@launch
                        if (listState.layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) {
                            listState.animateScrollToItem(lastIndex)
                        }
                        val layout = listState.layoutInfo
                        val lastItem = layout.visibleItemsInfo.lastOrNull()
                            ?.takeIf { it.index == lastIndex } ?: return@launch
                        val remaining = lastItem.offset + lastItem.size +
                            layout.afterContentPadding - layout.viewportEndOffset
                        if (remaining > 0) listState.animateScrollBy(remaining.toFloat())
                    }
                },
                shape = CircleShape,
                color = ChatTheme.colors.surfaceRaised,
                border = BorderStroke(1.dp, ChatTheme.colors.borderInteractive),
                shadowElevation = ChatTactileTokens.elevationRaised,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .size(ChatTactileTokens.minimumTouchTarget)
                    .semantics { contentDescription = "滚动到最新消息" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = ChatTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
