package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun InputBar(
    isStreaming: Boolean,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ChatTheme.colors.background,
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ChatTheme.colors.surfaceVariant,
                border = null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(
                        onClick = { },
                        modifier = Modifier.size(40.dp).padding(bottom = 2.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = ChatTheme.colors.textSecondary,
                        ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "附件", modifier = Modifier.size(20.dp))
                    }

                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp, max = 120.dp)
                            .padding(vertical = 10.dp),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = ChatTheme.colors.textPrimary,
                        ),
                        cursorBrush = SolidColor(ChatTheme.colors.brand),
                        maxLines = 5,
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        text = if (isStreaming) "AI 正在回复中..." else "询问任何问题...",
                                        fontSize = 15.sp,
                                        color = ChatTheme.colors.textSecondary,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    Box(modifier = Modifier.padding(bottom = 4.dp, end = 2.dp)) {
                        if (isStreaming) {
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(ChatTheme.colors.error, CircleShape)
                                    .semantics { contentDescription = "停止" },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = ChatTheme.colors.onBrand),
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            val hasText = text.isNotBlank()
                            IconButton(
                                onClick = {
                                    val t = text.trim()
                                    if (t.isNotEmpty()) {
                                        onSend(t)
                                        text = ""
                                    }
                                },
                                enabled = hasText,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        if (hasText) ChatTheme.colors.userBubble else ChatTheme.colors.border,
                                        CircleShape,
                                    )
                                    .semantics { contentDescription = "发送" },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (hasText) ChatTheme.colors.onUserBubble else ChatTheme.colors.textSecondary,
                                    disabledContentColor = ChatTheme.colors.textSecondary,
                                ),
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
