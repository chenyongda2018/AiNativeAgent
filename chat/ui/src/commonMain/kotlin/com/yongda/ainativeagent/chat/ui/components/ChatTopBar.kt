package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun ChatTopBar(
    onOpenSidebar: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarControl(
            icon = Icons.Default.Menu,
            contentDescription = "打开侧边栏",
            onClick = onOpenSidebar,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        ToolbarControl(
            icon = Icons.Default.Add,
            contentDescription = "新建对话",
            onClick = onNewChat,
        )
    }
}

@Composable
private fun ToolbarControl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(ChatTactileTokens.minimumTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(ChatTactileTokens.toolbarControl),
            shape = RoundedCornerShape(13.dp),
            color = ChatTheme.colors.surface,
            border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
            shadowElevation = ChatTactileTokens.elevationRaised,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = ChatTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
