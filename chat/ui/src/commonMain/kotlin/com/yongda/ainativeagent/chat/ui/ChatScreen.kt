package com.yongda.ainativeagent.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.parseMarkdownFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        MessageList(state, Modifier.weight(1f).fillMaxWidth())

        state.error?.let { error ->
            ErrorBar(error = error, onRetry = onRetry)
        }

        InputBar(
            isStreaming = state.isStreaming,
            onSend = onSend,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun MessageList(state: ChatUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val markdownCache = remember { MarkdownRenderCache() }
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
                MessageBubble(msg, markdownCache)
            }
            state.streamingMessage?.let { streaming ->
                item(key = streaming.id, contentType = streaming.role) {
                    MessageBubble(streaming, markdownCache)
                }
            }
        }

        if (showScrollToLatest) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
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
                Box(contentAlignment = Alignment.Center) { Text("↓") }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessageUi, markdownCache: MarkdownRenderCache) {
    val isUser = msg.role == ChatRole.USER
    val bubbleColor =
        if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 320.dp)
                .then(if (isUser) Modifier else Modifier.fillMaxWidth()),
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                when {
                    isUser -> Text(msg.content, style = MaterialTheme.typography.bodyLarge)
                    msg.content.isEmpty() && msg.streaming ->
                        Text("▍", style = MaterialTheme.typography.bodyLarge)
                    msg.streaming -> StreamingText(msg.content)
                    else -> CachedMarkdown(msg, markdownCache)
                }
            }
        }
    }
}

@Composable
private fun StreamingText(content: String, modifier: Modifier = Modifier) {
    val paragraphs = remember { StreamingParagraphs() }
    val lines = remember(content) { paragraphs.update(content) }
    Column(modifier = modifier) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CachedMarkdown(msg: ChatMessageUi, cache: MarkdownRenderCache) {
    var parsed by remember(cache, msg.id, msg.content) {
        mutableStateOf<State?>(cache.get(msg.id, msg.content))
    }
    LaunchedEffect(cache, msg.id, msg.content) {
        if (parsed != null) return@LaunchedEffect
        val result = withContext(Dispatchers.Default) {
            parseMarkdownFlow(msg.content).first { it !is State.Loading }
        }
        if (result is State.Success) cache.put(msg.id, result)
        parsed = result
    }
    val result = parsed
    if (result is State.Success) {
        Markdown(
            state = result,
            modifier = Modifier.fillMaxWidth(),
            animations = markdownAnimations(animateTextSize = { this }),
        )
    } else {
        StreamingText(msg.content)
    }
}

@Composable
private fun ErrorBar(error: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun InputBar(
    isStreaming: Boolean,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…") },
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            if (isStreaming) {
                Button(onClick = onCancel) { Text("停止") }
            } else {
                Button(
                    onClick = {
                        val t = text.trim()
                        if (t.isNotEmpty()) {
                            onSend(t)
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank(),
                ) { Text("发送") }
            }
        }
    }
}
