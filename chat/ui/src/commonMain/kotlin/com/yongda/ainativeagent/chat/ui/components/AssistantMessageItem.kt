package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.ChatMessageUi
import com.yongda.ainativeagent.chat.ui.MarkdownRenderCache
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(ChatTheme.colors.brandGradientStart, ChatTheme.colors.brandGradientEnd),
                    ),
                    shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White,
            )
        }

        // Content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Model name label
            Text(
                text = msg.modelName ?: "AI",
                fontSize = 12.sp,
                color = ChatTheme.colors.textSecondary,
            )

            // Thinking section
            if (msg.thinking != null) {
                ThinkingSection(
                    thinking = msg.thinking,
                    // 思考进行中（仍在流式且正式回答尚未开始）默认展开，回答一开始自动收起。
                    defaultExpanded = msg.streaming && msg.content.isEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }

            // Message body
            when {
                msg.content.isEmpty() && msg.streaming -> {
                    Text(
                        text = "▍",
                        fontSize = 14.5.sp,
                        color = ChatTheme.colors.textPrimary,
                    )
                }
                msg.streaming -> {
                    StreamingText(content = msg.content)
                }
                else -> {
                    CachedMarkdown(msg = msg, cache = markdownCache)
                }
            }

            // Streaming indicator
            if (msg.streaming && msg.content.isNotEmpty()) {
                StreamingIndicator()
            }

            // Action bar
            if (!msg.streaming) {
                MessageActionBar(content = msg.content, onRetry = onRetry)
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
        modifier = modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(dotAlpha)
                .background(ChatTheme.colors.brand, CircleShape),
        )
        Text(
            text = "AI 正在生成…",
            fontSize = 12.sp,
            color = ChatTheme.colors.brand,
        )
    }
}
