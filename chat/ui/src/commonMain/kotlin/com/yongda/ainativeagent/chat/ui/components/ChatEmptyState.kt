package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun ChatEmptyState(
    modelName: String,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6))),
                    RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color.White,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "你好，今天想探索什么？",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ChatTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = modelName,
            fontSize = 13.sp,
            color = ChatTheme.colors.brand,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            promptSuggestions.forEach { suggestion ->
                PromptCard(
                    icon = suggestion.icon,
                    title = suggestion.title,
                    description = suggestion.description,
                    onClick = { onSelectPrompt(suggestion.prompt) },
                )
            }
        }
    }
}

@Composable
private fun PromptCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ChatTheme.colors.surface,
        border = null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ChatTheme.colors.brand,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ChatTheme.colors.textPrimary,
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = ChatTheme.colors.textSecondary,
                    lineHeight = 16.sp,
                )
            }
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
        icon = Icons.Default.Code,
        title = "代码助手",
        description = "帮我写一个 Kotlin 协程并发示例",
        prompt = "帮我写一个 Kotlin 协程并发的示例代码，展示 async/await 和 Channel 的用法",
    ),
    PromptSuggestion(
        icon = Icons.Default.Lightbulb,
        title = "创意灵感",
        description = "为我的 App 想一个独特的功能点",
        prompt = "为一个 AI 原生的 Android App 想几个独特的功能点，要有创意且技术可行",
    ),
    PromptSuggestion(
        icon = Icons.Default.Edit,
        title = "文案润色",
        description = "帮我优化一段产品介绍文案",
        prompt = "帮我优化一段产品介绍文案，使其更有吸引力和说服力",
    ),
    PromptSuggestion(
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        title = "技术趋势",
        description = "分析 2024 年移动端 AI 技术趋势",
        prompt = "分析当前移动端 AI 技术趋势，包括端侧推理、大模型小型化、多模态交互等方向",
    ),
)
