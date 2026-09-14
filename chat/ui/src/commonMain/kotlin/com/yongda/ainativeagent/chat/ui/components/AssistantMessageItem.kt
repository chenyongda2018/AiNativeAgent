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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.ChatMessageUi
import com.yongda.ainativeagent.chat.ui.MarkdownRenderCache
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun AssistantMessageItem(
    msg: ChatMessageUi,
    markdownCache: MarkdownRenderCache,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Surface(
            modifier = Modifier.size(26.dp),
            shape = RoundedCornerShape(ChatTactileTokens.radiusSmall),
            color = ChatTheme.colors.surface,
            border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = ChatTheme.colors.brand,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = msg.modelName ?: "AI",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ChatTheme.colors.textPrimary,
            )

            msg.thinking?.let { thinking ->
                ThinkingSection(
                    thinking = thinking,
                    defaultExpanded = msg.streaming && msg.content.isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                msg.content.isEmpty() && msg.streaming -> Text(
                    text = "▍",
                    fontSize = 14.5.sp,
                    color = ChatTheme.colors.brand,
                )
                msg.streaming -> StreamingText(content = msg.content)
                else -> CachedMarkdown(msg = msg, cache = markdownCache)
            }

            if (msg.streaming && msg.content.isNotEmpty()) StreamingIndicator()
            if (!msg.streaming) {
                MessageActionBar(
                    content = msg.content,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StreamingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot_pulse",
    )

    Row(
        modifier = modifier.padding(top = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(dotAlpha)
                .background(ChatTheme.colors.brand, CircleShape),
        )
        Text(
            text = "正在生成",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = ChatTheme.colors.brand,
        )
    }
}
