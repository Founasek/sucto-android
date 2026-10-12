package cz.sucto.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF55B560)
val Ink = Color(0xFF0E2A1C)
val InkSoft = Color(0xFF17402B)
val BrandGradient = listOf(Color(0xFF2F9E55), Color(0xFF1B6B3D))
private val Light = lightColorScheme(
    primary = Color(0xFF1B6B3D), secondary = Brand,
    background = Color(0xFFF2F2F7), surface = Color.White,
)
private val Dark = darkColorScheme(
    primary = Brand, secondary = Brand,
    background = Color(0xFF101512), surface = Color(0xFF1B211D),
)
@Composable
fun SuctoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}