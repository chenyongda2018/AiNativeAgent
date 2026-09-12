package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.parseMarkdownFlow
import com.yongda.ainativeagent.chat.ui.ChatMessageUi
import com.yongda.ainativeagent.chat.ui.MarkdownRenderCache
import com.yongda.ainativeagent.chat.ui.StreamingParagraphs
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Composable
internal fun StreamingText(content: String, modifier: Modifier = Modifier) {
    val paragraphs = remember { StreamingParagraphs() }
    val lines = remember(content) { paragraphs.update(content) }
    Column(modifier = modifier) {
        lines.forEach { line ->
            Text(
                text = line,
                fontSize = 14.5.sp,
                lineHeight = 23.sp,
                color = ChatTheme.colors.textPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun CachedMarkdown(msg: ChatMessageUi, cache: MarkdownRenderCache, modifier: Modifier = Modifier) {
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
            modifier = modifier.fillMaxWidth(),
            animations = markdownAnimations(animateTextSize = { this }),
        )
    } else {
        StreamingText(msg.content, modifier)
    }
}
