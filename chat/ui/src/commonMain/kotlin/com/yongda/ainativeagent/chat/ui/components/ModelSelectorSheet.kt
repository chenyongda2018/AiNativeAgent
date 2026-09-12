package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSelectorSheet(
    selectedModelId: String,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ChatTheme.colors.surface,
        contentColor = ChatTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("选择 AI 模型", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ChatTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text("根据任务复杂度切换推理引擎", fontSize = 12.sp, color = ChatTheme.colors.textSecondary)
            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stubModels, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        isSelected = model.id == selectedModelId,
                        onClick = {
                            onSelectModel(model.id)
                            onDismiss()
                        },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.ShieldMoon, contentDescription = null, modifier = Modifier.size(14.dp), tint = ChatTheme.colors.success)
                        Spacer(Modifier.width(6.dp))
                        Text("全链路端到端加密", fontSize = 11.sp, color = ChatTheme.colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: StubModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) ChatTheme.colors.brandContainer else ChatTheme.colors.surfaceVariant,
        border = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ChatTheme.colors.textPrimary)
                    if (model.badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ChatTheme.colors.brand.copy(alpha = 0.12f),
                        ) {
                            Text(
                                model.badge,
                                fontSize = 10.sp,
                                color = ChatTheme.colors.brand,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(model.tagline, fontSize = 12.sp, color = ChatTheme.colors.textSecondary, lineHeight = 16.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("上下文: ${model.contextLimit}", fontSize = 11.sp, color = ChatTheme.colors.textSecondary, fontFamily = FontFamily.Monospace)
                    if (model.thinkingSupported) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(12.dp), tint = ChatTheme.colors.brand)
                            Spacer(Modifier.width(2.dp))
                            Text("深度思考", fontSize = 11.sp, color = ChatTheme.colors.brand)
                        }
                    }
                }
            }

            if (isSelected) {
                Surface(shape = CircleShape, color = ChatTheme.colors.brand, modifier = Modifier.size(22.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Check, contentDescription = "已选择", modifier = Modifier.size(14.dp), tint = ChatTheme.colors.onBrand)
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = ChatTheme.colors.surface,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ChatTheme.colors.border),
                    modifier = Modifier.size(22.dp),
                    content = {},
                )
            }
        }
    }
}

@Immutable
private data class StubModel(
    val id: String,
    val name: String,
    val badge: String?,
    val tagline: String,
    val contextLimit: String,
    val thinkingSupported: Boolean,
)

private val stubModels = listOf(
    StubModel("deepseek-r1", "DeepSeek R1", "推荐", "深度推理能力强，支持复杂思考链", "64K", true),
    StubModel("deepseek-v3", "DeepSeek V3", null, "快速响应，适合日常对话", "128K", false),
    StubModel("deepseek-coder", "DeepSeek Coder", "代码", "专注于代码生成与调试", "64K", false),
    StubModel("local-mnn", "MNN 本地模型", "端侧", "设备端推理，无需网络连接", "4K", false),
)
