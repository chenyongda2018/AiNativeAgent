package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolResults

/** 提供「当前时间」的抽象（epoch 毫秒）。生产用系统时钟，测试可固定，避免依赖真实时间。 */
fun interface CurrentTime {
    fun nowEpochMillis(): Long
}

/**
 * 日历工具共享的权限申请与错误映射：把 [PermissionResult] 统一转为结构化 [ToolExecutionResult]，
 * 让四个工具的权限处理保持一致（拒绝 / 永久拒绝 / 取消各有明确错误码与引导文案）。
 *
 * 所有非授予结果都标记为 [ToolExecutionResult.terminal]：本轮内再次申请只会反复弹窗打扰用户，应由 Agent Loop
 * 停止后续工具调用，转为自然语言告知用户。
 */
internal suspend fun CalendarPermissionController.requireGranted(
    permission: CalendarPermission,
): ToolExecutionResult? = when (request(permission)) {
    PermissionResult.GRANTED -> null
    PermissionResult.DENIED -> ToolResults.terminalFailure(
        C.ERR_PERMISSION_DENIED,
        "用户拒绝了${permission.label()}权限，无法继续。可稍后重新发起并说明用途。",
    )
    PermissionResult.PERMANENTLY_DENIED -> ToolResults.terminalFailure(
        C.ERR_PERMISSION_PERMANENTLY_DENIED,
        "${permission.label()}权限被永久拒绝，请在系统设置中手动开启后重试。",
    )
    PermissionResult.CANCELLED -> ToolResults.terminalFailure(
        C.ERR_PERMISSION_CANCELLED,
        "${permission.label()}权限申请被取消。",
    )
}

private fun CalendarPermission.label(): String = when (this) {
    CalendarPermission.READ -> "读取日历"
    CalendarPermission.WRITE -> "写入日历"
}
