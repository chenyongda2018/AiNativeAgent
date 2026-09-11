package com.yongda.ainativeagent.chat.ui

import com.mikepenz.markdown.model.State

internal class MarkdownRenderCache(
    private val maxEntries: Int = 32,
    private val maxCharacters: Int = 262_144,
) {
    private val entries = LinkedHashMap<Long, State.Success>()
    private var characters = 0

    init {
        require(maxEntries > 0)
        require(maxCharacters > 0)
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
