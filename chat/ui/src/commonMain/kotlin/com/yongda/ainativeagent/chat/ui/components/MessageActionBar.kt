package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val colors = IconButtonDefaults.iconButtonColors(
            contentColor = ChatTheme.colors.textSecondary,
        )

        IconButton(
            onClick = { clipboardManager.setText(AnnotatedString(content)) },
            modifier = Modifier.size(32.dp),
            colors = colors,
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
        }

        IconButton(
            onClick = { feedback = if (feedback == Feedback.LIKE) null else Feedback.LIKE },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (feedback == Feedback.LIKE) ChatTheme.colors.brand else ChatTheme.colors.textSecondary,
            ),
        ) {
            Icon(Icons.Default.ThumbUp, contentDescription = "点赞", modifier = Modifier.size(16.dp))
        }

        IconButton(
            onClick = { feedback = if (feedback == Feedback.DISLIKE) null else Feedback.DISLIKE },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (feedback == Feedback.DISLIKE) ChatTheme.colors.error else ChatTheme.colors.textSecondary,
            ),
        ) {
            Icon(Icons.Default.ThumbDown, contentDescription = "点踩", modifier = Modifier.size(16.dp))
        }

        IconButton(
            onClick = onRetry,
            modifier = Modifier.size(32.dp),
            colors = colors,
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "重新生成", modifier = Modifier.size(16.dp))
        }
    }
}

private enum class Feedback { LIKE, DISLIKE }
