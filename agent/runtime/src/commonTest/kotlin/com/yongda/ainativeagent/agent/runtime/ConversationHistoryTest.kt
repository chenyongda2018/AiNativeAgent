package com.yongda.ainativeagent.agent.runtime

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.ToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationHistoryTest {

    @Test
    fun preservesToolTrajectoryAcrossTurnsAndRollsBackOnlyCurrentTurnOnRetry() {
        val toolCall = ToolCall("call_1", "get_battery_level", "{}")
        val assistantWithCall = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            content = "",
            reasoningContent = "need battery",
            toolCalls = listOf(toolCall),
        )
        val toolResult = ChatMessage(
            role = ChatMessage.Role.TOOL,
            content = """{"ok":true,"level":82}""",
            toolCallId = "call_1",
        )
        val finalAnswer = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "当前电量 82%。")

        var history = ConversationHistory()
            .startTurn("还有多少电？")
            .appendToCurrentTurn(listOf(assistantWithCall, toolResult))
            .appendToCurrentTurn(listOf(finalAnswer))
            .startTurn("帮我查明天日程")
            .appendToCurrentTurn(
                listOf(
                    ChatMessage(
                        role = ChatMessage.Role.ASSISTANT,
                        content = "",
                        toolCalls = listOf(ToolCall("call_2", "query_calendar_events", "{}")),
                    ),
                    ChatMessage(role = ChatMessage.Role.TOOL, content = """{"ok":true}""", toolCallId = "call_2"),
                ),
            )

        // 重试第二轮：应只清空第二轮的工具消息与回答，保留第二轮的用户提问与第一轮完整工具轨迹。
        history = history.rollbackLastTurnResponse()

        val llmMessages = history.toLlmMessages("SYS")
        assertEquals(
            listOf(
                ChatMessage.Role.SYSTEM,
                ChatMessage.Role.USER,
                ChatMessage.Role.ASSISTANT,
                ChatMessage.Role.TOOL,
                ChatMessage.Role.ASSISTANT,
                ChatMessage.Role.USER,
            ),
            llmMessages.map { it.role },
        )
        assertEquals(listOf(toolCall), llmMessages[2].toolCalls)
        assertEquals("call_1", llmMessages[3].toolCallId)
        assertEquals("帮我查明天日程", llmMessages.last().content)
    }

    @Test
    fun clearDropsAllTurns() {
        val cleared = ConversationHistory()
            .startTurn("hi")
            .appendToCurrentTurn(listOf(ChatMessage(ChatMessage.Role.ASSISTANT, "hello")))
            .clear()

        assertTrue(cleared.turns.isEmpty())
        assertEquals(
            listOf(ChatMessage(ChatMessage.Role.SYSTEM, "SYS")),
            cleared.toLlmMessages("SYS"),
        )
    }
}
