package com.yongda.ainativeagent.tool.calendar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一次「为什么需要该权限」的教育性提示请求。UI 据 [permission] 渲染可取消的说明弹窗。 */
data class PermissionRationale(val permission: CalendarPermission)

/**
 * 权限教育（rationale）控制器：连接「权限宿主（后台协程）」与「Compose 说明弹窗」。当系统提示应先解释用途
 * （`shouldShowRequestPermissionRationale` 为真）时，宿主先调用 [requestRationale] 挂起展示说明，用户「继续」
 * 才真正 launch 系统弹窗，「取消」则终止本次申请（区别于系统层的拒绝/永久拒绝）。
 *
 * 与 [CalendarConfirmationController] 同构：进程内长期存活、单发、协程取消时撤下弹窗。
 */
class PermissionRationaleController {

    private val _pending = MutableStateFlow<PermissionRationale?>(null)
    val pending: StateFlow<PermissionRationale?> = _pending.asStateFlow()

    private var deferred: CompletableDeferred<Boolean>? = null

    /** 返回 true 表示用户同意继续申请，false 表示用户取消。 */
    suspend fun requestRationale(permission: CalendarPermission): Boolean {
        val d = CompletableDeferred<Boolean>()
        deferred = d
        _pending.value = PermissionRationale(permission)
        return try {
            d.await()
        } catch (cancellation: CancellationException) {
            if (deferred === d) {
                deferred = null
                _pending.value = null
            }
            throw cancellation
        } finally {
            if (deferred === d) {
                deferred = null
                _pending.value = null
            }
        }
    }

    /** UI：用户点「继续」。 */
    fun proceed() {
        deferred?.complete(true)
    }

    /** UI：用户点「取消」或关闭弹窗。 */
    fun dismiss() {
        deferred?.complete(false)
    }
}
