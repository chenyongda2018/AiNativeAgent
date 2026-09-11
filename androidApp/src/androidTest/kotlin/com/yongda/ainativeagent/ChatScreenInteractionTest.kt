package com.yongda.ainativeagent

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yongda.ainativeagent.chat.ui.ChatMessageUi
import com.yongda.ainativeagent.chat.ui.ChatRole
import com.yongda.ainativeagent.chat.ui.ChatScreen
import com.yongda.ainativeagent.chat.ui.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatScreenInteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun focusedInputRemainsEditableDuringStreamingAndKeepsDraftAfterStop() {
        val history = history()
        val state = mutableStateOf(ChatUiState(messages = history))
        val sent = mutableListOf<String>()
        var cancellations = 0
        render(
            state = state,
            onSend = { sent += it },
            onCancel = {
                cancellations++
                val current = state.value
                state.value = current.copy(
                    messages = current.messages + requireNotNull(current.streamingMessage)
                        .copy(streaming = false),
                    streamingMessage = null,
                )
            },
        )

        val input = compose.onNode(hasSetTextAction())
        input.performClick().performTextInput("Offline draft")
        input.assertIsFocused()

        compose.runOnIdle {
            state.value = state.value.copy(streamingMessage = reply("Generating locally"))
        }
        compose.onNodeWithText("停止").assertIsDisplayed()
        input.assertIsEnabled().assertIsFocused().performTextInput(" while streaming")
        input.assertTextEquals("Offline draft while streaming")

        compose.runOnIdle {
            state.value = state.value.copy(
                streamingMessage = reply("Generating locally\nAnother synthetic chunk"),
            )
        }
        input.assertIsFocused().assertTextEquals("Offline draft while streaming")
        compose.onNodeWithText("停止").performClick()

        compose.runOnIdle {
            assertEquals(1, cancellations)
            assertTrue(sent.isEmpty())
            assertEquals(false, state.value.isStreaming)
        }
        compose.onNodeWithText("停止").assertDoesNotExist()
        input.assertIsEnabled().assertTextEquals("Offline draft while streaming")
        compose.onNodeWithText("发送").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("Offline draft while streaming"), sent)
        }
    }

    @Test
    fun successiveStreamChunksKeepAllTextAndCompletionRendersMarkdown() {
        val history = history()
        val state = mutableStateOf(
            ChatUiState(messages = history, streamingMessage = reply("")),
        )
        render(state)
        val chunks = listOf(
            "# Offline",
            " stream\n\nFirst paragraph keeps **every",
            " token**.\n",
            "\nSecond paragraph keeps `split",
            " chunks` intact.\n\n",
            (1..24).joinToString("\n\n", postfix = "\n\n") {
                "Synthetic paragraph $it retains its complete offline payload."
            },
            "**Final",
            " marker.**",
        )
        var accumulated = ""

        chunks.forEach { chunk ->
            accumulated += chunk
            val expected = accumulated
            compose.runOnIdle {
                state.value = state.value.copy(streamingMessage = reply(expected))
            }
            expected.split('\n').filter { it.isNotEmpty() }.forEach { line ->
                compose.onNodeWithText(line, useUnmergedTree = true).assertExists()
            }
        }

        compose.runOnIdle {
            state.value = ChatUiState(
                messages = history + reply(accumulated).copy(streaming = false),
            )
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(
                hasText("Offline stream"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("# Offline stream", useUnmergedTree = true)
            .assertDoesNotExist()
        compose.onNodeWithText("Offline stream", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "First paragraph keeps every token.",
            useUnmergedTree = true,
        ).assertExists()
        compose.onNode(
            SemanticsMatcher("Second paragraph retains all text after normalizing inline-code spacing") { node ->
                node.config.contains(SemanticsProperties.Text) &&
                    node.config[SemanticsProperties.Text].any { text ->
                        text.text.replace(Regex("[ \u00A0]+"), " ") ==
                            "Second paragraph keeps split chunks intact."
                    }
            },
            useUnmergedTree = true,
        ).assertExists()
        (1..24).forEach {
            compose.onNodeWithText(
                "Synthetic paragraph $it retains its complete offline payload.",
                useUnmergedTree = true,
            ).assertExists()
        }
        compose.onNodeWithText("Final marker.", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("停止").assertDoesNotExist()
    }

    @Test
    fun scrollToLatestFromMiddleOfLongReplyMovesForwardAndShowsEnd() {
        val history = history()
        val content = (0 until 100).joinToString("\n", postfix = "\nLatest reply end") {
            "Offline reply line $it"
        }
        render(mutableStateOf(ChatUiState(history, streamingMessage = reply(content))))
        val list = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex),
        )
        list.performScrollToIndex(history.size)
        val viewportHeight = list.fetchSemanticsNode().boundsInRoot.height
        assertTrue(viewportHeight > 0f)
        list.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            assertTrue(scrollBy(0f, viewportHeight * 2f))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Offline reply line 0", useUnmergedTree = true)
            .assertIsNotDisplayed()
        compose.onNodeWithText("Latest reply end", useUnmergedTree = true)
            .assertIsNotDisplayed()

        fun scrollPosition(): Float = list.fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()

        val start = scrollPosition()
        val range = list.fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(start < range.maxValue())
        val latest = compose.onNodeWithContentDescription("滚动到最新消息")
        latest.assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            latest.performClick()
            var previous = start
            repeat(180) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val current = scrollPosition()
                assertTrue(
                    "Scroll moved backwards at frame $it: $previous -> $current",
                    current + 0.0001f >= previous,
                )
                previous = current
            }
            assertTrue(previous > start)
            compose.onNodeWithText("Latest reply end", useUnmergedTree = true)
                .assertIsDisplayed()
            latest.assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun render(
        state: MutableState<ChatUiState>,
        onSend: (String) -> Unit = { error("Unexpected send: $it") },
        onCancel: () -> Unit = { error("Unexpected cancellation") },
    ) {
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    state = state.value,
                    onSend = onSend,
                    onCancel = onCancel,
                    onRetry = { error("Unexpected retry") },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        }
    }

    private fun history(): List<ChatMessageUi> = List(24) { index ->
        ChatMessageUi(
            id = index.toLong(),
            role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
            content = "Synthetic history $index: offline interaction fixture.",
        )
    }

    private fun reply(content: String) = ChatMessageUi(
        id = 1000L,
        role = ChatRole.ASSISTANT,
        content = content,
        streaming = true,
    )
}
