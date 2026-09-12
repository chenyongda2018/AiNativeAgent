package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.clickable
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
    val showScrollToLatest by remember {
        derivedStateOf { listState.canScrollForward }
    }

    LaunchedEffect(lastMessageId) {
        if (totalMessageCount > 0) {
            listState.animateScrollToItem(totalMessageCount - 1)
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = state.messages,
                key = { it.id },
                contentType = { it.role },
            ) { msg ->
                when (msg.role) {
                    ChatRole.USER -> UserMessageItem(msg)
                    ChatRole.ASSISTANT -> AssistantMessageItem(msg, markdownCache, onRetry)
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
                shape = CircleShape,
                color = ChatTheme.colors.surface,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .size(36.dp)
                    .semantics { contentDescription = "滚动到最新消息" }
                    .clickable {
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
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = ChatTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}
