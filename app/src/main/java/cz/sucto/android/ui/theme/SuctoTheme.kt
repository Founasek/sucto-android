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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Barvy podle zvolené předvolby (`AppAccent`); čtou se při vykreslení, tak se po změně v Nastavení samy přebarví. */
val Brand: Color get() = ThemeState.accent.brand
val Ink: Color get() = ThemeState.accent.ink
val InkSoft: Color get() = ThemeState.accent.inkSoft
/** Třetí, světlejší odstín přechodu tmavých ploch (přihlášení, zámek). */
val InkGlow: Color get() = ThemeState.accent.meshHighlight
val BrandGradient: List<Color> get() = ThemeState.accent.gradient
/** Barva tlačítek na tmavém pozadí (začátek značkového přechodu). */
val BrandButton: Color get() = ThemeState.accent.gradient.first()
/** Náklady a přijaté: protiváha k barvě aplikace, kterou mají výnosy a vydané. */
val Cost: Color get() = ThemeState.accent.contrast
val NegativeGradient = listOf(Color(0xFFC0392B), Color(0xFF8E2A20))
/** Varování ve stavech faktur; na barvě aplikace nezávisí. */
val Amber = Color(0xFFF59E0B)
val Info = Color(0xFF3B82F6)

private fun lightScheme(a: AppAccent) = lightColorScheme(
    primary = a.primaryLight, onPrimary = Color.White, secondary = a.brand,
    primaryContainer = a.container, onPrimaryContainer = a.ink,
    secondaryContainer = a.container, onSecondaryContainer = a.ink,
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF111412),
    surface = Color.White, onSurface = Color(0xFF111412), onSurfaceVariant = Color(0xFF6B7280),
    surfaceVariant = Color(0xFFE9ECEA), outlineVariant = Color(0xFFD9DDDB), outline = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF2F2F7),
)

/** Tmavé plochy jsou neutrální s lehkým nádechem barvy aplikace. */
private fun tinted(neutral: Long, a: AppAccent, amount: Float) = lerp(Color(0xFF000000 or neutral), a.ink, amount)

private fun darkScheme(a: AppAccent) = darkColorScheme(
    primary = a.primaryDark, onPrimary = a.meshDeep, secondary = a.brand,
    primaryContainer = a.inkSoft, onPrimaryContainer = a.container,
    secondaryContainer = a.inkSoft, onSecondaryContainer = a.container,
    background = tinted(0x0C0D0F, a, .15f), onBackground = Color(0xFFE8ECE9),
    surface = tinted(0x1B1D20, a, .12f), onSurface = Color(0xFFE8ECE9), onSurfaceVariant = Color(0xFFA3ABA6),
    surfaceVariant = tinted(0x2B2E31, a, .12f), outlineVariant = tinted(0x3A3D40, a, .1f), outline = Color(0xFF6B756F),
    surfaceContainerLowest = tinted(0x1B1D20, a, .12f), surfaceContainerLow = tinted(0x1B1D20, a, .12f), surfaceContainer = tinted(0x1B1D20, a, .12f),
    surfaceContainerHigh = tinted(0x232629, a, .12f), surfaceContainerHighest = tinted(0x2B2E31, a, .12f),
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
    val accent = ThemeState.accent
    MaterialTheme(colorScheme = if (dark) darkScheme(accent) else lightScheme(accent), typography = Type, shapes = Shapes, content = content)
}
