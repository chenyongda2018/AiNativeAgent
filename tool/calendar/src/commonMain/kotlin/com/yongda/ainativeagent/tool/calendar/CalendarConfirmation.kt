package com.yongda.ainativeagent.tool.calendar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一次写操作在真正落库前需要用户确认的请求。字段都是已渲染好的字符串，确认 UI 只负责展示，不含业务逻辑。
 */
sealed interface CalendarConfirmation {

    val title: String

    /** 新增：展示标题 / 时间 / 目标日历。 */
    data class Create(
        override val title: String,
        val timeText: String,
        val targetCalendar: String,
        val allDay: Boolean,
    ) : CalendarConfirmation

    /** 修改：展示目标标题与逐字段「前 → 后」差异；重复日程带整系列提示。 */
    data class Update(
        override val title: String,
        val changes: List<FieldChange>,
        val recurring: Boolean,
    ) : CalendarConfirmation

    /** 删除：展示目标标题 / 时间 / 目标日历；重复日程带整系列提示。 */
    data class Delete(
        override val title: String,
        val timeText: String,
        val targetCalendar: String,
        val recurring: Boolean,
    ) : CalendarConfirmation
}

/** 用户对确认请求的决定。 */
enum class ConfirmationDecision { CONFIRMED, CANCELLED }

/**
 * 确认展示器：工具在写操作前调用 [confirm] 挂起，直到用户在应用级 UI 做出选择。由平台组合边界注入实现。
 */
interface CalendarConfirmationPresenter {
    suspend fun confirm(request: CalendarConfirmation): ConfirmationDecision
}

/**
 * 连接「工具（后台协程）」与「Compose 确认 UI」的控制器：既是 [CalendarConfirmationPresenter]（给工具调用），
 * 又对外暴露 [pending] 状态（给 UI 观察渲染弹窗），[resolve] 供 UI 回传用户决定。
 *
 * 单发语义：同一时刻只处理一个确认请求；[confirm] 内部用 [CompletableDeferred] 挂起，[resolve]/取消将其完成。
 * 协程取消时把挂起的请求视为「取消」，清空 [pending] 并让弹窗消失，不会泄漏或卡死。
 */
class CalendarConfirmationController : CalendarConfirmationPresenter {

    private val _pending = MutableStateFlow<CalendarConfirmation?>(null)
    val pending: StateFlow<CalendarConfirmation?> = _pending.asStateFlow()

    private var deferred: CompletableDeferred<ConfirmationDecision>? = null

    override suspend fun confirm(request: CalendarConfirmation): ConfirmationDecision {
        val d = CompletableDeferred<ConfirmationDecision>()
        deferred = d
        _pending.value = request
        return try {
            d.await()
        } catch (cancellation: CancellationException) {
            // 协程被取消：撤下弹窗，异常继续向上传播（中断生成 / 页面销毁）。
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
