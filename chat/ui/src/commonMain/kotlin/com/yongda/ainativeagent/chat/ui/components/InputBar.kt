package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@Composable
internal fun InputBar(
    isStreaming: Boolean,
    modelName: String,
    onOpenModelSelector: () -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(ChatTactileTokens.radiusPanel),
            color = ChatTheme.colors.dockSurface,
            border = BorderStroke(1.dp, ChatTheme.colors.borderInteractive),
        ) {
            Column(modifier = Modifier.padding(start = 14.dp, top = 13.dp, end = 10.dp, bottom = 9.dp)) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ChatTactileTokens.minimumTouchTarget, max = 120.dp),
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = ChatTheme.colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(ChatTheme.colors.brand),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp)) {
                            if (text.isEmpty()) {
                                Text(
                                    text = if (isStreaming) "AI 正在回复中..." else "输入消息，探讨任何想法...",
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = ChatTheme.colors.textTertiary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Spacer(Modifier.height(7.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UtilityControl(
                            icon = Icons.Default.Add,
                            contentDescription = "添加附件",
                            enabled = false,
                        )
                        Spacer(Modifier.width(4.dp))
                        ModelSelectorPill(
                            modelName = modelName,
                            onClick = onOpenModelSelector,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UtilityControl(
                            icon = Icons.Default.Mic,
                            contentDescription = "语音输入",
                            enabled = false,
                        )
                        Spacer(Modifier.width(4.dp))
                        PrimaryComposerControl(
                            isStreaming = isStreaming,
                            enabled = isStreaming || text.isNotBlank(),
                            onClick = {
                                if (isStreaming) {
                                    onCancel()
                                } else {
                                    val message = text.trim()
                                    if (message.isNotEmpty()) {
                                        onSend(message)
                                        text = ""
                                        keyboardController?.hide()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UtilityControl(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(ChatTactileTokens.minimumTouchTarget)
            .alpha(if (enabled) 1f else 0.42f)
            .semantics {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(ChatTactileTokens.composerControl),
            shape = CircleShape,
            color = ChatTheme.colors.surfaceRecessed,
            border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ChatTheme.colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun PrimaryComposerControl(
    isStreaming: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        isStreaming -> ChatTheme.colors.error
        enabled -> ChatTheme.colors.userBubble
        else -> ChatTheme.colors.surfaceRecessed
    }
    val foreground = if (isStreaming || enabled) ChatTheme.colors.onUserBubble else ChatTheme.colors.textTertiary
    val label = if (isStreaming) "停止" else "发送"

    Box(
        modifier = Modifier
            .size(ChatTactileTokens.minimumTouchTarget)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(ChatTactileTokens.composerControl),
            shape = CircleShape,
            color = background,
            contentColor = foreground,
            border = BorderStroke(1.dp, if (enabled) ChatTheme.colors.borderInteractive else ChatTheme.colors.borderSubtle),
            shadowElevation = if (enabled) ChatTactileTokens.elevationRaised else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(if (isStreaming) 15.dp else 19.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelSelectorPill(
    modelName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = ChatTheme.colors.surfaceRecessed,
        border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
        modifier = modifier
            .height(ChatTactileTokens.composerControl)
            .semantics { contentDescription = "切换模型" },
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = modelName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ChatTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 118.dp),
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = ChatTheme.colors.textTertiary,
            )
        }
    }
}
