package com.yongda.ainativeagent.chat.vm

import androidx.lifecycle.viewModelScope
import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelThreadingTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultDispatcherRunsProviderCreationAndCollectionAwayFromTheCaller() = runBlocking {
        val caller = Thread.currentThread()
        val factoryThread = CompletableDeferred<Thread>()
        val collectorThread = CompletableDeferred<Thread>()
        val collectorContext = CompletableDeferred<ContinuationInterceptor?>()
        val provider = object : LlmProvider {
            override val name: String = "fake"

            override fun streamChat(messages: List<ChatMessage>): Flow<String> {
                factoryThread.complete(Thread.currentThread())
                return flow {
                    collectorThread.complete(Thread.currentThread())
                    collectorContext.complete(currentCoroutineContext()[ContinuationInterceptor])
                    emit("answer")
                }
            }
        }
        val vm = ChatViewModel(provider)
        try {
            vm.send("question")
            val state = withTimeout(5_000) { vm.state.first { !it.isStreaming } }

            assertEquals("answer", state.messages.last().content)
            assertNotSame(caller, factoryThread.await())
            assertNotSame(caller, collectorThread.await())
            assertSame(Dispatchers.Default, collectorContext.await())
            assertNull(state.error)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun cancellationOnTheCallerWaitsForBackgroundCleanupAndPreservesTheReceivedSnapshot() = runBlocking {
        val received = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val text = "received\uD83D\uDE42".repeat(10_000)
        val provider = object : LlmProvider {
            override val name: String = "fake"

            override fun streamChat(messages: List<ChatMessage>): Flow<String> = flow {
                try {
                    emit(text)
                    received.complete(Unit)
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        releaseCleanup.await()
                    }
                }
            }
        }
        val vm = ChatViewModel(provider)
        try {
            vm.send("question")
            withTimeout(5_000) { received.await() }
            vm.cancel()
            withTimeout(5_000) { cleanupStarted.await() }

            assertTrue(vm.state.value.isStreaming)
            vm.retry()
            vm.send("ignored")
            releaseCleanup.complete(Unit)
            val state = withTimeout(5_000) { vm.state.first { !it.isStreaming } }

            assertEquals(listOf("question", text), state.messages.map { it.content })
            assertFalse(state.messages.last().streaming)
            assertNull(state.error)
        } finally {
            releaseCleanup.complete(Unit)
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun backgroundFailurePublishesAllChunksAfterCollectorCleanup() = runBlocking {
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val chunks = List(20) { "$it|".repeat(1_000) }
        val provider = object : LlmProvider {
            override val name: String = "fake"

            override fun streamChat(messages: List<ChatMessage>): Flow<String> = flow {
                try {
                    chunks.forEach { emit(it) }
                    throw IllegalStateException("offline")
                } finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        releaseCleanup.await()
                    }
                }
            }
        }
        val vm = ChatViewModel(provider)
        try {
            vm.send("question")
            withTimeout(5_000) { cleanupStarted.await() }
            assertTrue(vm.state.value.isStreaming)
            releaseCleanup.complete(Unit)
            val state = withTimeout(5_000) { vm.state.first { !it.isStreaming } }

            assertEquals(chunks.joinToString(""), state.messages.last().content)
            assertEquals("offline", state.error)
            assertEquals(2, state.messages.size)
        } finally {
            releaseCleanup.complete(Unit)
            vm.viewModelScope.cancel()
        }
    }
}
