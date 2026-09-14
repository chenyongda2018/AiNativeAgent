package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.ThinkingContent
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun ThinkingSection(
    thinking: ThinkingContent,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
) {
    var expanded by remember(defaultExpanded) { mutableStateOf(defaultExpanded) }

    Surface(
        shape = RoundedCornerShape(ChatTactileTokens.radiusMedium),
        color = ChatTheme.colors.surfaceRecessed,
        border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = ChatTheme.colors.amber,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "思考过程 · ${thinking.durationSeconds}s",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ChatTheme.colors.amber,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(16.dp),
                    tint = ChatTheme.colors.textTertiary,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(color = ChatTheme.colors.borderSubtle)
                    Text(
                        text = thinking.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        color = ChatTheme.colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
