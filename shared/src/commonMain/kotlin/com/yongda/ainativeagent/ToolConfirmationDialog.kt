package com.yongda.ainativeagent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import com.yongda.ainativeagent.tool.core.ToolConfirmationRequest

/**
 * 应用级的通用工具操作确认弹窗。真正执行敏感/写操作前展示标题、副标题、字段列表与可选风险提示；
 * 用户确认或取消后由上层回传给 [com.yongda.ainativeagent.tool.core.ToolConfirmationController]。
 *
 * 纯 UI：不含任何具体工具业务/权限/网络逻辑，[request] 为 null 时不渲染。
 */
@Composable
fun ToolConfirmationDialog(
    request: ToolConfirmationRequest?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (request == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                request.subtitle?.let { subtitle ->
                    Text(text = subtitle, fontWeight = FontWeight.Medium)
                }
                request.warning?.let { warning ->
                    Text(
                        text = "⚠ $warning",
                        color = ChatTheme.colors.amber,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                request.fields.forEach { field ->
                    Text(
                        text = "${field.label}：${field.value}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    request.confirmLabel,
                    color = if (request.destructive) ChatTheme.colors.error else ChatTheme.colors.brand,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
