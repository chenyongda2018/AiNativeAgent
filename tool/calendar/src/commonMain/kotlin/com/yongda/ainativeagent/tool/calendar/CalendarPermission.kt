package com.yongda.ainativeagent.tool.calendar

/** 日历运行时权限（危险权限）。READ / WRITE 各自独立申请，不依赖权限组自动联动。 */
enum class CalendarPermission { READ, WRITE }

/** 权限申请结果。区分拒绝与「永久拒绝（勾选不再询问）」，供工具回填不同结构化错误与引导文案。 */
enum class PermissionResult {
    GRANTED,

    /** 本次拒绝，后续仍可再次弹窗申请。 */
    DENIED,

    /** 永久拒绝（不再询问）：需引导用户到系统设置手动开启，不应继续弹窗。 */
    PERMANENTLY_DENIED,

    /** 申请过程被取消（例如 Activity 销毁 / 协程取消前的宿主回收），返回结构化错误而非崩溃。 */
    CANCELLED,
}

/**
 * 运行时权限协调器。由 Activity 侧实现（Activity Result API），从平台组合边界注入。
 * commonMain / ViewModel 不持有 Activity，只依赖本抽象；[request] 挂起直到用户在系统弹窗做出选择。
 */
interface CalendarPermissionController {
    suspend fun request(permission: CalendarPermission): PermissionResult
}
