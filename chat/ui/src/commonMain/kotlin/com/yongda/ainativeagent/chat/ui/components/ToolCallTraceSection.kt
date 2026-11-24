package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.ToolCallUi
import com.yongda.ainativeagent.chat.ui.ToolCallUiStatus
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

/** 在助手消息区展示工具调用轨迹，仅在 [toolCalls] 非空时由调用方渲染。 */
@Composable
internal fun ToolCallTraceSection(
    toolCalls: List<ToolCallUi>,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(ChatTactileTokens.radiusMedium),
        color = ChatTheme.colors.surfaceRecessed,
        border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            toolCalls.forEach { item ->
                ToolCallTraceRow(item = item)
            }
        }
    }
}

@Composable
private fun ToolCallTraceRow(item: ToolCallUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when (item.status) {
            ToolCallUiStatus.RUNNING -> RunningDot()
            ToolCallUiStatus.SUCCEEDED -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = ChatTheme.colors.success,
            )
            ToolCallUiStatus.FAILED -> Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = ChatTheme.colors.error,
            )
        }
        val text = when (item.status) {
            ToolCallUiStatus.RUNNING -> "正在调用 ${item.label}…"
            ToolCallUiStatus.SUCCEEDED -> "已调用 ${item.label}"
            ToolCallUiStatus.FAILED -> "调用失败 · ${item.label}"
        }
        val color = when (item.status) {
            ToolCallUiStatus.RUNNING -> ChatTheme.colors.textPrimary
            ToolCallUiStatus.SUCCEEDED -> ChatTheme.colors.textSecondary
            ToolCallUiStatus.FAILED -> ChatTheme.colors.error
        }
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (item.status == ToolCallUiStatus.RUNNING) FontWeight.Medium else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
private fun RunningDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "tool_running")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tool_dot_pulse",
    )
    Box(
        modifier = Modifier
            .size(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(ChatTheme.colors.brand, CircleShape),
        )
    }
}
