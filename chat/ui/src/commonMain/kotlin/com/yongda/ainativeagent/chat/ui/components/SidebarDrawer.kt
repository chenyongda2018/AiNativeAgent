package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
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
internal fun SidebarDrawerContent(
    onNewChat: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier.fillMaxHeight().widthIn(max = 320.dp),
        drawerContainerColor = ChatTheme.colors.surface,
        drawerContentColor = ChatTheme.colors.textPrimary,
        drawerShape = RoundedCornerShape(topEnd = ChatTactileTokens.radiusPanel, bottomEnd = ChatTactileTokens.radiusPanel),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = ChatTheme.colors.brandContainer,
                border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("AI", color = ChatTheme.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("AI 聊天助手", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("专注、清晰、随时可用", fontSize = 11.sp, color = ChatTheme.colors.textTertiary)
            }
            Surface(
                onClick = onCloseDrawer,
                modifier = Modifier.size(ChatTactileTokens.minimumTouchTarget),
                shape = RoundedCornerShape(13.dp),
                color = ChatTheme.colors.surfaceRaised,
                border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭侧边栏",
                        modifier = Modifier.size(18.dp),
                        tint = ChatTheme.colors.textSecondary,
                    )
                }
            }
        }

        HorizontalDivider(color = ChatTheme.colors.borderSubtle)

        Surface(
            onClick = {
                onNewChat()
                onCloseDrawer()
            },
            shape = RoundedCornerShape(ChatTactileTokens.radiusMedium),
            color = ChatTheme.colors.userBubble,
            border = BorderStroke(1.dp, ChatTheme.colors.borderInteractive),
            shadowElevation = ChatTactileTokens.elevationRaised,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = ChatTheme.colors.onUserBubble,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "开启新对话",
                    color = ChatTheme.colors.onUserBubble,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(ChatTactileTokens.radiusMedium),
                color = ChatTheme.colors.surfaceRecessed,
                border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = ChatTheme.colors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("暂无历史对话", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("开始聊天后，对话记录会显示在这里", fontSize = 11.sp, color = ChatTheme.colors.textTertiary)
        }
    }
}
