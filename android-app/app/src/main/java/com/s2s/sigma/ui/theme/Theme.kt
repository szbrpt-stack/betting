package com.s2s.sigma.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DeepNavy = Color(0xFF041321)
val Navy = Color(0xFF071D31)
val DarkPanel = Color(0xFF0B2944)
val ElevatedPanel = Color(0xFF103653)
val Cyan = Color(0xFF32D5F2)
val CyanSoft = Color(0xFF8BE8F6)
val Aqua = Color(0xFF49DDB7)
val PrimaryText = Color(0xFFF4FAFF)
val SecondaryText = Color(0xFFA9C1D2)
val MutedText = Color(0xFF7895AA)
val Border = Color(0xFF214A66)
val Success = Color(0xFF49DDB7)
val Warning = Color(0xFFFFC968)
val ErrorRed = Color(0xFFFF7185)
val Purple = Color(0xFF9B8CF2)

private val SigmaColors = darkColorScheme(
    primary = Cyan,
    onPrimary = DeepNavy,
    primaryContainer = Color(0xFF06465B),
    onPrimaryContainer = Color(0xFFC4F6FF),
    secondary = Aqua,
    onSecondary = DeepNavy,
    secondaryContainer = Color(0xFF124B43),
    onSecondaryContainer = Color(0xFFB8F5E7),
    tertiary = Purple,
    background = DeepNavy,
    onBackground = PrimaryText,
    surface = Navy,
    onSurface = PrimaryText,
    surfaceVariant = DarkPanel,
    onSurfaceVariant = SecondaryText,
    outline = Border,
    outlineVariant = Border.copy(alpha = .55f),
    error = ErrorRed,
)

private val SigmaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = .25.sp,
    ),
)

private val SigmaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun S2SSigmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SigmaColors,
        typography = SigmaTypography,
        shapes = SigmaShapes,
        content = content,
    )
}
