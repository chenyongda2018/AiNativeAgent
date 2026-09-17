package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.llm.core.ToolDefinition
import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import com.yongda.ainativeagent.tool.core.AgentTool
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolResults
import kotlinx.coroutines.CancellationException

/**
 * 查询日程（只读）。按需申请 READ_CALENDAR，通过 Instances 展开重复日程，缺省区间为「当前时间起 7 天」。
 * 数据源异常一律转结构化 [C.ERR_CALENDAR_ERROR]，协程取消照常向上传播。
 */
class QueryCalendarEventsTool(
    private val dataSource: CalendarDataSource,
    private val permissions: CalendarPermissionController,
    private val now: CurrentTime,
) : AgentTool {

    override val definition: ToolDefinition = C.queryDefinition

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val args = when (val r = CalendarArgs.parseQuery(argumentsJson)) {
            is ArgResult.Fail -> return ToolResults.failure(r.code, r.message)
            is ArgResult.Ok -> r.value
        }

        // 校验「有效区间」：缺省 timeMin 落到当前时间后，仅给一个过去的 timeMax 会导致 start > end。
        // 在申请权限 / 触达数据源之前就拦截，避免弹权限却查一个空/无意义区间。
        val start = args.timeMinMillis ?: now.nowEpochMillis()
        val end = args.timeMaxMillis ?: (start + 7 * CalendarTime.MILLIS_PER_DAY)
        if (end <= start) {
            return ToolResults.failure(C.ERR_INVALID_TIME_RANGE, "有效查询区间的结束时间必须晚于开始时间")
        }

        permissions.requireGranted(CalendarPermission.READ)?.let { return it }

        val query = EventQuery(start, end, args.keyword, args.limit)
        return try {
            val events = dataSource.queryEvents(query)
            ToolExecutionResult(C.querySuccessJson(events), ok = true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            ToolResults.failure(C.ERR_CALENDAR_ERROR, t.message ?: "查询日历失败")
        }
    }
}
