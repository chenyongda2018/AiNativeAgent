package com.yongda.ainativeagent

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.yongda.ainativeagent.tool.calendar.CalendarPermission
import com.yongda.ainativeagent.tool.calendar.PermissionHost
import com.yongda.ainativeagent.tool.calendar.PermissionRationaleController
import com.yongda.ainativeagent.tool.calendar.PermissionRequestCoordinator
import com.yongda.ainativeagent.tool.calendar.PermissionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Activity 作用域的权限宿主：真正调用 Activity Result API 弹系统权限窗。由 [MainActivity.onCreate] 构造并
 * attach 到进程内长期存活的 [com.yongda.ainativeagent.tool.calendar.CalendarPermissionGateway]，onDestroy 时 detach，
 * 从而 retained ViewModel / 工具注册表只捕获稳定网关、永不捕获失效 Activity。
 *
 * 线程：Agent Loop 跑在 [Dispatchers.Default]，而 `ActivityResultLauncher.launch` 与
 * `shouldShowRequestPermissionRationale` / `checkSelfPermission` 都要求在主线程调用，故整段申请切到
 * [Dispatchers.Main] 的 immediate 语义执行（网关侧已用 Mutex 串行化，避免并发弹窗）。
 *
 * 取消 / 生命周期时序全部委托给平台无关的 [PermissionRequestCoordinator]（在 commonTest 有单元覆盖）：本类只负责
 * 提供 Activity 副作用（授予判断 / rationale 判断 / launch / 结果映射），并把系统回调 [PermissionRequestCoordinator.deliverResult] 转发进去。
 *
 * 流程：已授予立即返回；系统提示需要说明用途时先经 [rationale] 展示可取消的教育弹窗，用户「继续」才 launch；
 * 结果区分 授予 / 普通拒绝 / 永久拒绝；宿主被回收（detach/onDestroy）时把挂起申请以 CANCELLED 收尾。
 *
 * 配置变更（旋转）时若教育弹窗正显示：宿主随旧 Activity 销毁而 [cancelPending] → 协调器置失效并撤下弹窗；
 * 即便挂起协程稍后被恢复，也会因失效标记或对已注销 launcher 的 [IllegalStateException] 兜底而返回 CANCELLED，
 * 绝不调用已注销的 launcher 而崩溃/挂起（launcher 属于已销毁的旧 Activity）。协程取消（CancellationException）
 * 则始终原样向上传播，不会被吞成 CANCELLED。
 */
class AndroidPermissionHost(
    private val activity: ComponentActivity,
    private val rationale: PermissionRationaleController,
) : PermissionHost {

    private val coordinator = PermissionRequestCoordinator(dismissRationale = rationale::dismiss)

    private val launcher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            coordinator.deliverResult(granted)
        }

    override suspend fun request(permission: CalendarPermission): PermissionResult =
        withContext(Dispatchers.Main.immediate) {
            val androidPermission = permission.toAndroid()
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

    private fun CalendarPermission.toAndroid(): String = when (this) {
        CalendarPermission.READ -> Manifest.permission.READ_CALENDAR
        CalendarPermission.WRITE -> Manifest.permission.WRITE_CALENDAR
    }
}
