package com.yongda.ainativeagent.tool.calendar

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 当前已附着的、能真正弹系统权限窗的宿主（Activity 作用域）。由平台侧实现并在 Activity 生命周期内 attach 到
 * [CalendarPermissionGateway]。commonMain 只依赖本抽象，不接触 Activity。
 */
interface PermissionHost {
    suspend fun request(permission: CalendarPermission): PermissionResult
}

/**
 * 权限网关：一个**进程内长期存活**的稳定 [CalendarPermissionController]，供 retained ViewModel / 工具注册表
 * 安全捕获，从而**不会**捕获随配置变更（旋转、深色模式切换、Activity 重建）而失效的 Activity 作用域对象。
 *
 * 当前 Activity 在 onCreate 时把自己的 [PermissionHost] [attach] 进来，onDestroy 时 [detach]；网关只是把
 * [request] 转发给当前附着的宿主。宿主缺席（重建间隙）时返回 [PermissionResult.CANCELLED]，由上层转结构化错误，
 * 绝不挂起或崩溃。
 *
 * 并发：用 [Mutex] 串行化权限申请，避免多个工具/协程同时触发系统弹窗（Activity Result 单发语义）。
 */
class CalendarPermissionGateway : CalendarPermissionController {

    private val mutex = Mutex()
    private var host: PermissionHost? = null

    fun attach(host: PermissionHost) {
        this.host = host
    }

    /** 仅当传入的正是当前宿主时才清空，避免旧 Activity 的 detach 覆盖新 Activity 的 attach。 */
    fun detach(host: PermissionHost) {
        if (this.host === host) this.host = null
    }

    override suspend fun request(permission: CalendarPermission): PermissionResult = mutex.withLock {
        val current = host ?: return@withLock PermissionResult.CANCELLED
        current.request(permission)
    }
}
