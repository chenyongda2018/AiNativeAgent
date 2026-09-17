package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.core.AgentTool

/**
 * 组装四个日历工具。由平台组合边界注入平台实现（[CalendarDataSource]、[CalendarPermissionController]、
 * [CalendarConfirmationPresenter]、[CurrentTime]），产出可直接放进 ToolRegistry 的 [AgentTool] 列表。
 */
object CalendarTools {

    fun create(
        dataSource: CalendarDataSource,
        permissions: CalendarPermissionController,
        confirmation: CalendarConfirmationPresenter,
        now: CurrentTime,
    ): List<AgentTool> = listOf(
        QueryCalendarEventsTool(dataSource, permissions, now),
        CreateCalendarEventTool(dataSource, permissions, confirmation),
        UpdateCalendarEventTool(dataSource, permissions, confirmation),
        DeleteCalendarEventTool(dataSource, permissions, confirmation),
    )
}
