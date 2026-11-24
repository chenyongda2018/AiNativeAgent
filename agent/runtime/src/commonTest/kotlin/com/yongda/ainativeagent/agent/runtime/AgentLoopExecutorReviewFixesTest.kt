package com.yongda.ainativeagent.agent.runtime

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmChunk
import com.yongda.ainativeagent.llm.core.LlmProvider
import com.yongda.ainativeagent.llm.core.LlmRequest
import com.yongda.ainativeagent.llm.core.ToolCall
import com.yongda.ainativeagent.llm.core.ToolDefinition
import com.yongda.ainativeagent.tool.core.AgentTool
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolRegistry
import com.yongda.ainativeagent.tool.core.ToolResults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLoopExecutorReviewFixesTest {

    private fun user(text: String) = ChatMessage(ChatMessage.Role.USER, text)

    // ---- F6：去重签名对 JSON 做规范化，键顺序不同视为同一调用 ----

    @Test
    fun duplicateDetectionIgnoresObjectKeyOrder() = runTest {
        val tool = RecordingTool("query_calendar_events", """{"ok":true}""")
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c1", "query_calendar_events", """{"a":1,"b":[1,2],"c":"x"}"""))) } },
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c2", "query_calendar_events", """{"c":"x","b":[1,2],"a":1}"""))) } },
            { flow { emit(LlmChunk.Content("答")) } },
        )
        val executor = AgentLoopExecutor(provider, ToolRegistry(listOf(tool)))

        executor.run(listOf(user("x"))).toList()

        assertEquals(1, tool.calls.size)
        assertTrue(provider.requests[2].messages.last().content.contains(AgentLoopExecutor.ERROR_DUPLICATE_TOOL_CALL))
    }

    @Test
    fun differentArrayOrderIsNotDuplicate() = runTest {
        val tool = RecordingTool("query_calendar_events", """{"ok":true}""")
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c1", "query_calendar_events", """{"b":[1,2]}"""))) } },
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c2", "query_calendar_events", """{"b":[2,1]}"""))) } },
            { flow { emit(LlmChunk.Content("答")) } },
        )
        val executor = AgentLoopExecutor(provider, ToolRegistry(listOf(tool)))

        executor.run(listOf(user("x"))).toList()

        assertEquals(2, tool.calls.size)
    }

    // ---- F6：终态结果强制无工具收尾，避免反复打扰 ----

    @Test
    fun terminalToolResultForcesFinalNoToolResponse() = runTest {
        val cancelTool = object : AgentTool {
            override val definition = ToolDefinition("create_calendar_event", "", "{}")
            var calls = 0
            override suspend fun execute(argumentsJson: String): ToolExecutionResult {
                calls++
                return ToolResults.terminalFailure("USER_CANCELLED", "用户取消")
            }
        }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c1", "create_calendar_event", """{"title":"x"}"""))) } },
            { flow { emit(LlmChunk.Content("已取消，未创建")) } },
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c2", "create_calendar_event", """{"title":"y"}"""))) } },
        )
        val executor = AgentLoopExecutor(provider, ToolRegistry(listOf(cancelTool)), maxToolSteps = 6)

        val events = executor.run(listOf(user("新建日程"))).toList()

        assertEquals(1, cancelTool.calls)
        assertEquals(2, provider.requests.size)
        assertTrue(provider.requests[1].tools.isEmpty())
        assertEquals(false, provider.requests[1].allowToolCalls)
        assertEquals(
            listOf(AgentTurnEvent.ContentDelta("已取消，未创建")),
            events.filterIsInstance<AgentTurnEvent.ContentDelta>(),
        )
    }

    private class RecordingTool(
        name: String,
        private val resultJson: String = """{"ok":true}""",
    ) : AgentTool {
        override val definition = ToolDefinition(name, "desc", """{"type":"object"}""")
        val calls = mutableListOf<String>()
        override suspend fun execute(argumentsJson: String): ToolExecutionResult {
            calls += argumentsJson
            return ToolExecutionResult(resultJson, ok = true)
        }
    }

    private class FakeProvider(
        private vararg val responders: () -> Flow<LlmChunk>,
    ) : LlmProvider {
        override val name: String = "fake"
        val requests = mutableListOf<LlmRequest>()
        private var index = 0
        override fun streamChat(messages: List<ChatMessage>): Flow<String> = flow { }
        override fun stream(request: LlmRequest): Flow<LlmChunk> {
            requests += request
            return responders[index++]()
        }
    }
}
