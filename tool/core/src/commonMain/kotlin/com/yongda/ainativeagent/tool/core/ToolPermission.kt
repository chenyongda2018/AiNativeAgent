package com.yongda.ainativeagent.tool.core

/**
 * 平台无关的运行时权限描述。各工具模块只声明权限标识与向用户解释用途的文案，由宿主映射为具体平台权限。
 *
 * @param id 唯一权限标识，例如 `"calendar.read"`。
 * @param label 用于权限错误提示的简短中文名称，例如 `"读取日历"`。
 * @param rationaleTitle 教育引导弹窗标题。
 * @param rationaleMessage 教育引导弹窗正文，向用户说明为何需要该权限。
 */
data class ToolPermission(
    val id: String,
    val label: String,
    val rationaleTitle: String,
    val rationaleMessage: String,
)

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
 * 通用运行时权限控制器。由宿主侧实现（如 Android Activity Result API），从平台组合边界注入。
 * commonMain / ViewModel 不持有 Activity，只依赖本抽象；[request] 挂起直到用户在系统弹窗做出选择。
 */
interface ToolPermissionController {
    suspend fun request(permission: ToolPermission): PermissionResult
}

/** 权限与用户交互相关的通用错误码。 */
object ToolPermissionErrors {
    const val ERR_PERMISSION_DENIED: String = "PERMISSION_DENIED"
    const val ERR_PERMISSION_PERMANENTLY_DENIED: String = "PERMISSION_PERMANENTLY_DENIED"
    const val ERR_PERMISSION_CANCELLED: String = "PERMISSION_CANCELLED"
    const val ERR_USER_CANCELLED: String = "USER_CANCELLED"
}

/**
 * 通用权限申请与结构化错误映射：把 [PermissionResult] 统一转为 [ToolExecutionResult]。
 *
 * 所有非授予结果都标记为 [ToolExecutionResult.terminal]：本轮内再次申请只会反复弹窗打扰用户，
 * 应由 Agent Loop 停止后续工具调用，转为自然语言告知用户。
 */
suspend fun ToolPermissionController.requireGranted(
    permission: ToolPermission,
): ToolExecutionResult? = when (request(permission)) {
    PermissionResult.GRANTED -> null
    PermissionResult.DENIED -> ToolResults.terminalFailure(
        ToolPermissionErrors.ERR_PERMISSION_DENIED,
        "用户拒绝了${permission.label}权限，无法继续。可稍后重新发起并说明用途。",
    )
    PermissionResult.PERMANENTLY_DENIED -> ToolResults.terminalFailure(
        ToolPermissionErrors.ERR_PERMISSION_PERMANENTLY_DENIED,
        "${permission.label}权限被永久拒绝，请在系统设置中手动开启后重试。",
    )
    PermissionResult.CANCELLED -> ToolResults.terminalFailure(
        ToolPermissionErrors.ERR_PERMISSION_CANCELLED,
        "${permission.label}权限申请被取消。",
    )
}
