package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.llm.core.ToolDefinition
import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import com.yongda.ainativeagent.tool.core.AgentTool
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolResults
import kotlinx.coroutines.CancellationException

/**
 * 新增日程（写操作）。推荐链路：按需申请 READ → 解析目标可写日历 → 应用级确认 → 申请 WRITE → 落库。
 * 缺少可写日历、目标日历不存在、用户取消确认、权限被拒均返回明确结构化错误，不伪造成功。
 */
class CreateCalendarEventTool(
    private val dataSource: CalendarDataSource,
    private val permissions: CalendarPermissionController,
    private val confirmation: CalendarConfirmationPresenter,
) : AgentTool {

    override val definition: ToolDefinition = C.createDefinition

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val args = when (val r = CalendarArgs.parseCreate(argumentsJson)) {
            is ArgResult.Fail -> return ToolResults.failure(r.code, r.message)
            is ArgResult.Ok -> r.value
        }

        // 先申请读权限以列出可写日历、解析目标。
        permissions.requireGranted(CalendarPermission.READ)?.let { return it }

        val target = try {
            resolveTarget(args.calendarId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            return ToolResults.failure(C.ERR_CALENDAR_ERROR, t.message ?: "解析目标日历失败")
        }
        val calendar = when (target) {
            is TargetResult.Fail -> return target.result
            is TargetResult.Ok -> target.calendar
        }

        // 应用级确认（展示标题 / 时间 / 目标日历）。
        val decision = confirmation.confirm(
            CalendarConfirmation.Create(
                title = args.title,
                timeText = args.displayTime,
                targetCalendar = calendar.displayName,
                allDay = args.allDay,
            ),
        )
        if (decision == ConfirmationDecision.CANCELLED) {
            return ToolResults.terminalFailure(C.ERR_USER_CANCELLED, "用户取消了新增日程")
        }

        // 确认后再申请写权限（不依赖权限组联动）。
        permissions.requireGranted(CalendarPermission.WRITE)?.let { return it }

        val draft = EventDraft(
            calendarId = calendar.id,
            title = args.title,
            description = args.description,
            location = args.location,
            startMillis = args.startMillis,
            endMillis = args.endMillis,
            allDay = args.allDay,
            timeZone = args.timeZone,
        )
        return try {
            val id = dataSource.createEvent(draft)
            ToolExecutionResult(C.createSuccessJson(id, calendar.displayName), ok = true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            ToolResults.failure(C.ERR_CALENDAR_ERROR, t.message ?: "新增日程失败")
        }
    }

    private sealed interface TargetResult {
        data class Ok(val calendar: CalendarInfo) : TargetResult
        data class Fail(val result: ToolExecutionResult) : TargetResult
    }

    private suspend fun resolveTarget(requestedId: Long?): TargetResult {
        val writable = dataSource.writableCalendars().filter { it.isWritable }
        if (writable.isEmpty()) {
            return TargetResult.Fail(
                ToolResults.failure(C.ERR_NO_WRITABLE_CALENDAR, "设备上没有可写入的日历，无法新增日程"),
            )
        }
        val chosen = if (requestedId != null) {
            writable.firstOrNull { it.id == requestedId }
                ?: return TargetResult.Fail(
                    ToolResults.failure(C.ERR_CALENDAR_NOT_FOUND, "指定的 calendarId=$requestedId 不存在或不可写"),
                )
        } else {
            writable.firstOrNull { it.isPrimary } ?: writable.first()
        }
        return TargetResult.Ok(chosen)
    }
}
