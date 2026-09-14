package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun ChatEmptyState(
    modelName: String,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(ChatTactileTokens.radiusMedium),
            color = ChatTheme.colors.surface,
            border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = ChatTheme.colors.brand,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "今天想聊些什么？",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChatTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "由 $modelName 驱动",
            fontSize = 12.sp,
            color = ChatTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            promptSuggestions.forEach { suggestion ->
                PromptRow(
                    suggestion = suggestion,
                    onClick = { onSelectPrompt(suggestion.prompt) },
                )
            }
        }
    }
}

@Composable
private fun PromptRow(
    suggestion: PromptSuggestion,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ChatTactileTokens.radiusMedium),
        color = ChatTheme.colors.surface,
        border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(ChatTactileTokens.radiusSmall),
                color = ChatTheme.colors.surfaceRecessed,
                border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = suggestion.icon,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = ChatTheme.colors.brand,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatTheme.colors.textPrimary,
                )
                Text(
                    text = suggestion.description,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = ChatTheme.colors.textSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = ChatTheme.colors.textTertiary,
            )
        }
    }
}

private data class PromptSuggestion(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val prompt: String,
)

private val promptSuggestions = listOf(
    PromptSuggestion(
        icon = Icons.Default.Lightbulb,
        title = "梳理想法",
        description = "把零散信息整理成清晰步骤",
        prompt = "帮我把一个复杂想法梳理成清晰、可执行的步骤",
    ),
    PromptSuggestion(
        icon = Icons.Default.Edit,
        title = "改进表达",
        description = "优化结构、语气与可读性",
        prompt = "帮我改进一段文字的结构、语气和可读性",
    ),
    PromptSuggestion(
        icon = Icons.Default.Code,
        title = "解决问题",
        description = "分析问题并给出可靠方案",
        prompt = "请帮我分析一个问题，并给出可靠的解决方案和验证步骤",
    ),
)
