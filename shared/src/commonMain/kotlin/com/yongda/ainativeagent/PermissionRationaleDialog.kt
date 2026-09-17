package com.yongda.ainativeagent

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import com.yongda.ainativeagent.tool.calendar.CalendarPermission
import com.yongda.ainativeagent.tool.calendar.PermissionRationale

/**
 * 权限教育弹窗：在真正调起系统权限窗前，向用户解释为何需要读/写日历权限，可取消。用户点「继续」才发起系统申请，
 * 「取消」则不申请（区别于系统层的拒绝/永久拒绝）。纯 UI，[request] 为 null 时不渲染。
 */
@Composable
fun PermissionRationaleDialog(
    request: PermissionRationale?,
    onProceed: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (request == null) return

    val (title, body) = when (request.permission) {
        CalendarPermission.READ -> "需要读取日历权限" to
            "为了帮你查询、核对日程，需要读取设备日历。为生成回答，读取到的日程内容（标题、时间、地点等）" +
            "会发送给云端模型 DeepSeek 处理，请勿在日程中保存敏感信息。"
        CalendarPermission.WRITE -> "需要写入日历权限" to
            "为了按你的确认新增 / 修改 / 删除日程，需要写入设备日历。每次写入前都会先向你确认。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onProceed) {
                Text("继续", color = ChatTheme.colors.brand, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
