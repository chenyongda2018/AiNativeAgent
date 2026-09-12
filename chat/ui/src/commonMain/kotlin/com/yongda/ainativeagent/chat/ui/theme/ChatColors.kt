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
    val userBubble: Color,
    val onUserBubble: Color,
    val onBrand: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val success: Color,
)

val LightChatColors = ChatColorScheme(
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    border = Color(0xFF000000).copy(alpha = 0.06f),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    brand = Color(0xFF4F46E5),
    brandContainer = Color(0xFFEEF2FF),
    userBubble = Color(0xFF0F172A),
    onUserBubble = Color.White,
    onBrand = Color.White,
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = Color(0xFF991B1B),
    success = Color(0xFF10B981),
)

val DarkChatColors = ChatColorScheme(
    background = Color(0xFF090D16),
    surface = Color(0xFF161B26),
    surfaceVariant = Color(0xFF1E2536),
    border = Color(0xFFFFFFFF).copy(alpha = 0.08f),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    brand = Color(0xFF6366F1),
    brandContainer = Color(0xFF312E81),
    userBubble = Color(0xFF27272A),
    onUserBubble = Color(0xFFF1F5F9),
    onBrand = Color.White,
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFCA5A5),
    success = Color(0xFF34D399),
)

internal val LocalChatColors = staticCompositionLocalOf { LightChatColors }
