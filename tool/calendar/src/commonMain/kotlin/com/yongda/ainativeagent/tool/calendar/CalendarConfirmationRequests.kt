package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.core.ConfirmationField
import com.yongda.ainativeagent.tool.core.ToolConfirmationRequest

/** 把日历写操作参数组装为通用 [ToolConfirmationRequest]，供应用级确认弹窗渲染。 */
internal object CalendarConfirmationRequests {

    fun create(
        title: String,
        timeText: String,
        targetCalendar: String,
        allDay: Boolean,
    ): ToolConfirmationRequest = ToolConfirmationRequest(
        title = "新增日程",
        fields = listOf(
            ConfirmationField("标题", title),
            ConfirmationField(if (allDay) "日期" else "时间", timeText),
            ConfirmationField("目标日历", targetCalendar),
        ),
        confirmLabel = "确认",
        destructive = false,
    )

    fun update(
        title: String,
        changes: List<FieldChange>,
        recurring: Boolean,
    ): ToolConfirmationRequest = ToolConfirmationRequest(
        title = "修改日程",
        subtitle = "《$title》将修改以下字段：",
        fields = changes.map { ConfirmationField(it.label, "${it.before} → ${it.after}") },
        warning = if (recurring) "这是重复日程，将修改整个系列。" else null,
        confirmLabel = "确认",
        destructive = false,
    )

    fun delete(
        title: String,
        timeText: String,
        targetCalendar: String,
        recurring: Boolean,
    ): ToolConfirmationRequest = ToolConfirmationRequest(
        title = "删除日程",
        fields = listOf(
            ConfirmationField("标题", title),
            ConfirmationField("时间", timeText),
            ConfirmationField("目标日历", targetCalendar),
        ),
        warning = if (recurring) "这是重复日程，将删除整个系列。" else null,
        confirmLabel = "删除",
        destructive = true,
    )
}
