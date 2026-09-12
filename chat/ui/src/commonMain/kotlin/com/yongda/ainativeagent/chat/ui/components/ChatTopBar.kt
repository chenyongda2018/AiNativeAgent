package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

/**
 * 顶栏：左侧侧边栏按钮 + 右侧新建对话按钮。
 * 模型选择 pill 已迁移到底部输入坞（InputBar），此处保持简洁、中间留白。
 */
@Composable
internal fun ChatTopBar(
    onOpenSidebar: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = ChatTheme.colors.border

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        color = ChatTheme.colors.surface,
    ) {
        Row(
            // Surface 背景绘制到状态栏之下，内容用 statusBarsPadding 推到状态栏下方。
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onOpenSidebar,
                colors = IconButtonDefaults.iconButtonColors(contentColor = ChatTheme.colors.textPrimary),
            ) {
                Icon(Icons.Default.Menu, contentDescription = "打开侧边栏", modifier = Modifier.size(22.dp))
            }

            IconButton(
                onClick = onNewChat,
                colors = IconButtonDefaults.iconButtonColors(contentColor = ChatTheme.colors.textPrimary),
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建对话", modifier = Modifier.size(22.dp))
            }
        }
    }
}
