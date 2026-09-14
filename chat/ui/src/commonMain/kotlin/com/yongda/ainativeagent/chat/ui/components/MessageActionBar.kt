package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Suppress("DEPRECATION")
@Composable
internal fun MessageActionBar(
    content: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var feedback by remember { mutableStateOf<Feedback?>(null) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MessageAction(
            icon = Icons.Default.ContentCopy,
            contentDescription = "复制",
            onClick = { clipboardManager.setText(AnnotatedString(content)) },
        )
        MessageAction(
            icon = Icons.Default.ThumbUp,
            contentDescription = "点赞",
            tint = if (feedback == Feedback.LIKE) ChatTheme.colors.brand else ChatTheme.colors.textTertiary,
            onClick = { feedback = if (feedback == Feedback.LIKE) null else Feedback.LIKE },
        )
        MessageAction(
            icon = Icons.Default.ThumbDown,
            contentDescription = "点踩",
            tint = if (feedback == Feedback.DISLIKE) ChatTheme.colors.error else ChatTheme.colors.textTertiary,
            onClick = { feedback = if (feedback == Feedback.DISLIKE) null else Feedback.DISLIKE },
        )
        MessageAction(
            icon = Icons.Default.Refresh,
            contentDescription = "重新生成",
            onClick = onRetry,
        )
    }
}

@Composable
private fun MessageAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = ChatTheme.colors.textTertiary,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        shape = RoundedCornerShape(9.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(15.dp),
                tint = tint,
            )
        }
    }
}

private enum class Feedback { LIKE, DISLIKE }
