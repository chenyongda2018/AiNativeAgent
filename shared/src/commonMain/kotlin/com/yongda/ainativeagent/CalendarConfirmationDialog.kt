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
import com.yongda.ainativeagent.tool.calendar.CalendarConfirmation

/**
 * 应用级的日历写操作确认弹窗。真正落库前展示：新增（标题/时间/目标日历）、修改（逐字段前→后差异）、
 * 删除（目标标题/时间/日历）。重复日程标注「整个系列」。用户确认或取消回传给 [CalendarConfirmation] 桥。
 *
 * 纯 UI：不含任何业务/权限/网络逻辑，[request] 为 null 时不渲染。
 */
@Composable
fun CalendarConfirmationDialog(
    request: CalendarConfirmation?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (request == null) return

    val destructive = request is CalendarConfirmation.Delete
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleOf(request)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (request) {
                    is CalendarConfirmation.Create -> {
                        Line("标题", request.title)
                        Line(if (request.allDay) "日期" else "时间", request.timeText)
                        Line("目标日历", request.targetCalendar)
                    }

                    is CalendarConfirmation.Update -> {
                        Text("《${request.title}》将修改以下字段：", fontWeight = FontWeight.Medium)
                        if (request.recurring) RecurringWarning("修改")
                        request.changes.forEach { change ->
                            Line(change.label, "${change.before} → ${change.after}")
                        }
                    }

                    is CalendarConfirmation.Delete -> {
                        Line("标题", request.title)
                        Line("时间", request.timeText)
                        Line("目标日历", request.targetCalendar)
                        if (request.recurring) RecurringWarning("删除")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    if (destructive) "删除" else "确认",
                    color = if (destructive) ChatTheme.colors.error else ChatTheme.colors.brand,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun Line(label: String, value: String) {
    Text(
        text = "$label：$value",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun RecurringWarning(action: String) {
    Text(
        text = "⚠ 这是重复日程，将${action}整个系列。",
        color = ChatTheme.colors.amber,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

private fun titleOf(request: CalendarConfirmation): String = when (request) {
    is CalendarConfirmation.Create -> "新增日程"
    is CalendarConfirmation.Update -> "修改日程"
    is CalendarConfirmation.Delete -> "删除日程"
}
