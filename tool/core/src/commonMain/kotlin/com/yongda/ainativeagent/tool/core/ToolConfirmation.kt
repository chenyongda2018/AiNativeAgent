package com.yongda.ainativeagent.tool.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 确认弹窗中的一行键值展示信息。 */
data class ConfirmationField(
    val label: String,
    val value: String,
)

/**
 * 通用工具执行前的应用级确认请求。所有字段均为已渲染好的展示文本，UI 只负责展示，不含工具业务逻辑。
 */
data class ToolConfirmationRequest(
    val title: String,
    val subtitle: String? = null,
    val fields: List<ConfirmationField>,
    val warning: String? = null,
    val confirmLabel: String = "确认",
    val destructive: Boolean = false,
)

/** 用户对确认请求的决定。 */
enum class ConfirmationDecision { CONFIRMED, CANCELLED }

/**
 * 通用确认展示器：工具在敏感/写操作前调用 [confirm] 挂起，直到用户在应用级 UI 做出选择。由平台组合边界注入实现。
 */
interface ToolConfirmationPresenter {
    suspend fun confirm(request: ToolConfirmationRequest): ConfirmationDecision
}

/**
 * 连接「工具（后台协程）」与「Compose 确认 UI」的通用控制器：既是 [ToolConfirmationPresenter]（给工具调用），
 * 又对外暴露 [pending] 状态（给 UI 观察渲染弹窗），[resolve] 供 UI 回传用户决定。
 *
 * 串行单发语义：通过 [Mutex] 串行化并发确认请求，避免后者覆盖前者挂起中的弹窗句柄；
 * [confirm] 内部用 [CompletableDeferred] 挂起，协程取消时清空 [pending] 并让弹窗消失，不会泄漏或卡死。
 */
class ToolConfirmationController : ToolConfirmationPresenter {

    private val mutex = Mutex()
    private val _pending = MutableStateFlow<ToolConfirmationRequest?>(null)
    val pending: StateFlow<ToolConfirmationRequest?> = _pending.asStateFlow()

    private var deferred: CompletableDeferred<ConfirmationDecision>? = null

    override suspend fun confirm(request: ToolConfirmationRequest): ConfirmationDecision = mutex.withLock {
        val d = CompletableDeferred<ConfirmationDecision>()
        deferred = d
        _pending.value = request
        try {
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

    /** UI 回传用户决定。无挂起请求时忽略。 */
    fun resolve(decision: ConfirmationDecision) {
        deferred?.complete(decision)
    }

    /** UI 便捷入口：用户点「确认」。 */
    fun confirm() = resolve(ConfirmationDecision.CONFIRMED)

    /** UI 便捷入口：用户点「取消」或关闭弹窗。 */
    fun cancel() = resolve(ConfirmationDecision.CANCELLED)
}
