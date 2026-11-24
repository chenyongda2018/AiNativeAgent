package com.yongda.ainativeagent

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.yongda.ainativeagent.tool.calendar.CalendarPermissions
import com.yongda.ainativeagent.tool.core.PermissionHost
import com.yongda.ainativeagent.tool.core.PermissionRationaleController
import com.yongda.ainativeagent.tool.core.PermissionRequestCoordinator
import com.yongda.ainativeagent.tool.core.PermissionResult
import com.yongda.ainativeagent.tool.core.ToolPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Activity 作用域的权限宿主：真正调用 Activity Result API 弹系统权限窗。由 [MainActivity.onCreate] 构造并
 * attach 到进程内长期存活的 [com.yongda.ainativeagent.tool.core.PermissionGateway]，onDestroy 时 detach，
 * 从而 retained ViewModel / 工具注册表只捕获稳定网关、永不捕获失效 Activity。
 *
 * 线程：Agent Loop 跑在 [Dispatchers.Default]，而 `ActivityResultLauncher.launch` 与
 * `shouldShowRequestPermissionRationale` / `checkSelfPermission` 都要求在主线程调用，故整段申请切到
 * [Dispatchers.Main] 的 immediate 语义执行（网关侧已用 Mutex 串行化，避免并发弹窗）。
 *
 * 取消 / 生命周期时序全部委托给平台无关的 [PermissionRequestCoordinator]（在 `:tool:core` commonTest 有单元覆盖）：
 * 本类只负责把平台无关 [ToolPermission] 映射为 Android Manifest 权限并执行 Activity 副作用。
 */
class AndroidPermissionHost(
    private val activity: ComponentActivity,
    private val rationale: PermissionRationaleController,
    private val permissionMapper: (ToolPermission) -> String? = ::defaultAndroidPermission,
) : PermissionHost {

    private val coordinator = PermissionRequestCoordinator(dismissRationale = rationale::dismiss)

    private val launcher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            coordinator.deliverResult(granted)
        }

    override suspend fun request(permission: ToolPermission): PermissionResult =
        withContext(Dispatchers.Main.immediate) {
            val androidPermission = permissionMapper(permission) ?: return@withContext PermissionResult.DENIED
            coordinator.request(
                isGranted = { isGranted(androidPermission) },
                needsRationale = { activity.shouldShowRequestPermissionRationale(androidPermission) },
                showRationale = { rationale.requestRationale(permission) },
                launch = { launcher.launch(androidPermission) },
                evaluate = { granted ->
                    when {
                        granted == null -> PermissionResult.CANCELLED
                        granted -> PermissionResult.GRANTED
                        activity.shouldShowRequestPermissionRationale(androidPermission) -> PermissionResult.DENIED
                        else -> PermissionResult.PERMANENTLY_DENIED
                    }
                },
            )
        }

    /** 宿主回收（detach / onDestroy）时调用：委托协调器标记失效、唤醒挂起申请并撤下陈旧教育弹窗。 */
    fun cancelPending() = coordinator.cancel()

    private fun isGranted(androidPermission: String): Boolean =
        activity.checkSelfPermission(androidPermission) == PackageManager.PERMISSION_GRANTED
}

private fun defaultAndroidPermission(permission: ToolPermission): String? = when (permission.id) {
    CalendarPermissions.READ.id -> Manifest.permission.READ_CALENDAR
    CalendarPermissions.WRITE.id -> Manifest.permission.WRITE_CALENDAR
    else -> null
}
