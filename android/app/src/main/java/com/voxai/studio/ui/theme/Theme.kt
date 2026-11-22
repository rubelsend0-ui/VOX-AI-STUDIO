package com.voxai.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AmoledBlack = Color(0xFF000000)
val GlassSurface = Color(0xFF0F111A)
val GlassBorder = Color(0x337C4DFF)
val ElectricPurple = Color(0xFF8B5CF6)
val DeepViolet = Color(0xFF5B21B6)
val NeonBlue = Color(0xFF38BDF8)
val CyanGlow = Color(0xFF06B6D4)
val EmeraldStatus = Color(0xFF10B981)
val RoseError = Color(0xFFF43F5E)

private val DarkColors = darkColorScheme(
    primary = ElectricPurple,
    secondary = NeonBlue,
    tertiary = CyanGlow,
    background = AmoledBlack,
    surface = GlassSurface,
    onPrimary = Color.White,
    onSecondary = AmoledBlack,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFE2E8F0)
)

@Composable
fun VoxStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
