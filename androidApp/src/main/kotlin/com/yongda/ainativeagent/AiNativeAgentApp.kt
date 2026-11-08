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
 * 进程级 Agent 依赖图组合根。
 *
 * 在 [Application] 进程生命周期内维护单例工具注册表、权限网关及确认控制器，
 * 跨配置变更（如屏幕旋转）与 Activity 重建保持稳定。
 * retained [com.yongda.ainativeagent.chat.vm.ChatViewModel] 间接持有这里的依赖，
 * 避免引用随 Activity 重建而失效的对象。
 *
 * [Application] 不持有 Activity 实例；前台 [MainActivity] 在创建与销毁时
 * 将自身的临时权限宿主挂载（attach）或解绑（detach）到稳定的 [permissionGateway]。
 */
class AiNativeAgentApp : Application() {

    /** 日历写操作二次确认控制器。 */
    val confirmationController: CalendarConfirmationController by lazy { CalendarConfirmationController() }

    /** 权限被拒后的教育引导提示控制器。 */
    val rationaleController: com.yongda.ainativeagent.tool.calendar.PermissionRationaleController by lazy {
        com.yongda.ainativeagent.tool.calendar.PermissionRationaleController()
    }

    /** 跨 Activity 重建保持稳定的日历权限请求网关。 */
    val permissionGateway: CalendarPermissionGateway by lazy { CalendarPermissionGateway() }

    /** 进程内统一的 Agent 工具注册表。 */
    val toolRegistry: ToolRegistry by lazy {
        // 电量工具接收 applicationContext，日历数据源接收其 ContentResolver，避免持有 Activity 导致内存泄漏。
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
