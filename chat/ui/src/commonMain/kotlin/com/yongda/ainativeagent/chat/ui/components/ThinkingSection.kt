package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.ThinkingContent
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun ThinkingSection(
    thinking: ThinkingContent,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
) {
    // 以 defaultExpanded 为初值：思考进行中自动展开，回答开始时（值翻转）自动收起，其间仍可手动切换。
    var expanded by remember(defaultExpanded) { mutableStateOf(defaultExpanded) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ChatTheme.colors.amberContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = ChatTheme.colors.amber,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "思考过程 (${thinking.durationSeconds}s)",
                    fontSize = 12.sp,
                    color = ChatTheme.colors.amber,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(16.dp),
                    tint = ChatTheme.colors.amber,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Text(
                    text = thinking.text,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                    color = ChatTheme.colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }
        }
    }
}
