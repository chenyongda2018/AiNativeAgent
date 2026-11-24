package com.yongda.ainativeagent.tool.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PermissionRequestCoordinator] 的取消 / 生命周期时序单测。覆盖两处易错点：
 *  1. 等待系统弹窗结果时协程被取消 → `CancellationException` 透传，绝不被吞成 CANCELLED
 *     （JVM 上 CancellationException 是 IllegalStateException 子类，窄兜底必须先重抛）。
 *  2. 教育弹窗挂起期间宿主被回收（[PermissionRequestCoordinator.cancel]）→ 不 launch 已注销的 launcher，且干净地以 CANCELLED 收尾。
 */
class PermissionRequestCoordinatorTest {

    @Test
    fun cancellationWhileAwaitingResultPropagatesAndIsNotSwallowedAsCancelled() = runTest {
        var launchCalls = 0
        val coordinator = PermissionRequestCoordinator(dismissRationale = {})

        var outcome: PermissionResult? = null
        var thrown: Throwable? = null
        val job = launch {
            try {
                outcome = coordinator.request(
                    isGranted = { false },
                    needsRationale = { false },
                    showRationale = { true },
                    launch = { launchCalls++ },
                    evaluate = { granted ->
                        when (granted) {
                            null -> PermissionResult.CANCELLED
                            true -> PermissionResult.GRANTED
                            else -> PermissionResult.DENIED
                        }
                    },
                )
            } catch (t: Throwable) {
                thrown = t
            }
        }

        testScheduler.runCurrent()
        assertEquals(1, launchCalls, "应已 launch 系统弹窗并等待结果")

        job.cancel()
        job.join()

        assertNull(outcome, "取消不应让 request 正常返回任何结果")
        assertTrue(thrown is CancellationException, "协程取消必须以 CancellationException 透传，实际=$thrown")
    }

    @Test
    fun cancelWhileRationalePendingSkipsLaunchAndReturnsCancelled() = runTest {
        val rationaleGate = CompletableDeferred<Boolean>()
        var dismissCalls = 0
        var launchCalls = 0
        val coordinator = PermissionRequestCoordinator(
            dismissRationale = {
                dismissCalls++
                if (!rationaleGate.isCompleted) rationaleGate.complete(true)
            },
        )

        var outcome: PermissionResult? = null
        val job = launch {
            outcome = coordinator.request(
                isGranted = { false },
                needsRationale = { true },
                showRationale = { rationaleGate.await() },
                launch = { launchCalls++ },
                evaluate = { PermissionResult.GRANTED },
            )
        }

        testScheduler.runCurrent()
        assertFalse(rationaleGate.isCompleted, "此刻应仍卡在教育弹窗上")

        coordinator.cancel()
        job.join()

        assertEquals(1, dismissCalls, "回收时应撤下陈旧教育弹窗")
        assertEquals(0, launchCalls, "失效后绝不 launch 已注销的 launcher")
        assertEquals(PermissionResult.CANCELLED, outcome, "应干净地以 CANCELLED 收尾")
    }

    @Test
    fun cancelWhileAwaitingSystemDialogReleasesRequestAsCancelled() = runTest {
        val coordinator = PermissionRequestCoordinator(dismissRationale = {})

        var outcome: PermissionResult? = null
        val job = launch {
            outcome = coordinator.request(
                isGranted = { false },
                needsRationale = { false },
                showRationale = { true },
                launch = { },
                evaluate = { granted -> if (granted == null) PermissionResult.CANCELLED else PermissionResult.GRANTED },
            )
        }

        testScheduler.runCurrent()

        coordinator.cancel()
        job.join()

        assertEquals(PermissionResult.CANCELLED, outcome)
    }

    @Test
    fun requestAfterCancelShortCircuitsToCancelled() = runTest {
        val coordinator = PermissionRequestCoordinator(dismissRationale = {})
        coordinator.cancel()

        var launchCalls = 0
        val outcome = coordinator.request(
            isGranted = { false },
            needsRationale = { false },
            showRationale = { true },
            launch = { launchCalls++ },
            evaluate = { PermissionResult.GRANTED },
        )

        assertEquals(PermissionResult.CANCELLED, outcome)
        assertEquals(0, launchCalls, "已失效宿主不应再 launch")
    }
}
