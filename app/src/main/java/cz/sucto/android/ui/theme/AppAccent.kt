package cz.sucto.android.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import cz.sucto.android.data.AppSettings

/**
 * Předvolby barvy aplikace. Z každé se odvozuje celá paleta (prvky, přechody, tmavé plochy), aby se vzhled neskládal
 * z náhodných odstínů. Výchozí je původní zelená.
 */
enum class AppAccent(
    val title: String,
    /** Barva prvků ve světlém a tmavém režimu (tmavý je světlejší kvůli kontrastu). */
    val primaryLight: Color, val primaryDark: Color,
    /** Světlý podklad (kontejner) ve světlém režimu. */
    val container: Color,
    /** Světlejší značková barva – dekorace, stíny, výnosy v grafech. */
    val brand: Color,
    /** Přechod hero ploch a tlačítek (bílý text musí být čitelný). */
    val gradient: List<Color>,
    /** Tmavé plochy (přihlášení, zámek) a jejich třetí, světlejší odstín přechodu. */
    val ink: Color, val inkSoft: Color, val meshHighlight: Color, val meshDeep: Color,
) {
    Green("Zelená", c(0x1B6B3D), c(0x55B560), c(0xDDF1E2), c(0x55B560), listOf(c(0x2F9E55), c(0x1B6B3D)),
        c(0x0E2A1C), c(0x17402B), c(0x246B3B), c(0x062012)),
    Blue("Modrá", c(0x1D4ED8), c(0x6EA8FF), c(0xDCE8FE), c(0x4C8DF6), listOf(c(0x3B82F6), c(0x1D4ED8)),
        c(0x0E1E3A), c(0x172B52), c(0x2456B8), c(0x0A1428)),
    Teal("Tyrkysová", c(0x0B6B66), c(0x4FD1C5), c(0xD5F3EF), c(0x14B8A6), listOf(c(0x14A39A), c(0x0B6B66)),
        c(0x0B2A2A), c(0x123F3E), c(0x0F7A74), c(0x071C1C)),
    Purple("Fialová", c(0x5B21B6), c(0xB79CFF), c(0xE9E0FD), c(0x8B5CF6), listOf(c(0x8B5CF6), c(0x5B21B6)),
        c(0x1D1235), c(0x2B1A4F), c(0x5B2FB8), c(0x120A24)),
    Rose("Růžová", c(0x9D174D), c(0xFF8FB8), c(0xFCE0EC), c(0xEC4899), listOf(c(0xDB2777), c(0x9D174D)),
        c(0x2E0F1E), c(0x471730), c(0xA3205C), c(0x1F0913)),
    Orange("Oranžová", c(0xB4500A), c(0xFFA05C), c(0xFDE6D0), c(0xF28C28), listOf(c(0xE8741A), c(0xB4500A)),
        c(0x2E1A0B), c(0x47270F), c(0xA9570F), c(0x1F1006)),
    Graphite("Grafitová", c(0x1F2937), c(0xD1D5DB), c(0xE5E7EB), c(0x6B7280), listOf(c(0x4B5563), c(0x1F2937)),
        c(0x111418), c(0x1F242B), c(0x3A424D), c(0x0A0C0F));

    /** Protiváha pro náklady a přijaté (výnosy a vydané mají barvu aplikace); oranžová se u oranžové předvolby nahradí modrou. */
    val contrast: Color get() = if (this == Orange) Color(0xFF3B82F6) else Color(0xFFF28C28)
}

private fun c(rgb: Int) = Color(0xFF000000.toInt() or rgb)

/** Zvolená barva aplikace. Čte se při vykreslení, takže se po změně v Nastavení překreslí všechno, co ji používá. */
object ThemeState {
    var accent by mutableStateOf(AppAccent.Green)
        private set

    fun load(context: Context) {
        accent = AppSettings.accent(context)
    }

    fun select(context: Context, value: AppAccent) {
        accent = value
        AppSettings.setAccent(context, value)
    }
}
