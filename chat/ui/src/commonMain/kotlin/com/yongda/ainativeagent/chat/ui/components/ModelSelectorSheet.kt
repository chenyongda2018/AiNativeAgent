package com.yongda.ainativeagent.chat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yongda.ainativeagent.chat.ui.ChatModel
import com.yongda.ainativeagent.chat.ui.ChatModels
import com.yongda.ainativeagent.chat.ui.theme.ChatTactileTokens
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("选择 AI 模型", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text("切换仅影响后续消息", fontSize = 12.sp, color = ChatTheme.colors.textSecondary)
                }
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.size(ChatTactileTokens.minimumTouchTarget),
                    shape = CircleShape,
                    color = ChatTheme.colors.surfaceRaised,
                    border = BorderStroke(1.dp, ChatTheme.colors.borderSubtle),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(17.dp),
                            tint = ChatTheme.colors.textSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = ChatTheme.colors.borderSubtle)
            Spacer(Modifier.height(14.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(ChatModels.all, key = { it.id }) { model ->
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
                    Text(
                        text = "选择适合当前任务的模型",
                        fontSize = 11.sp,
                        color = ChatTheme.colors.textTertiary,
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ChatModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ChatTactileTokens.radiusLarge),
        color = if (isSelected) ChatTheme.colors.brandContainer else ChatTheme.colors.surfaceRaised,
        border = BorderStroke(
            1.dp,
            if (isSelected) ChatTheme.colors.brand else ChatTheme.colors.borderSubtle,
        ),
        shadowElevation = if (isSelected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    model.badge?.let { badge ->
                        Spacer(Modifier.width(7.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = ChatTheme.colors.brand.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, ChatTheme.colors.brand.copy(alpha = 0.2f)),
                        ) {
                            Text(
                                text = badge,
                                fontSize = 10.sp,
                                color = ChatTheme.colors.brand,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = model.tagline,
                    fontSize = 12.sp,
                    color = ChatTheme.colors.textSecondary,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = "上下文 ${model.contextLimit}",
                        fontSize = 11.sp,
                        color = ChatTheme.colors.textTertiary,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (model.thinkingSupported) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = ChatTheme.colors.amber,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text("深度思考", fontSize = 11.sp, color = ChatTheme.colors.amber)
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            Surface(
                shape = CircleShape,
                color = if (isSelected) ChatTheme.colors.brand else ChatTheme.colors.surfaceRecessed,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) ChatTheme.colors.brand else ChatTheme.colors.borderInteractive,
                ),
                modifier = Modifier.size(22.dp),
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选择",
                            modifier = Modifier.size(14.dp),
                            tint = ChatTheme.colors.onBrand,
                        )
                    }
                }
            }
        }
    }
}
