package com.yongda.ainativeagent.chat.ui

import androidx.compose.runtime.Stable
import com.mikepenz.markdown.model.State
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

@Stable
internal class MarkdownRenderCache(
    val maxEntries: Int = 32,
    private val maxCharacters: Int = 262_144,
) {
    private val entries = LinkedHashMap<Long, State.Success>()
    private var characters = 0

    init {
        require(maxEntries > 0)
        require(maxCharacters > 0)
    }

    /** 只读查询缓存而不调整 LRU 顺序，适合在组合阶段安全调用。 */
    fun peek(id: Long, content: String): State.Success? {
        val entry = entries[id] ?: return null
        return entry.takeIf { it.content == content }
    }

    fun get(id: Long, content: String): State.Success? {
        val entry = entries[id] ?: return null
        if (entry.content != content) return null
        entries.remove(id)
        entries[id] = entry
        return entry
    }

    fun put(id: Long, state: State.Success) {
        entries.remove(id)?.let { characters -= it.content.length }
        if (state.content.length > maxCharacters) return
        entries[id] = state
        characters += state.content.length
        while (entries.size > maxEntries || characters > maxCharacters) {
            val oldest = entries.keys.first()
            characters -= entries.remove(oldest)!!.content.length
        }
    }
}

/**
 * 提取可独立放入 LazyColumn 的顶级 Markdown AST 块节点，过滤纯换行/空白节点，
 * 避免为不可见空行创建多余的 Lazy item 与额外的列表间距。
 */
internal fun extractRenderableMarkdownNodes(state: State.Success): List<ASTNode> =
    state.node.children.filter { node ->
        node.type != MarkdownTokenTypes.EOL && node.type != MarkdownTokenTypes.WHITE_SPACE
    }

/**
 * 根据单 item 内的相对滚动进度（0f..1f）与各顶级 AST 块的字符偏移区间，
 * 反推出拆分为块级 LazyColumn item 后最接近原阅读位置的块索引。
 */
internal fun findAnchorBlockIndex(
    blocks: List<ASTNode>,
    contentLength: Int,
    scrollFraction: Float,
): Int {
    if (blocks.isEmpty()) return 0
    val clamped = scrollFraction.coerceIn(0f, 1f)
    if (contentLength <= 0) {
        return (clamped * blocks.lastIndex).toInt().coerceIn(0, blocks.lastIndex)
    }
    val targetCharOffset = (clamped * contentLength).toInt()
    val matched = blocks.indexOfFirst { it.endOffset >= targetCharOffset }
    return if (matched >= 0) matched else blocks.lastIndex
}

internal class StreamingParagraphs {
    private var previous = ""
    private val completed = mutableListOf<String>()
    private var tailStart = 0

    fun update(content: String): List<String> {
        if (!content.startsWith(previous)) {
            completed.clear()
            tailStart = 0
            previous = ""
        }
        var newline = content.indexOf('\n', startIndex = previous.length)
        while (newline >= 0) {
            completed += content.substring(tailStart, newline)
            tailStart = newline + 1
            newline = content.indexOf('\n', startIndex = tailStart)
        }
        previous = content
        return completed + content.substring(tailStart)
    }
}
