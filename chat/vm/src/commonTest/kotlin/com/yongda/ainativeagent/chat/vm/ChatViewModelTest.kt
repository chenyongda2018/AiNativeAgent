package com.yongda.ainativeagent.chat.vm

import androidx.lifecycle.viewModelScope
import com.yongda.ainativeagent.chat.ui.ChatRole
import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private lateinit var mainDispatcher: TestDispatcher
    private val viewModels = mutableListOf<ChatViewModel>()

    @BeforeTest
    fun setUp() {
        mainDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        mainDispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun collectsOnTheInjectedDispatcherAndCompletesWithFullText() = runTest {
        val worker = StandardTestDispatcher(testScheduler, "pipeline")
        val provider = FakeProvider {
            flow {
                assertSame(worker, currentCoroutineContext()[ContinuationInterceptor])
                emit("first")
                delay(1)
                emit(" second")
            }
        }
        val vm = viewModel(provider, worker)

        vm.send("  question  ")
        assertTrue(vm.state.value.isStreaming)
        assertTrue(provider.requests.isEmpty())
        advanceUntilIdle()

        assertFalse(vm.state.value.isStreaming)
        assertNull(vm.state.value.error)
        assertEquals(listOf("question", "first second"), vm.state.value.messages.map { it.content })
        assertTrue(vm.state.value.messages.none { it.streaming })
        assertEquals(
            listOf(
                ChatMessage(ChatMessage.Role.SYSTEM, ChatViewModel.DEFAULT_SYSTEM_PROMPT),
                ChatMessage(ChatMessage.Role.USER, "question"),
            ),
            provider.requests.single(),
        )
    }

    @Test
    fun cancellationBeforeDispatchClearsThePlaceholderAndAllowsAnotherSend() = runTest {
        val provider = FakeProvider { flowOf("answer") }
        val vm = viewModel(provider)

        vm.send("first")
        vm.cancel()
        vm.cancel()
        runCurrent()

        assertFalse(vm.state.value.isStreaming)
        assertEquals("", vm.state.value.messages.last().content)
        assertTrue(provider.requests.isEmpty())
        assertNull(vm.state.value.error)

        vm.send("second")
        advanceUntilIdle()
        assertEquals("answer", vm.state.value.messages.last().content)
        assertEquals(1, provider.requests.size)
        assertFalse(vm.state.value.isStreaming)
    }

    @Test
    fun cancellationKeepsReceivedTextThatHasNotBeenDisplayed() = runTest {
        val text = "a".repeat(84) + "\uD83D\uDE42"
        val provider = FakeProvider {
            flow {
                emit(text)
                awaitCancellation()
            }
        }
        val vm = viewModel(provider)
        vm.send("question")
        runCurrent()

        val draft = assertNotNull(vm.state.value.streamingMessage)
        assertTrue(draft.content.isNotEmpty())
        assertTrue(draft.content.length < text.length)
        vm.cancel()
        runCurrent()

        assertFalse(vm.state.value.isStreaming)
        assertNull(vm.state.value.error)
        assertEquals(text, vm.state.value.messages.last().content)
    }

    @Test
    fun cancellationWhileDrainingACompletedProviderKeepsTheEntireAnswer() = runTest {
        val text = "x".repeat(100_000)
        val vm = viewModel(FakeProvider { flowOf(text) })
        vm.send("question")
        runCurrent()

        assertTrue(assertNotNull(vm.state.value.streamingMessage).content.length < text.length)
        vm.cancel()
        runCurrent()

        assertEquals(0L, testScheduler.currentTime)
        assertFalse(vm.state.value.isStreaming)
        assertEquals(text, vm.state.value.messages.last().content)
        assertNull(vm.state.value.error)
    }

    @Test
    fun cancellationDuringChannelBackpressureKeepsEvenThePendingSend() = runTest {
        val attempted = StringBuilder()
        val provider = FakeProvider {
            flow {
                repeat(1_000) {
                    val delta = "$it|"
                    attempted.append(delta)
                    emit(delta)
                }
            }
        }
        val vm = viewModel(provider)
        vm.send("question")
        runCurrent()
        val receivedAtCancellation = attempted.toString()

        assertTrue(receivedAtCancellation.isNotEmpty())
        assertTrue(assertNotNull(vm.state.value.streamingMessage).content.length < receivedAtCancellation.length)
        vm.cancel()
        runCurrent()

        assertEquals(receivedAtCancellation, vm.state.value.messages.last().content)
        assertEquals(receivedAtCancellation, attempted.toString())
        assertFalse(vm.state.value.isStreaming)
    }

    @Test
    fun cancellationWaitsForTheCollectorToExitBeforeFinalizingOrAcceptingAnotherRequest() = runTest {
        val text = "x".repeat(84)
        var collectorExited = false
        val provider = FakeProvider {
            flow {
                try {
                    emit(text)
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        delay(50)
                        collectorExited = true
                    }
                }
            }
        }
        val vm = viewModel(provider)
        vm.send("question")
        runCurrent()
        vm.cancel()
        runCurrent()

        assertFalse(collectorExited)
        assertTrue(vm.state.value.isStreaming)
        vm.send("ignored")
        vm.retry()
        assertEquals(1, provider.requests.size)
        advanceTimeBy(50)
        runCurrent()

        assertTrue(collectorExited)
        assertFalse(vm.state.value.isStreaming)
        assertEquals(listOf("question", text), vm.state.value.messages.map { it.content })
    }

    @Test
    fun failureKeepsAllReceivedTextAndRetryReusesHistoryWithoutTheFailedAnswer() = runTest {
        val partial = "partial".repeat(50)
        var attempts = 0
        val provider = FakeProvider {
            flow {
                if (attempts++ == 0) {
                    emit(partial)
                    delay(1)
                    emit("tail")
                    throw IllegalStateException("offline")
                }
                emit("recovered")
            }
        }
        val vm = viewModel(provider)
        vm.send("question")
        advanceUntilIdle()

        assertEquals("offline", vm.state.value.error)
        assertEquals(partial + "tail", vm.state.value.messages.last().content)
        assertFalse(vm.state.value.isStreaming)

        vm.retry()
        assertNull(vm.state.value.error)
        assertEquals(listOf("question"), vm.state.value.messages.map { it.content })
        advanceUntilIdle()

        assertEquals(provider.requests[0], provider.requests[1])
        assertEquals(listOf("question", "recovered"), vm.state.value.messages.map { it.content })
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isStreaming)
    }

    @Test
    fun providerFactoryFailureAlsoFinalizesAndCanBeRetried() = runTest {
        var attempts = 0
        val provider = FakeProvider {
            if (attempts++ == 0) throw IllegalArgumentException()
            flowOf("ok")
        }
        val vm = viewModel(provider)
        vm.send("question")
        advanceUntilIdle()

        assertFalse(vm.state.value.isStreaming)
        assertEquals("请求失败，请重试", vm.state.value.error)
        assertEquals("", vm.state.value.messages.last().content)

        vm.retry()
        advanceUntilIdle()
        assertEquals("ok", vm.state.value.messages.last().content)
        assertNull(vm.state.value.error)
    }

    @Test
    fun cancelThenRetryDoesNotCancelTheNewJobAndPreservesEarlierTurns() = runTest {
        var attempts = 0
        val provider = FakeProvider {
            flow {
                when (attempts++) {
                    0 -> emit("first answer")
                    1 -> {
                        emit("partial".repeat(20))
                        awaitCancellation()
                    }
                    else -> emit("retry answer")
                }
            }
        }
        val vm = viewModel(provider)
        vm.send("first question")
        advanceUntilIdle()
        vm.send("second question")
        runCurrent()
        vm.cancel()
        runCurrent()
        vm.retry()
        advanceUntilIdle()

        assertEquals(provider.requests[1], provider.requests[2])
        assertEquals(
            listOf("first question", "first answer", "second question", "retry answer"),
            vm.state.value.messages.map { it.content },
        )
        assertEquals(
            listOf(ChatMessage.Role.SYSTEM, ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT, ChatMessage.Role.USER),
            provider.requests.last().map { it.role },
        )
        assertEquals(vm.state.value.messages.size, vm.state.value.messages.map { it.id }.distinct().size)
        assertFalse(vm.state.value.isStreaming)
    }

    @Test
    fun blankSendsAndConcurrentSendOrRetryAreIgnored() = runTest {
        val provider = FakeProvider {
            flow {
                emit("answer")
                awaitCancellation()
            }
        }
        val vm = viewModel(provider)
        vm.send(" \n ")
        assertTrue(vm.state.value.messages.isEmpty())
        assertFalse(vm.state.value.isStreaming)
        vm.send("question")
        vm.send("ignored")
        vm.retry()
        runCurrent()

        assertEquals(1, provider.requests.size)
        assertEquals(listOf("question"), vm.state.value.messages.map { it.content })
        vm.cancel()
        advanceUntilIdle()
    }

    @Test
    fun emptyProviderCompletionDoesNotLeaveStreamingStateOrAStaleCancellationTarget() = runTest {
        var attempts = 0
        val provider = FakeProvider {
            if (attempts++ == 0) emptyFlow() else flow {
                emit("next")
                awaitCancellation()
            }
        }
        val vm = viewModel(provider, UnconfinedTestDispatcher(testScheduler))
        vm.send("first")
        assertFalse(vm.state.value.isStreaming)
        vm.send("second")
        vm.cancel()
        advanceUntilIdle()

        assertFalse(vm.state.value.isStreaming)
        assertEquals("next", vm.state.value.messages.last().content)
        assertEquals(ChatRole.ASSISTANT, vm.state.value.messages.last().role)
    }

    @Test
    fun scopeCancellationBeforeDispatchStillFinalizesTheAssistant() = runTest {
        val provider = FakeProvider { flowOf("unused") }
        val vm = viewModel(provider)
        vm.send("question")
        vm.viewModelScope.cancel()
        runCurrent()

        assertFalse(vm.state.value.isStreaming)
        assertEquals("", vm.state.value.messages.last().content)
        assertTrue(provider.requests.isEmpty())
    }

    private fun TestScope.viewModel(
        provider: LlmProvider,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): ChatViewModel = ChatViewModel(provider, dispatcher = dispatcher).also(viewModels::add)

    private class FakeProvider(
        private val response: (List<ChatMessage>) -> Flow<String>,
    ) : LlmProvider {
        override val name: String = "fake"
        val requests = mutableListOf<List<ChatMessage>>()

        override fun streamChat(messages: List<ChatMessage>): Flow<String> {
            requests += messages.toList()
            return response(messages)
        }
    }
}
