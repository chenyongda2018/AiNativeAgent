package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.core.ToolPermission

/** 日历工具所需运行时权限定义。READ / WRITE 各自独立申请，不依赖权限组自动联动。 */
object CalendarPermissions {

    val READ: ToolPermission = ToolPermission(
        id = "calendar.read",
        label = "读取日历",
        rationaleTitle = "需要读取日历权限",
        rationaleMessage = "为了帮你查询、核对日程，需要读取设备日历。为生成回答，读取到的日程内容（标题、时间、地点等）" +
            "会发送给云端模型 DeepSeek 处理，请勿在日程中保存敏感信息。",
    )

    val WRITE: ToolPermission = ToolPermission(
        id = "calendar.write",
        label = "写入日历",
        rationaleTitle = "需要写入日历权限",
        rationaleMessage = "为了按你的确认新增 / 修改 / 删除日程，需要写入设备日历。每次写入前都会先向你确认。",
    )
}
