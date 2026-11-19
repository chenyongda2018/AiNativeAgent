package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.LocalMarkdownAnimations
import com.mikepenz.markdown.compose.LocalMarkdownAnnotator
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownExtendedSpans
import com.mikepenz.markdown.compose.LocalMarkdownInlineContent
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalReferenceLinkHandler
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.markdownInlineContent
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.parseMarkdownFlow
import com.yongda.ainativeagent.chat.ui.ChatMessageUi
import com.yongda.ainativeagent.chat.ui.MarkdownRenderCache
import com.yongda.ainativeagent.chat.ui.StreamingParagraphs
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.intellij.markdown.ast.ASTNode

/**
 * 在列表外层一次性提供所有 Markdown 渲染所需的 CompositionLocal，
 * 避免每个块级 Lazy item 重复构建样式对象与 Provider。
 */
@Composable
internal fun ProvideChatMarkdownLocals(content: @Composable () -> Unit) {
    val colors = markdownColor()
    val typography = markdownTypography()
    val padding = markdownPadding()
    val dimens = markdownDimens()
    val imageTransformer = remember { NoOpImageTransformerImpl() }
    val annotator = markdownAnnotator()
    val extendedSpans = markdownExtendedSpans()
    val inlineContent = markdownInlineContent()
    val components = remember {
        markdownComponents(
            checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
        )
    }
    val animations = markdownAnimations(animateTextSize = { this })

    CompositionLocalProvider(
        LocalMarkdownColors provides colors,
        LocalMarkdownTypography provides typography,
        LocalMarkdownPadding provides padding,
        LocalMarkdownDimens provides dimens,
        LocalImageTransformer provides imageTransformer,
        LocalMarkdownAnnotator provides annotator,
        LocalMarkdownExtendedSpans provides extendedSpans,
        LocalMarkdownInlineContent provides inlineContent,
        LocalMarkdownComponents provides components,
        LocalMarkdownAnimations provides animations,
        content = content,
    )
}

/** 渲染单个已解析的顶级 Markdown AST 块节点，直接作为 LazyColumn 的独立 item 使用。 */
@Composable
internal fun MarkdownBlockItem(
    state: State.Success,
    node: ASTNode,
    includeSpacer: Boolean,
    modifier: Modifier = Modifier,
) {
    val components = LocalMarkdownComponents.current
    CompositionLocalProvider(LocalReferenceLinkHandler provides state.referenceLinkHandler) {
        Box(modifier = modifier.fillMaxWidth().testTag("assistant_message_content")) {
            MarkdownElement(
                node = node,
                components = components,
                content = state.content,
                includeSpacer = includeSpacer,
            )
        }
    }
}

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
                modifier = Modifier.fillMaxWidth().testTag("assistant_message_content"),
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
            modifier = modifier.fillMaxWidth().testTag("assistant_message_content"),
            animations = markdownAnimations(animateTextSize = { this }),
        )
    } else {
        StreamingText(msg.content, modifier.testTag("assistant_message_content"))
    }
}
