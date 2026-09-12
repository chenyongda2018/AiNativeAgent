package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun SidebarDrawerContent(
    onNewChat: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = ChatTheme.colors.surface,
        drawerContentColor = ChatTheme.colors.textPrimary,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ChatTheme.colors.brand,
                    modifier = Modifier.size(36.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text("AI", color = ChatTheme.colors.onBrand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("AI 聊天助手", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ChatTheme.colors.textPrimary)
                    Text("移动端 AI Agent", fontSize = 11.sp, color = ChatTheme.colors.textSecondary)
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                onClick = {
                    onNewChat()
                    onCloseDrawer()
                },
                shape = RoundedCornerShape(12.dp),
                color = ChatTheme.colors.userBubble,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ChatTheme.colors.onUserBubble, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("开启新对话", color = ChatTheme.colors.onUserBubble, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            item {
                Text(
                    "置顶对话",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    letterSpacing = 1.sp,
                )
            }
            items(pinnedSessions) { session ->
                SessionItem(session, isPinned = true, onClick = onCloseDrawer)
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text(
                    "历史记录",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    letterSpacing = 1.sp,
                )
            }
            items(recentSessions) { session ->
                SessionItem(session, isPinned = false, onClick = onCloseDrawer)
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: StubSession,
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Column {
                Text(session.title, fontSize = 13.sp, maxLines = 1)
                Text(session.date, fontSize = 10.sp, color = ChatTheme.colors.textSecondary)
            }
        },
        selected = false,
        onClick = onClick,
        icon = {
            Icon(
                if (isPinned) Icons.Default.PushPin else Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isPinned) ChatTheme.colors.brand else ChatTheme.colors.textSecondary,
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = ChatTheme.colors.surface,
            unselectedTextColor = ChatTheme.colors.textPrimary,
        ),
    )
}

@Immutable
private data class StubSession(val title: String, val date: String)

private val pinnedSessions = listOf(
    StubSession("Kotlin 协程最佳实践", "今天"),
    StubSession("Compose 动画指南", "昨天"),
)

private val recentSessions = listOf(
    StubSession("Android AI Agent 架构设计", "3 天前"),
    StubSession("DeepSeek API 集成调试", "5 天前"),
    StubSession("MNN 端侧推理方案调研", "1 周前"),
)
