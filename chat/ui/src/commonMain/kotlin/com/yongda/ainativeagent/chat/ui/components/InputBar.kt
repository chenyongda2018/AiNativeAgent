package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

/**
 * 底部输入坞：1:1 复刻 React 参考（ChatInputDock.tsx）的 Claude 风格圆角卡片。
 *
 * 结构：
 * - 外层暖色圆角卡片（圆角 24dp、细边框、[dockSurface] 底色）。
 * - 自增长多行输入（最小 44dp，增长至 120dp 后内部滚动）；占位符随模型名/流式态变化。
 * - 底部操作栏：左 = [+] 附件按钮 + 模型选择 pill；右 = 麦克风（装饰 no-op）+ 发送/停止按钮。
 *
 * 模型选择 pill 从顶栏迁移到此处（见参考设计），点击回调 [onOpenModelSelector] 复用外层的
 * ModelSelectorSheet 逻辑。附件与语音后端不在本阶段范围，相关交互均为占位无操作。
 */
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
    var showAttachMenu by remember { mutableStateOf(false) }
    // [+] 打开附件菜单时旋转 45° 变成 X（对齐参考交互）
    val plusRotation by animateFloatAsState(if (showAttachMenu) 45f else 0f, label = "plusRotation")

    Surface(
        // navigationBarsPadding() 先消费导航栏边距，imePadding() 再叠加键盘高度，
        // 二者链式书写避免键盘弹出时重复计入导航栏（否则会出现空隙）。
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = ChatTheme.colors.background,
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp)) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ChatTheme.colors.dockSurface,
                border = BorderStroke(1.dp, ChatTheme.colors.border),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 文本输入区：最小 44dp，增长至 120dp 后滚动
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp, max = 120.dp),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = ChatTheme.colors.textPrimary,
                        ),
                        cursorBrush = SolidColor(ChatTheme.colors.brand),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = if (isStreaming) "AI 正在回复中..." else "Chat with $modelName...",
                                        fontSize = 15.sp,
                                        color = ChatTheme.colors.textSecondary,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    Spacer(Modifier.height(4.dp))

                    // 底部操作栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 左：[+] 附件 + 模型 pill
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(ChatTheme.colors.dockControl)
                                        .clickable { showAttachMenu = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "添加附件",
                                        tint = ChatTheme.colors.textSecondary,
                                        modifier = Modifier.size(18.dp).rotate(plusRotation),
                                    )
                                }
                                // 附件功能暂未接后端，菜单项均为占位无操作
                                DropdownMenu(
                                    expanded = showAttachMenu,
                                    onDismissRequest = { showAttachMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("上传文档") },
                                        onClick = { showAttachMenu = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("照片与图片") },
                                        onClick = { showAttachMenu = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("拍照上传") },
                                        onClick = { showAttachMenu = false },
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            ModelSelectorPill(
                                modelName = modelName,
                                onClick = onOpenModelSelector,
                            )
                        }

                        // 右：麦克风（装饰 no-op）+ 发送/停止
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = ChatTheme.colors.textSecondary,
                                ),
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "语音输入", modifier = Modifier.size(20.dp))
                            }

                            Spacer(Modifier.width(4.dp))

                            if (isStreaming) {
                                IconButton(
                                    onClick = onCancel,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(ChatTheme.colors.error, CircleShape)
                                        .semantics { contentDescription = "停止" },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = ChatTheme.colors.onBrand),
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                        .size(36.dp)
                                        .background(
                                            if (hasText) ChatTheme.colors.userBubble else ChatTheme.colors.dockControl,
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
}

/**
 * Claude 风格模型选择 pill（`{模型名} ▾`）：迁移自顶栏，现位于输入坞底部操作栏左侧。
 */
@Composable
private fun ModelSelectorPill(
    modelName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = ChatTheme.colors.dockControl,
        modifier = modifier.semantics { contentDescription = "切换模型" },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = modelName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ChatTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 130.dp),
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ChatTheme.colors.textSecondary,
            )
        }
    }
}
