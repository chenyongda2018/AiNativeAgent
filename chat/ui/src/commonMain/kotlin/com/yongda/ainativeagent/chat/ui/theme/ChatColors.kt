package com.yongda.ainativeagent.chat.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ChatColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val brand: Color,
    val brandContainer: Color,
    // 次级强调（琥珀）：深度思考标签、代码高亮关键字。核心品牌走 [brand]（terracotta）。
    val amber: Color,
    val amberContainer: Color,
    val userBubble: Color,
    val onUserBubble: Color,
    val onBrand: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val success: Color,
    // 输入坞（Claude 风格圆角卡片）：暖色卡片底 + 控件（[+]/模型 pill）底色
    val dockSurface: Color,
    val dockControl: Color,
    // 品牌渐变（AI 头像 / 空态星标）：暖色 amber→orange
    val brandGradientStart: Color,
    val brandGradientEnd: Color,
)

// 色板对齐移动端 AI 聊天 UI 设计规范（暖石 + Terracotta，拒绝科技蓝/霓虹）：
// 核心品牌 = terracotta；次级强调 = 琥珀（思考/代码）；文本走暖石；深色中性色走 zinc；
// 在线点 emerald；停止/错误 rose。
val LightChatColors = ChatColorScheme(
    background = Color(0xFFFFFFFF),      // Canvas 基础画板
    surface = Color(0xFFFFFFFF),         // white
    surfaceVariant = Color(0xFFF5F5F4),  // stone-100
    border = Color(0xFFE7E5E4),          // stone-200 细线
    textPrimary = Color(0xFF1C1917),     // stone-900
    textSecondary = Color(0xFF78716C),   // stone-500
    brand = Color(0xFFCC785C),           // terracotta（核心品牌）
    brandContainer = Color(0xFFF8ECE7),  // 柔和 terracotta 微染（选中态容器）
    amber = Color(0xFFD97706),           // amber-600（次级：思考标签/代码）
    amberContainer = Color(0xFFFFFBEB),  // amber-50
    userBubble = Color(0xFF0F172A),      // slate-900
    onUserBubble = Color.White,
    onBrand = Color.White,
    error = Color(0xFFE11D48),           // rose-600
    errorContainer = Color(0xFFFFF1F2),  // rose-50
    onErrorContainer = Color(0xFF9F1239),// rose-800
    success = Color(0xFF10B981),         // emerald-500
    dockSurface = Color(0xFFF5F4EF),
    dockControl = Color(0xFFE7E5E4),     // stone-200
    brandGradientStart = Color(0xFFCC785C), // terracotta
    brandGradientEnd = Color(0xFFD97706),   // amber-600（暖石星芒渐变）
)

val DarkChatColors = ChatColorScheme(
    background = Color(0xFF18181B),      // zinc-900（OLED 纯净底）
    surface = Color(0xFF27272A),         // zinc-800（升起卡片）
    surfaceVariant = Color(0xFF3F3F46),  // zinc-700
    border = Color(0xFFFFFFFF).copy(alpha = 0.08f),
    textPrimary = Color(0xFFF4F4F5),     // zinc-100
    textSecondary = Color(0xFFA1A1AA),   // zinc-400
    brand = Color(0xFFE08A6F),           // terracotta 暗调（提亮饱和度）
    brandContainer = Color(0xFF3B2A24),  // 深 terracotta 微染
    amber = Color(0xFFFBBF24),           // amber-400
    amberContainer = Color(0xFF451A03),  // amber-950
    userBubble = Color(0xFF27272A),      // zinc-800
    onUserBubble = Color(0xFFF4F4F5),
    onBrand = Color.White,
    error = Color(0xFFFB7185),           // rose-400
    errorContainer = Color(0xFF4C0519),  // rose-950
    onErrorContainer = Color(0xFFFDA4AF),// rose-300
    success = Color(0xFF34D399),         // emerald-400
    dockSurface = Color(0xFF27272A),     // zinc-800
    dockControl = Color(0xFF3F3F46),     // zinc-700
    brandGradientStart = Color(0xFFE08A6F), // terracotta 暗调
    brandGradientEnd = Color(0xFFF59E0B),   // amber-500
)

internal val LocalChatColors = staticCompositionLocalOf { LightChatColors }
