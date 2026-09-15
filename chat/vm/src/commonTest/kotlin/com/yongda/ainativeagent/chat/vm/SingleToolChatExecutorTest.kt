package com.yongda.ainativeagent.chat.vm

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmChunk
import com.yongda.ainativeagent.llm.core.LlmProvider
import com.yongda.ainativeagent.llm.core.LlmProtocolException
import com.yongda.ainativeagent.llm.core.LlmRequest
import com.yongda.ainativeagent.llm.core.ToolCall
import com.yongda.ainativeagent.tool.battery.BatteryLevelTool
import com.yongda.ainativeagent.tool.battery.BatteryToolContract
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SingleToolChatExecutorTest {

    private fun user(text: String) = ChatMessage(ChatMessage.Role.USER, text)

    private fun success() = ToolExecutionResult(BatteryToolContract.successJson(73, true), ok = true)

    @Test
    fun toolCallExecutesBatteryOnceAndSecondRequestCarriesAssistantAndToolResult() = runTest {
        val battery = FakeBatteryTool { success() }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("call_1", BatteryToolContract.NAME, "{}"))) } },
            { flow { emit(LlmChunk.Content("你的电量是 73%")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        val chunks = executor.run(listOf(user("我手机还剩多少电？"))).toList()

        assertEquals(1, battery.calls)
        assertEquals(2, provider.requests.size)
        assertEquals(listOf(LlmChunk.Content("你的电量是 73%")), chunks)

        val first = provider.requests[0]
        assertEquals(listOf(BatteryToolContract.NAME), first.tools.map { it.name })
        assertTrue(first.allowToolCalls)

        val second = provider.requests[1]
        assertTrue(second.tools.isEmpty())
        assertFalse(second.allowToolCalls)
        val assistant = second.messages[second.messages.size - 2]
        assertEquals(ChatMessage.Role.ASSISTANT, assistant.role)
        assertEquals(listOf("call_1"), assistant.toolCalls.map { it.id })
        val toolResult = second.messages.last()
        assertEquals(ChatMessage.Role.TOOL, toolResult.role)
        assertEquals("call_1", toolResult.toolCallId)
        assertEquals(BatteryToolContract.successJson(73, true), toolResult.content)
    }

    @Test
    fun plainTextMakesOnlyOneRequest() = runTest {
        val battery = FakeBatteryTool { success() }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.Content("我是助手")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        val chunks = executor.run(listOf(user("介绍你自己"))).toList()

        assertEquals(0, battery.calls)
        assertEquals(1, provider.requests.size)
        assertEquals(listOf(LlmChunk.Content("我是助手")), chunks)
    }

    @Test
    fun unknownToolIsNotExecuted() = runTest {
        val battery = FakeBatteryTool { success() }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c", "delete_everything", "{}"))) } },
            { flow { emit(LlmChunk.Content("done")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        executor.run(listOf(user("x"))).toList()

        assertEquals(0, battery.calls)
        assertTrue(provider.requests[1].messages.last().content.contains("UNKNOWN_TOOL"))
    }

    @Test
    fun illegalArgumentsAreNotExecuted() = runTest {
        val battery = FakeBatteryTool { success() }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c", BatteryToolContract.NAME, "{\"extra\":1}"))) } },
            { flow { emit(LlmChunk.Content("done")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        executor.run(listOf(user("x"))).toList()

        assertEquals(0, battery.calls)
        assertTrue(provider.requests[1].messages.last().content.contains("INVALID_ARGUMENTS"))
    }

    @Test
    fun malformedJsonArgumentsAreNotExecuted() = runTest {
        val battery = FakeBatteryTool { success() }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c", BatteryToolContract.NAME, "{not json"))) } },
            { flow { emit(LlmChunk.Content("done")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        executor.run(listOf(user("x"))).toList()

        assertEquals(0, battery.calls)
        assertTrue(provider.requests[1].messages.last().content.contains("INVALID_ARGUMENTS"))
    }

    @Test
    fun emptyStringArgumentsAreAccepted() = runTest {
        val battery = FakeBatteryTool { success() }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c", BatteryToolContract.NAME, ""))) } },
            { flow { emit(LlmChunk.Content("done")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        executor.run(listOf(user("x"))).toList()

        assertEquals(1, battery.calls)
    }

    @Test
    fun multipleToolCallsAreNotExecuted() = runTest {
        val battery = FakeBatteryTool { success() }
        val provider = FakeProvider(
            {
                flow {
                    emit(LlmChunk.ToolCallReceived(ToolCall("a", BatteryToolContract.NAME, "{}")))
                    emit(LlmChunk.ToolCallReceived(ToolCall("b", BatteryToolContract.NAME, "{}")))
                }
            },
            { flow { emit(LlmChunk.Content("done")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        executor.run(listOf(user("x"))).toList()

        assertEquals(0, battery.calls)
        val toolResults = provider.requests[1].messages.filter { it.role == ChatMessage.Role.TOOL }
        assertEquals(2, toolResults.size)
        assertTrue(toolResults.all { it.content.contains("MULTIPLE_TOOL_CALLS_NOT_SUPPORTED") })
    }

    @Test
    fun toolFailureBecomesStructuredResult() = runTest {
        val battery = object : BatteryLevelTool {
            var calls = 0
            override suspend fun execute(argumentsJson: String): ToolExecutionResult {
                calls++
                throw IllegalStateException("boom")
            }
        }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c", BatteryToolContract.NAME, "{}"))) } },
            { flow { emit(LlmChunk.Content("解释失败")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        executor.run(listOf(user("x"))).toList()

        assertEquals(1, battery.calls)
        assertEquals(2, provider.requests.size)
        assertTrue(provider.requests[1].messages.last().content.contains("TOOL_EXECUTION_ERROR"))
    }

    @Test
    fun cancellationDuringToolExecutionPropagatesAndSkipsSecondRequest() = runTest {
        val started = CompletableDeferred<Unit>()
        val battery = object : BatteryLevelTool {
            override suspend fun execute(argumentsJson: String): ToolExecutionResult {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c", BatteryToolContract.NAME, "{}"))) } },
            { flow { emit(LlmChunk.Content("never")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        val job = launch { executor.run(listOf(user("x"))).toList() }
        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun secondRequestToolCallIsAControlledProtocolError() = runTest {
        val battery = FakeBatteryTool { success() }
        val provider = FakeProvider(
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("c", BatteryToolContract.NAME, "{}"))) } },
            { flow { emit(LlmChunk.ToolCallReceived(ToolCall("d", BatteryToolContract.NAME, "{}"))) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        assertFailsWith<LlmProtocolException> {
            executor.run(listOf(user("x"))).toList()
        }
        assertEquals(1, battery.calls)
    }

    @Test
    fun firstRequestReasoningIsCarriedOntoTheAssistantToolCallMessage() = runTest {
        val battery = FakeBatteryTool { success() }
        val provider = FakeProvider(
            {
                flow {
                    emit(LlmChunk.Reasoning("要查电量"))
                    emit(LlmChunk.ToolCallReceived(ToolCall("c", BatteryToolContract.NAME, "{}")))
                }
            },
            { flow { emit(LlmChunk.Content("73%")) } },
        )
        val executor = SingleToolChatExecutor(provider, battery)

        executor.run(listOf(user("x"))).toList()

        val assistant = provider.requests[1].messages.first { it.role == ChatMessage.Role.ASSISTANT }
        assertEquals("要查电量", assistant.reasoningContent)
    }

    private class FakeBatteryTool(private val result: () -> ToolExecutionResult) : BatteryLevelTool {
        var calls = 0
        override suspend fun execute(argumentsJson: String): ToolExecutionResult {
            calls++
            return result()
        }
    }

    private class FakeProvider(
        private vararg val responders: () -> Flow<LlmChunk>,
    ) : LlmProvider {
        override val name: String = "fake"
        val requests = mutableListOf<LlmRequest>()
        private var index = 0

        override fun streamChat(messages: List<ChatMessage>): Flow<String> =
            flow { }

        override fun stream(request: LlmRequest): Flow<LlmChunk> {
            requests += request
            return responders[index++]()
        }
    }
}
