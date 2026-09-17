package com.yongda.ainativeagent

import android.app.Application
import com.yongda.ainativeagent.tool.battery.AndroidBatteryLevelTool
import com.yongda.ainativeagent.tool.battery.BatteryAgentTool
import com.yongda.ainativeagent.tool.calendar.AndroidCalendarDataSource
import com.yongda.ainativeagent.tool.calendar.CalendarConfirmationController
import com.yongda.ainativeagent.tool.calendar.CalendarPermissionGateway
import com.yongda.ainativeagent.tool.calendar.CalendarTools
import com.yongda.ainativeagent.tool.calendar.CurrentTime
import com.yongda.ainativeagent.tool.core.ToolRegistry

/**
 * 进程级 Agent 依赖图：工具注册表、日历写操作确认控制器、权限教育控制器、权限网关都在 [Application] 作用域
 * 长期存活，跨 Activity 重建保持同一实例。retained [com.yongda.ainativeagent.chat.vm.ChatViewModel] 通过
 * 组合根捕获这里的 [toolRegistry]（内部持有稳定网关/控制器），因此配置变更后不会引用到失效的 Activity 作用域对象。
 *
 * 当前 Activity 仅负责把自己的 [AndroidPermissionHost] attach 到 [permissionGateway]（见 [MainActivity]），
 * 不持有工具或控制器实例。
 */
class AiNativeAgentApp : Application() {

    val confirmationController: CalendarConfirmationController by lazy { CalendarConfirmationController() }

    val rationaleController: com.yongda.ainativeagent.tool.calendar.PermissionRationaleController by lazy {
        com.yongda.ainativeagent.tool.calendar.PermissionRationaleController()
    }

    val permissionGateway: CalendarPermissionGateway by lazy { CalendarPermissionGateway() }

    val toolRegistry: ToolRegistry by lazy {
        // 电量与日历数据源只持有 applicationContext / 其 ContentResolver，避免泄漏 Activity。
        val battery = BatteryAgentTool(AndroidBatteryLevelTool(applicationContext))
        val calendar = CalendarTools.create(
            dataSource = AndroidCalendarDataSource(applicationContext.contentResolver),
            permissions = permissionGateway,
            confirmation = confirmationController,
            now = CurrentTime { System.currentTimeMillis() },
        )
        ToolRegistry(listOf(battery) + calendar)
    }
}
