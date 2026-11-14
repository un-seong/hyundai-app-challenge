package com.chaon.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 거의 검정에 가까운 남색 배경 + 앰버 포인트. 카드 구분은 얇은 테두리로. */
object ChaonColors {
    val Bg = Color(0xFF0A0F14)
    val Card = Color(0xFF111821)
    val CardBorder = Color(0xFF1F2A36)
    val Divider = Color(0xFF1C2631)
    val Text = Color(0xFFF2F4F7)
    val Muted = Color(0xFF8B98A7)
    val Amber = Color(0xFFF59E0B)
    val OnAmber = Color(0xFF1A1206)
    val Green = Color(0xFF34D399)
    val GreenBg = Color(0xFF0E241D)
    val GreenBorder = Color(0xFF1C4D3A)
    val Red = Color(0xFFF87171)
    val RedBg = Color(0xFF2A1416)
    val RedBorder = Color(0xFF5C2629)
    val Yellow = Color(0xFFFBBF24)
    val YellowBg = Color(0xFF2A2210)
    val YellowBorder = Color(0xFF5A4614)
    val Cyan = Color(0xFF38BDF8)
    val CyanBg = Color(0xFF0E2433)
}

private val colors = darkColorScheme(
    primary = ChaonColors.Amber,
    onPrimary = ChaonColors.OnAmber,
    secondary = ChaonColors.Cyan,
    background = ChaonColors.Bg,
    onBackground = ChaonColors.Text,
    surface = ChaonColors.Card,
    onSurface = ChaonColors.Text,
    surfaceVariant = ChaonColors.Card,
    onSurfaceVariant = ChaonColors.Muted,
    outline = ChaonColors.CardBorder,
    error = ChaonColors.Red,
)

// 차량 화면(약 1600×900dp) 기준. 디자인 시안처럼 굵은 제목 + 차분한 본문.
private val type = Typography(
    displayLarge = TextStyle(fontSize = 112.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 19.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ChaonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
