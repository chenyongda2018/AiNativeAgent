package com.yongda.ainativeagent.chat.vm

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLoopExecutorTest {

    private fun user(text: String) = ChatMessage(ChatMessage.Role.USER, text)

    @Test
    fun plainTextMakesOnlyOneRequest() = runTest {
        val provider = FakeProvider({ flow { emit(LlmChunk.Content("你好")) } })
        val executor = AgentLoopExecutor(provider, registryOf(RecordingTool("noop")))

        val chunks = executor.run(listOf(user("hi"))).toList()

        assertEquals(1, provider.requests.size)
        assertEquals(listOf(LlmChunk.Content("你好")), chunks)
    }

    @Test
    fun singleToolThenFinalAnswerRoundTripsAssistantAndToolResult() = runTest {
        val tool = RecordingTool("query_calendar_events", """{"ok":true,"count":0}""")
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c1", "query_calendar_events", "{}"))) } },
            { flow { emit(LlmChunk.Content("没有日程")) } },
        )
        val executor = AgentLoopExecutor(provider, registryOf(tool))

        val chunks = executor.run(listOf(user("今天有啥安排"))).toList()

        assertEquals(1, tool.calls.size)
        assertEquals(2, provider.requests.size)
        assertEquals(listOf(LlmChunk.Content("没有日程")), chunks)
        val second = provider.requests[1]
        val assistant = second.messages[second.messages.size - 2]
        assertEquals(ChatMessage.Role.ASSISTANT, assistant.role)
        assertEquals(listOf("c1"), assistant.toolCalls.map { it.id })
        val toolResult = second.messages.last()
        assertEquals(ChatMessage.Role.TOOL, toolResult.role)
        assertEquals("c1", toolResult.toolCallId)
    }

    @Test
    fun supportsQueryThenUpdateAcrossSteps() = runTest {
        val query = RecordingTool("query_calendar_events", """{"ok":true,"events":[{"eventId":42}]}""")
        val update = RecordingTool("update_calendar_event", """{"ok":true,"action":"updated"}""")
        val registry = ToolRegistry(listOf(query, update))
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c1", "query_calendar_events", "{}"))) } },
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c2", "update_calendar_event", """{"eventId":42,"title":"改"}"""))) } },
            { flow { emit(LlmChunk.Content("已更新")) } },
        )
        val executor = AgentLoopExecutor(provider, registry)

        val chunks = executor.run(listOf(user("把评审会改名"))).toList()

        assertEquals(1, query.calls.size)
        assertEquals(1, update.calls.size)
        assertEquals(3, provider.requests.size)
        assertEquals(listOf(LlmChunk.Content("已更新")), chunks)
    }

    @Test
    fun unknownToolIsBackfilledAndLoopContinues() = runTest {
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c", "delete_everything", "{}"))) } },
            { flow { emit(LlmChunk.Content("抱歉")) } },
        )
        val executor = AgentLoopExecutor(provider, registryOf(RecordingTool("noop")))

        executor.run(listOf(user("x"))).toList()

        assertTrue(provider.requests[1].messages.last().content.contains(ToolRegistry.ERROR_UNKNOWN_TOOL))
    }

    @Test
    fun parallelToolCallsInOneStepAreRejected() = runTest {
        val tool = RecordingTool("query_calendar_events")
        val provider = FakeProvider(
            {
                flow {
                    emit(LlmChunk.ToolCallReceived(ToolCall("a", "query_calendar_events", "{}")))
                    emit(LlmChunk.ToolCallReceived(ToolCall("b", "query_calendar_events", "{}")))
                }
            },
            { flow { emit(LlmChunk.Content("好的")) } },
        )
        val executor = AgentLoopExecutor(provider, registryOf(tool))

        executor.run(listOf(user("x"))).toList()

        assertEquals(0, tool.calls.size)
        val toolResults = provider.requests[1].messages.filter { it.role == ChatMessage.Role.TOOL }
        assertEquals(2, toolResults.size)
        assertTrue(toolResults.all { it.content.contains(AgentLoopExecutor.ERROR_PARALLEL_TOOL_CALLS) })
    }

    @Test
    fun duplicateIdenticalCallIsRejectedButExecutesOnce() = runTest {
        val tool = RecordingTool("query_calendar_events", """{"ok":true}""")
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c1", "query_calendar_events", """{"limit": 5}"""))) } },
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c2", "query_calendar_events", """{"limit":5}"""))) } },
            { flow { emit(LlmChunk.Content("答")) } },
        )
        val executor = AgentLoopExecutor(provider, registryOf(tool))

        executor.run(listOf(user("x"))).toList()

        assertEquals(1, tool.calls.size) // 第二次同名同参被拦截
        assertTrue(provider.requests[2].messages.last().content.contains(AgentLoopExecutor.ERROR_DUPLICATE_TOOL_CALL))
    }

    @Test
    fun boundedStepsForceFinalNoToolRequest() = runTest {
        val tool = RecordingTool("query_calendar_events", """{"ok":true}""")
        // 模型每一步都尝试调用工具，参数各不相同以绕过去重。
        val responders = buildList<() -> Flow<LlmChunk>> {
            repeat(3) { i ->
                add { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c$i", "query_calendar_events", """{"limit":$i}"""))) } }
            }
            add { flow { emit(LlmChunk.Content("到此为止")) } }
        }
        val provider = FakeProvider(*responders.toTypedArray())
        val executor = AgentLoopExecutor(provider, registryOf(tool), maxToolSteps = 3)

        val chunks = executor.run(listOf(user("x"))).toList()

        // 3 步都带工具，第 4 次为强制的无工具收尾请求。
        assertEquals(4, provider.requests.size)
        assertTrue(provider.requests[3].tools.isEmpty())
        assertEquals(false, provider.requests[3].allowToolCalls)
        assertEquals(listOf(LlmChunk.Content("到此为止")), chunks)
    }

    @Test
    fun cancellationDuringToolExecutionPropagates() = runTest {
        val started = CompletableDeferred<Unit>()
        val tool = object : AgentTool {
            override val definition = ToolDefinition("slow", "", "{}")
            override suspend fun execute(argumentsJson: String): ToolExecutionResult {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c", "slow", "{}"))) } },
            { flow { emit(LlmChunk.Content("never")) } },
        )
        val executor = AgentLoopExecutor(provider, ToolRegistry(listOf(tool)))

        val job = launch { executor.run(listOf(user("x"))).toList() }
        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, provider.requests.size)
    }

    // ---- fakes ----

    private fun registryOf(vararg tools: AgentTool) = ToolRegistry(tools.toList())

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
