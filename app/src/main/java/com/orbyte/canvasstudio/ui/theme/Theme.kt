package com.orbyte.canvasstudio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.orbyte.canvasstudio.model.StudioPalette

private val Scheme = darkColorScheme(
    primary = StudioPalette.Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF9DBBFF),
    background = StudioPalette.Background,
    surface = StudioPalette.Surface,
    surfaceVariant = StudioPalette.SurfaceRaised,
    outline = StudioPalette.Border,
    onBackground = StudioPalette.Text,
    onSurface = StudioPalette.Text,
    onSurfaceVariant = StudioPalette.TextMuted,
)

private val StudioTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun CanvasStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = StudioTypography, content = content)
}
