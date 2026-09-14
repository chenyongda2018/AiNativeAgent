package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun ErrorBar(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = ChatTheme.colors.errorContainer,
        shape = RoundedCornerShape(ChatTactileTokens.radiusMedium),
        border = BorderStroke(1.dp, ChatTheme.colors.error.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 7.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ChatTheme.colors.error,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = error,
                color = ChatTheme.colors.onErrorContainer,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onRetry,
                shape = RoundedCornerShape(9.dp),
                color = ChatTheme.colors.surfaceRaised,
                border = BorderStroke(1.dp, ChatTheme.colors.borderInteractive),
                shadowElevation = ChatTactileTokens.elevationRaised,
            ) {
                Text(
                    text = "重试",
                    color = ChatTheme.colors.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
        }
    }
}
