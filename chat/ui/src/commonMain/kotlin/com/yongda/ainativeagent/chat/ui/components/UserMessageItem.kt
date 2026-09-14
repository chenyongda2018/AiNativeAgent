package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.ChatMessageUi
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun UserMessageItem(msg: ChatMessageUi, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 5.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp,
            ),
            color = ChatTheme.colors.userBubble,
            border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
            modifier = Modifier.widthIn(max = 310.dp),
        ) {
            Text(
                text = msg.content,
                color = ChatTheme.colors.onUserBubble,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
