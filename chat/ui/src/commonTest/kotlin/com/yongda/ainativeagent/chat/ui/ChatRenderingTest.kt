package com.yongda.ainativeagent.chat.ui

import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdownFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ChatRenderingTest {
    @Test
    fun streamingParagraphsPreserveAllTextAndBlankLines() {
        val paragraphs = StreamingParagraphs()
        val snapshots = listOf("", "hello", "hello\n", "hello\n\n中", "hello\n\n中文\nend")

        snapshots.forEach { content ->
            assertEquals(content, paragraphs.update(content).joinToString("\n"))
        }
    }

    @Test
    fun streamingParagraphsReuseCompletedLines() {
        val paragraphs = StreamingParagraphs()
        val first = paragraphs.update("first line\nsecond")
        val second = paragraphs.update("first line\nsecond line\nthird")

        assertSame(first.first(), second.first())
        assertEquals(listOf("first line", "second line", "third"), second)
    }

    @Test
    fun streamingParagraphsResetWhenContentIsReplaced() {
        val paragraphs = StreamingParagraphs()
        paragraphs.update("first\nsecond")

        assertEquals(listOf("replacement", ""), paragraphs.update("replacement\n"))
        assertEquals(listOf(""), paragraphs.update(""))
    }

    @Test
    fun cacheRequiresMatchingContentAndReusesParsedState() = runBlocking {
        val cache = MarkdownRenderCache()
        val state = parse("**bold**")
        cache.put(1, state)

        assertSame(state, cache.get(1, "**bold**"))
        assertNull(cache.get(1, "changed"))
        assertNull(cache.get(2, "**bold**"))
    }

    @Test
    fun cacheEvictsLeastRecentlyUsedMessage() = runBlocking {
        val cache = MarkdownRenderCache(maxEntries = 2)
        cache.put(1, parse("one"))
        cache.put(2, parse("two"))
        cache.get(1, "one")
        cache.put(3, parse("three"))

        assertNull(cache.get(2, "two"))
        assertEquals("one", cache.get(1, "one")?.content)
        assertEquals("three", cache.get(3, "three")?.content)
    }

    @Test
    fun cacheBoundsTotalCharactersAndAccountsForReplacement() = runBlocking {
        val cache = MarkdownRenderCache(maxCharacters = 6)
        cache.put(1, parse("one"))
        cache.put(1, parse("four"))
        cache.put(2, parse("two"))

        assertNull(cache.get(1, "four"))
        assertEquals("two", cache.get(2, "two")?.content)
        cache.put(2, parse("oversized"))
        assertNull(cache.get(2, "oversized"))
        cache.put(3, parse("sixsix"))
        assertEquals("sixsix", cache.get(3, "sixsix")?.content)
    }

    private suspend fun parse(content: String): State.Success =
        parseMarkdownFlow(content).first { it !is State.Loading } as State.Success
}
