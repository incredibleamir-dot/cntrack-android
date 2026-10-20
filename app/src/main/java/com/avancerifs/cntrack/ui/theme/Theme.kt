package com.avancerifs.cntrack.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    val bg = Color(0xFFFFF6E9)
    val surface = Color(0xFFFFFFFF)
    val surface2 = Color(0xFFFFF1DC)
    val line = Color(0xFFE5D5BE)
    val ink = Color(0xFF37304A)
    val inkMuted = Color(0xFF6E6486)

    val primary = Color(0xFF1D3350)
    val primaryPressed = Color(0xFF14263D)
    val onPrimary = Color(0xFFFFFFFF)

    val successFill = Color(0xFF3FA66A)
    val successInk = Color(0xFF1E6B3A)
    val successBg = Color(0xFFD9F2E2)

    val warningFill = Color(0xFFF5A623)
    val warningInk = Color(0xFF7A5200)
    val warningBg = Color(0xFFFCEFC9)

    val dangerFill = Color(0xFFF0557A)
    val dangerInk = Color(0xFFA91E42)
    val dangerBg = Color(0xFFFAD9E1)

    val infoFill = Color(0xFF4A9BE8)
    val infoInk = Color(0xFF1D5FA8)
    val infoBg = Color(0xFFD7EAFB)

    val neutralInk = Color(0xFF6E6486)
    val neutralBg = Color(0xFFE8E2F2)

    val sidebar = Color(0xFFD7E4F0)
    val sidebarInk = Color(0xFF1D3350)
    val topbar = Color(0xFFFF6B6B)
    val topbarInk = Color(0xFF4E1D1D)

    val accentShipments = Color(0xFF3D348B)
    val accentContainers = Color(0xFF0E7C7B)
    val accentAnalytics = Color(0xFF7C3AED)
    val accentQuick = Color(0xFFD63384)
    val accentDetails = Color(0xFFC96A05)
    val accentAbout = Color(0xFF1D3350)
}

private val Scheme = lightColorScheme(
    primary = AppColors.primary,
    onPrimary = AppColors.onPrimary,
    primaryContainer = AppColors.infoBg,
    onPrimaryContainer = AppColors.primary,
    secondary = AppColors.accentContainers,
    onSecondary = Color.White,
    tertiary = AppColors.accentAnalytics,
    background = AppColors.bg,
    onBackground = AppColors.ink,
    surface = AppColors.surface,
    onSurface = AppColors.ink,
    surfaceVariant = AppColors.surface2,
    onSurfaceVariant = AppColors.inkMuted,
    outline = AppColors.line,
    error = AppColors.dangerFill,
    onError = Color.White,
    errorContainer = AppColors.dangerBg,
    onErrorContainer = AppColors.dangerInk
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

private val AppTypography = Typography().let {
    it.copy(
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = it.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun CnTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
