package com.yongda.ainativeagent.tool.calendar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * 纯粹、平台无关的权限申请协调状态机。把 Activity 侧宿主（[PermissionHost] 的实现）里易错的
 * 「取消语义 + 生命周期时序」抽离出来，使其可在 commonTest 用 `runTest` 覆盖，不依赖真实 Activity。
 *
 * 平台副作用由 [request] 的入参 lambda 注入（是否已授予 / 是否需解释用途 / 展示教育弹窗 / 真正 launch 系统窗 /
 * 结果映射），协调器只负责三件事：
 *  1. **协程取消透传**：等待系统弹窗结果（[CompletableDeferred.await]）时若协程被取消，`CancellationException`
 *     照常向上抛出，绝不被吞成 [PermissionResult.CANCELLED]。注意 JVM 上
 *     `java.util.concurrent.CancellationException` 恰是 `IllegalStateException` 的子类，故对 launch 的
 *     `IllegalStateException` 兜底**只**包住 [launch] 本身，且先重抛 `CancellationException`。
 *  2. **失效的 launcher 兜底**：仅 [launch] 抛 `IllegalStateException`（launcher 随 Activity 注销）时按取消收尾。
 *  3. **宿主回收时序**：[cancel] 置失效、唤醒挂起申请、撤下陈旧教育弹窗；教育弹窗返回后再次校验 [active]，
 *     即便弹窗恰好返回「继续」也绝不 launch 已注销的 launcher。
 */
class PermissionRequestCoordinator(
    private val dismissRationale: () -> Unit,
) {

    // 系统弹窗结果的挂起句柄；null 完成值表示被宿主回收取消（区别于协程取消的 CancellationException）。
    private var pending: CompletableDeferred<Boolean?>? = null

    // 宿主是否仍附着于存活的 Activity；cancel() 后置 false 且不再恢复。仅在单线程（主线程）读写。
    private var active = true

    // 是否正卡在教育弹窗等待用户决定；用于宿主回收时撤下这张陈旧弹窗。
    private var awaitingRationale = false

    /**
     * 执行一次权限申请。所有平台细节以 lambda 注入：
     * @param isGranted 是否已授予（已授予直接返回 GRANTED）。
     * @param needsRationale 系统是否建议先解释用途。
     * @param showRationale 展示可取消的教育弹窗，返回 true=继续、false=取消。
     * @param launch 真正调起系统权限窗；launcher 已注销时可抛 [IllegalStateException]。
     * @param evaluate 把系统回调的 granted（null=取消）映射为最终 [PermissionResult]。
     */
    suspend fun request(
        isGranted: () -> Boolean,
        needsRationale: () -> Boolean,
        showRationale: suspend () -> Boolean,
        launch: () -> Unit,
        evaluate: (Boolean?) -> PermissionResult,
    ): PermissionResult {
        if (!active) return PermissionResult.CANCELLED
        if (isGranted()) return PermissionResult.GRANTED

        // 请求前若系统建议解释用途，先展示可取消的教育 UI；用户取消则不 launch。
        if (needsRationale()) {
            awaitingRationale = true
            val proceed = try {
                showRationale()
            } finally {
                awaitingRationale = false
            }
            if (!proceed) return PermissionResult.CANCELLED
        }

        // 教育弹窗返回后，宿主可能已随 Activity 销毁（旋转）：此时 launcher 已注销，绝不能再 launch。
        if (!active) return PermissionResult.CANCELLED

        val deferred = CompletableDeferred<Boolean?>()
        pending = deferred
        return try {
            val launched = try {
                launch()
                true
            } catch (cancellation: CancellationException) {
                // 协程取消永远透传，绝不当作 launcher 失效兜底。
                throw cancellation
            } catch (illegalState: IllegalStateException) {
                // 仅 launch() 的 IllegalStateException 落此分支：launcher 已随 Activity 注销 → 按取消收尾。
                false
            }
            // deferred.await() 在窄兜底之外：其协程取消（CancellationException / JobCancellationException）
            // 照常向上传播，不会被上面的 IllegalStateException 分支吞掉。
            val granted = if (launched) deferred.await() else null
            evaluate(granted)
        } finally {
            if (pending === deferred) pending = null
        }
    }

    /** 系统权限窗回调：投递用户选择结果，唤醒 [request] 的挂起等待。 */
    fun deliverResult(granted: Boolean) {
        pending?.let { if (!it.isCompleted) it.complete(granted) }
    }

    /**
     * 宿主回收（detach / onDestroy）时调用：标记失效、以取消收尾挂起的系统弹窗申请，
     * 并撤下可能正显示的教育弹窗（否则挂起协程会一直持有网关锁直到用户操作陈旧弹窗）。
     */
    fun cancel() {
        active = false
        pending?.let { if (!it.isCompleted) it.complete(null) }
        if (awaitingRationale) dismissRationale()
    }
}
