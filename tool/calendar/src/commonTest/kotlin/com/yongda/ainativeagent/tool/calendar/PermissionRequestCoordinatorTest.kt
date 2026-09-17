package com.yongda.ainativeagent.tool.calendar

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
                    // launch 成功但系统永不回调结果 → request 挂在 deferred.await() 上。
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

        // 让子协程推进到 deferred.await() 挂起点。
        testScheduler.runCurrent()
        assertEquals(1, launchCalls, "应已 launch 系统弹窗并等待结果")

        job.cancel()
        job.join()

        assertNull(outcome, "取消不应让 request 正常返回任何结果")
        assertTrue(thrown is CancellationException, "协程取消必须以 CancellationException 透传，实际=$thrown")
    }

    @Test
    fun cancelWhileRationalePendingSkipsLaunchAndReturnsCancelled() = runTest {
        // dismiss 时把教育弹窗解析为「继续(true)」这一极端情形：即便如此，失效标记也必须拦住 launch。
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

        // 推进到教育弹窗挂起点：尚未做出选择。
        testScheduler.runCurrent()
        assertFalse(rationaleGate.isCompleted, "此刻应仍卡在教育弹窗上")

        // 宿主随 Activity 销毁被回收。
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
                launch = { /* 已 launch，等待系统回调 */ },
                evaluate = { granted -> if (granted == null) PermissionResult.CANCELLED else PermissionResult.GRANTED },
            )
        }

        testScheduler.runCurrent()

        // 宿主回收：唤醒挂起的系统弹窗申请并以取消（null）收尾——这不是协程取消，request 正常返回 CANCELLED。
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
