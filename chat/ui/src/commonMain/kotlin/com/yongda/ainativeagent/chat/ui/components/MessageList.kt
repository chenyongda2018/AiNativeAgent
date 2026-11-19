package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdownFlow
import com.yongda.ainativeagent.chat.ui.ChatRole
import com.yongda.ainativeagent.chat.ui.ChatUiState
import com.yongda.ainativeagent.chat.ui.MarkdownRenderCache
import com.yongda.ainativeagent.chat.ui.extractRenderableMarkdownNodes
import com.yongda.ainativeagent.chat.ui.findAnchorBlockIndex
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MessageList(
    state: ChatUiState,
    markdownCache: MarkdownRenderCache,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var cacheVersion by remember(markdownCache) { mutableIntStateOf(0) }
    val preparsedMessageIds = remember(markdownCache) { HashSet<Long>() }
    val lastMessageId = state.streamingMessage?.id ?: state.messages.lastOrNull()?.id
    val hasStreamingMessage = state.isStreaming
    val showScrollToLatest by remember { derivedStateOf { listState.canScrollForward } }

    // 仅对最新且尚未预解析过的定稿助手消息做后台预解析（上限不超过缓存容量），
    // 避免长会话超过 LRU 上限时反复淘汰并重解析旧历史；更老的消息进入视口时按需解析。
    LaunchedEffect(state.messages, markdownCache) {
        val newlyParsed = mutableListOf<Pair<Long, State.Success>>()
        var remainingBudget = markdownCache.maxEntries
        for (i in state.messages.lastIndex downTo 0) {
            if (remainingBudget <= 0) break
            val msg = state.messages[i]
            if (msg.role != ChatRole.ASSISTANT || msg.content.isEmpty()) continue
            remainingBudget--
            if (!preparsedMessageIds.add(msg.id)) continue
            if (markdownCache.peek(msg.id, msg.content) != null) continue
            val parsed = withContext(Dispatchers.Default) {
                parseMarkdownFlow(msg.content).first { it !is State.Loading }
            }
            if (parsed is State.Success) {
                markdownCache.put(msg.id, parsed)
                newlyParsed += msg.id to parsed
            }
        }
        if (newlyParsed.isEmpty()) return@LaunchedEffect

        // 若用户正停留在即将拆分为多块的某条长消息内部（scrollOffset > 0），
        // 在触发拆分前按原单 item 的阅读进度定位到对应的 Markdown 块，避免旧像素偏移被后续块吃掉导致跳位。
        val topItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        val topScrollOffset = listState.firstVisibleItemScrollOffset
        val anchorPlan = if (topItem != null && topItem.size > 0 && topScrollOffset > 0) {
            val matched = newlyParsed.firstOrNull { (id, _) -> id == topItem.key }
            if (matched != null) {
                val (anchorMsgId, parsedState) = matched
                val blocks = extractRenderableMarkdownNodes(parsedState)
                if (blocks.size > 1) {
                    val fraction = topScrollOffset.toFloat() / topItem.size.toFloat()
                    val blockIndex = findAnchorBlockIndex(blocks, parsedState.content.length, fraction)
                    var headerIndex = 0
                    for (msg in state.messages) {
                        if (msg.id == anchorMsgId) break
                        val cached = if (msg.role == ChatRole.ASSISTANT) {
                            markdownCache.peek(msg.id, msg.content)
                        } else {
                            null
                        }
                        val msgBlocks = cached?.let(::extractRenderableMarkdownNodes)
                        headerIndex += if (msgBlocks != null && msgBlocks.size > 1) {
                            2 + msgBlocks.size
                        } else {
                            1
                        }
                    }
                    // Header 占 headerIndex，对应正文块位于 headerIndex + 1 + blockIndex
                    headerIndex + 1 + blockIndex
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            null
        }

        cacheVersion++

        if (anchorPlan != null) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > anchorPlan }
            listState.scrollToItem(index = anchorPlan, scrollOffset = 0)
        }
    }

    // 仅对包含多个顶级块的长助手消息进行块级扁平化；单块短消息保持单个 item，
    // 既避免无谓的 slot 拆分开销，也保持短消息 1:1 列表索引。
    val splitBlocksByMessageId = remember(state.messages, cacheVersion) {
        buildMap {
            for (msg in state.messages) {
                if (msg.role != ChatRole.ASSISTANT) continue
                val parsed = markdownCache.peek(msg.id, msg.content) ?: continue
                val blocks = extractRenderableMarkdownNodes(parsed)
                if (blocks.size > 1) {
                    put(msg.id, parsed to blocks)
                }
            }
        }
    }

    val totalLazyItemCount = remember(state.messages, hasStreamingMessage, splitBlocksByMessageId) {
        var count = 0
        for (msg in state.messages) {
            val splitEntry = splitBlocksByMessageId[msg.id]
            count += if (msg.role == ChatRole.ASSISTANT && splitEntry != null) {
                // Header + 各 Markdown 顶级块 + Footer
                2 + splitEntry.second.size
            } else {
                1
            }
        }
        if (hasStreamingMessage) count += 1
        count
    }

    LaunchedEffect(lastMessageId) {
        if (totalLazyItemCount > 0) listState.animateScrollToItem(totalLazyItemCount - 1)
    }

    Box(modifier = modifier) {
        ProvideChatMarkdownLocals {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 18.dp),
            ) {
                state.messages.forEachIndexed { messageIndex, message ->
                    val topSpacing = if (messageIndex == 0) 0.dp else 16.dp
                    when (message.role) {
                        ChatRole.USER -> {
                            item(key = message.id, contentType = ChatRole.USER) {
                                UserMessageItem(
                                    msg = message,
                                    modifier = Modifier.padding(top = topSpacing),
                                )
                            }
                        }

                        ChatRole.ASSISTANT -> {
                            val splitEntry = splitBlocksByMessageId[message.id]
                            if (splitEntry != null) {
                                val (parsedState, blocks) = splitEntry
                                // Header 沿用 message.id 作为稳定锚点 key，避免拆分瞬间丢失滚动位置
                                item(
                                    key = message.id,
                                    contentType = "assistant_header",
                                ) {
                                    AssistantMessageHeader(
                                        msg = message,
                                        modifier = Modifier.padding(top = topSpacing, bottom = 4.dp),
                                    )
                                }
                                itemsIndexed(
                                    items = blocks,
                                    key = { blockIndex, node ->
                                        "md_${message.id}_${blockIndex}_${node.startOffset}_${node.endOffset}"
                                    },
                                    contentType = { _, node -> node.type },
                                ) { blockIndex, node ->
                                    MarkdownBlockItem(
                                        state = parsedState,
                                        node = node,
                                        includeSpacer = blockIndex > 0,
                                        modifier = Modifier.padding(start = ASSISTANT_CONTENT_START_PADDING),
                                    )
                                }
                                item(
                                    key = "assistant_footer_${message.id}",
                                    contentType = "assistant_footer",
                                ) {
                                    AssistantMessageFooter(
                                        content = message.content,
                                        onRetry = onRetry,
                                        modifier = Modifier.padding(
                                            start = ASSISTANT_CONTENT_START_PADDING,
                                            top = 9.dp,
                                        ),
                                    )
                                }
                            } else {
                                item(key = message.id, contentType = ChatRole.ASSISTANT) {
                                    AssistantMessageItem(
                                        msg = message,
                                        markdownCache = markdownCache,
                                        onRetry = onRetry,
                                        modifier = Modifier.padding(top = topSpacing),
                                    )
                                }
                            }
                        }
                    }
                }

                state.streamingMessage?.let { streaming ->
                    val topSpacing = if (state.messages.isEmpty()) 0.dp else 16.dp
                    item(key = streaming.id, contentType = streaming.role) {
                        when (streaming.role) {
                            ChatRole.USER -> UserMessageItem(
                                msg = streaming,
                                modifier = Modifier.padding(top = topSpacing),
                            )

                            ChatRole.ASSISTANT -> AssistantMessageItem(
                                msg = streaming,
                                markdownCache = markdownCache,
                                onRetry = onRetry,
                                modifier = Modifier.padding(top = topSpacing),
                            )
                        }
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
