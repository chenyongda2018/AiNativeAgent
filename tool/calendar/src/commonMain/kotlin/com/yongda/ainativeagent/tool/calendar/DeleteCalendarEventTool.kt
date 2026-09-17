package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.llm.core.ToolDefinition
import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import com.yongda.ainativeagent.tool.core.AgentTool
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolResults
import kotlinx.coroutines.CancellationException

/**
 * 删除日程（写操作）。链路：申请 READ → 读取目标以确认（展示标题 / 时间 / 目标日历，重复日程标注整系列删除）
 * → 申请 WRITE → 删除。事件不存在返回 [C.ERR_EVENT_NOT_FOUND]，用户取消返回 [C.ERR_USER_CANCELLED]。
 */
class DeleteCalendarEventTool(
    private val dataSource: CalendarDataSource,
    private val permissions: CalendarPermissionController,
    private val confirmation: CalendarConfirmationPresenter,
) : AgentTool {

    override val definition: ToolDefinition = C.deleteDefinition

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val eventId = when (val r = CalendarArgs.parseDelete(argumentsJson)) {
            is ArgResult.Fail -> return ToolResults.failure(r.code, r.message)
            is ArgResult.Ok -> r.value
        }

        permissions.requireGranted(CalendarPermission.READ)?.let { return it }

        val existing = try {
            dataSource.getEvent(eventId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            return ToolResults.failure(C.ERR_CALENDAR_ERROR, t.message ?: "读取日程失败")
        } ?: return ToolResults.failure(C.ERR_EVENT_NOT_FOUND, "找不到 eventId=$eventId 的日程")

        val decision = confirmation.confirm(
            CalendarConfirmation.Delete(
                title = existing.title,
                timeText = CalendarUpdatePlanner.timeText(
                    existing.startMillis, existing.endMillis, existing.allDay,
                    existing.timeZone, existing.startDisplay, existing.endDisplay,
                ),
                targetCalendar = existing.calendarName ?: "未知日历",
                recurring = existing.isRecurring,
            ),
        )
        if (decision == ConfirmationDecision.CANCELLED) {
            return ToolResults.terminalFailure(C.ERR_USER_CANCELLED, "用户取消了删除日程")
        }

        permissions.requireGranted(CalendarPermission.WRITE)?.let { return it }

        return try {
            val ok = dataSource.deleteEvent(eventId)
            if (ok) {
                ToolExecutionResult(C.deleteSuccessJson(eventId), ok = true)
            } else {
                ToolResults.failure(C.ERR_CALENDAR_ERROR, "删除未影响任何日程")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            ToolResults.failure(C.ERR_CALENDAR_ERROR, t.message ?: "删除日程失败")
        }
    }
}
