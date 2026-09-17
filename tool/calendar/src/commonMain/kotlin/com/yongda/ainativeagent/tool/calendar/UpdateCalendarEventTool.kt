package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.llm.core.ToolDefinition
import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import com.yongda.ainativeagent.tool.core.AgentTool
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolResults
import kotlinx.coroutines.CancellationException

/**
 * 修改日程（写操作）。链路：申请 READ → 读取既有事件 → 合并计算差异 → 确认（展示前后差异，重复日程标注整系列）
 * → 申请 WRITE → 更新。事件不存在返回 [C.ERR_EVENT_NOT_FOUND]，无实际变化 / 时间非法返回结构化错误。
 */
class UpdateCalendarEventTool(
    private val dataSource: CalendarDataSource,
    private val permissions: CalendarPermissionController,
    private val confirmation: CalendarConfirmationPresenter,
) : AgentTool {

    override val definition: ToolDefinition = C.updateDefinition

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val args = when (val r = CalendarArgs.parseUpdate(argumentsJson)) {
            is ArgResult.Fail -> return ToolResults.failure(r.code, r.message)
            is ArgResult.Ok -> r.value
        }

        permissions.requireGranted(CalendarPermission.READ)?.let { return it }

        val existing = try {
            dataSource.getEvent(args.eventId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            return ToolResults.failure(C.ERR_CALENDAR_ERROR, t.message ?: "读取日程失败")
        } ?: return ToolResults.failure(C.ERR_EVENT_NOT_FOUND, "找不到 eventId=${args.eventId} 的日程")

        val plan = when (val r = CalendarUpdatePlanner.plan(existing, args)) {
            is ArgResult.Fail -> return ToolResults.failure(r.code, r.message)
            is ArgResult.Ok -> r.value
        }

        val decision = confirmation.confirm(
            CalendarConfirmation.Update(
                title = existing.title,
                changes = plan.changes,
                recurring = existing.isRecurring,
            ),
        )
        if (decision == ConfirmationDecision.CANCELLED) {
            return ToolResults.terminalFailure(C.ERR_USER_CANCELLED, "用户取消了修改日程")
        }

        permissions.requireGranted(CalendarPermission.WRITE)?.let { return it }

        return try {
            val ok = dataSource.updateEvent(args.eventId, plan.draft)
            if (ok) {
                ToolExecutionResult(C.updateSuccessJson(args.eventId, plan.changes.map { it.label }), ok = true)
            } else {
                ToolResults.failure(C.ERR_CALENDAR_ERROR, "更新未影响任何日程")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            ToolResults.failure(C.ERR_CALENDAR_ERROR, t.message ?: "修改日程失败")
        }
    }
}
