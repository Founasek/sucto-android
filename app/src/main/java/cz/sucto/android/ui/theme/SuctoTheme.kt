package cz.sucto.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Brand = Color(0xFF55B560)
val Ink = Color(0xFF0E2A1C)
val InkSoft = Color(0xFF17402B)
val BrandGradient = listOf(Color(0xFF2F9E55), Color(0xFF1B6B3D))
val NegativeGradient = listOf(Color(0xFFC0392B), Color(0xFF8E2A20))
val Amber = Color(0xFFF59E0B)
val Info = Color(0xFF3B82F6)

private val Light = lightColorScheme(
    primary = Color(0xFF1B6B3D), onPrimary = Color.White, secondary = Brand,
    primaryContainer = Color(0xFFDDF1E2), onPrimaryContainer = Color(0xFF0E2A1C),
    secondaryContainer = Color(0xFFDDF1E2), onSecondaryContainer = Color(0xFF0E2A1C),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF111412),
    surface = Color.White, onSurface = Color(0xFF111412), onSurfaceVariant = Color(0xFF6B7280),
    surfaceVariant = Color(0xFFE9ECEA), outlineVariant = Color(0xFFD9DDDB), outline = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF2F2F7),
)
private val Dark = darkColorScheme(
    primary = Brand, onPrimary = Color(0xFF062012), secondary = Brand,
    primaryContainer = Color(0xFF17402B), onPrimaryContainer = Color(0xFFDDF1E2),
    secondaryContainer = Color(0xFF17402B), onSecondaryContainer = Color(0xFFDDF1E2),
    background = Color(0xFF0B100D), onBackground = Color(0xFFE8ECE9),
    surface = Color(0xFF1A201C), onSurface = Color(0xFFE8ECE9), onSurfaceVariant = Color(0xFFA3ABA6),
    surfaceVariant = Color(0xFF2A322D), outlineVariant = Color(0xFF39423C), outline = Color(0xFF6B756F),
    surfaceContainerLowest = Color(0xFF1A201C), surfaceContainerLow = Color(0xFF1A201C), surfaceContainer = Color(0xFF1A201C),
    surfaceContainerHigh = Color(0xFF222923), surfaceContainerHighest = Color(0xFF2A322D),
)

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

/** Výraznější nadpisy a číslice stejné šířky, aby se částky v seznamech zarovnávaly. */
private val Type = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Styl částek: tučnější číslice stejné šířky. */
val MoneyStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", letterSpacing = 0.sp)

@Composable
fun SuctoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shapes, content = content)
}
