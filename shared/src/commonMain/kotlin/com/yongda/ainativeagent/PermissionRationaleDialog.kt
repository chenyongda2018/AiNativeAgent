package com.yongda.ainativeagent

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import com.yongda.ainativeagent.tool.core.PermissionRationale

/**
 * 权限教育弹窗：在真正调起系统权限窗前，向用户解释为何需要某项权限，可取消。用户点「继续」才发起系统申请，
 * 「取消」则不申请（区别于系统层的拒绝/永久拒绝）。纯 UI，[request] 为 null 时不渲染。
 */
@Composable
fun PermissionRationaleDialog(
    request: PermissionRationale?,
    onProceed: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (request == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.permission.rationaleTitle) },
        text = { Text(request.permission.rationaleMessage) },
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
