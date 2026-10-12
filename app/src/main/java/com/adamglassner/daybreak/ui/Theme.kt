package com.adamglassner.daybreak.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamglassner.daybreak.R
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.ThemeMode

data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val link: Color,
    val warm: Color,
    val onWarm: Color,
    val night: Color,
    val nightSoft: Color,
    val cards: List<Color>,
    val onCard: Color,
    // Clock-radio parts (LED windows stay dark in every mode).
    val panel: Color = Color(0xFF0A0606),
    val panelBorder: Color = Color(0xFF3A3430),
    val led: Color = Color(0xFFFF2D4F),
    val ledOff: Color = Color(0xFF2B0B12),
    val panelText: Color = Color(0xFFE9DCC4),
    val panelMuted: Color = Color(0xFF8E8378),
    val stripe: Color = Color(0xFFC2562B),
)

data class Fonts(
    val display: FontFamily,
    val body: FontFamily,
    val bodyBold: FontWeight,
    val caps: Boolean,
)

data class Db(val style: ClockStyle, val p: Palette, val f: Fonts, val radius: Dp)

val LocalDb = staticCompositionLocalOf { buildTheme(ClockStyle.STOCK, true) }

private val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)
private val Fredoka = FontFamily(Font(R.font.fredoka_semibold, FontWeight.SemiBold))
private val Bebas = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal))
private val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)
private val Archivo = FontFamily(
    Font(R.font.archivo_narrow_bold, FontWeight.Bold),
    Font(R.font.archivo_narrow_bold_italic, FontWeight.Bold, FontStyle.Italic),
)
private val Vt323 = FontFamily(Font(R.font.vt323, FontWeight.Normal))
private val JetMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
val TerminalFont = Vt323
val MonoFont = JetMono
val RadioFont = Archivo
val BebasFont = Bebas
val FredokaFont = Fredoka
val FigtreeFont = Figtree

fun c(hex: Long) = Color(hex)

fun buildTheme(style: ClockStyle, dark: Boolean): Db {
    val p = when (style) {
        ClockStyle.STOCK -> if (dark) Palette(
            true, c(0xFF111318), c(0xFF1C1F26), c(0xFF262A33), c(0xFF2C313B), c(0xFFECEEF2), c(0xFFA3A9B5),
            c(0xFF8AB4F8), c(0xFF0B1F44), c(0xFF2A3550), c(0xFF8AB4F8), c(0xFFF6C453), c(0xFF2A1E00),
            c(0xFF22213A), c(0xFF35325C), listOf(c(0xFF1C1F26)), c(0xFFECEEF2),
        ) else Palette(
            false, c(0xFFF4F5F8), c(0xFFFFFFFF), c(0xFFEBEDF1), c(0xFFE1E4EA), c(0xFF15171A), c(0xFF5B616B),
            c(0xFF1A5FD0), c(0xFFFFFFFF), c(0xFFDCE7FB), c(0xFF1A5FD0), c(0xFF8A5A00), c(0xFFFFFFFF),
            c(0xFFE9E6FF), c(0xFFD6D0FF), listOf(c(0xFFFFFFFF)), c(0xFF15171A),
        )
        ClockStyle.COLORS -> if (dark) Palette(
            true, c(0xFF1A0B3A), c(0xFF24124D), c(0xFF2F1A60), c(0xFF3A2470), c(0xFFFFFFFF), c(0xFFCFC3F5),
            c(0xFFFF7A59), c(0xFF1A0B3A), c(0xFF4A2350), c(0xFFFFD23F), c(0xFFFFD23F), c(0xFF1A0B3A),
            c(0xFF2F1A60), c(0xFF4B2F8A), listOf(c(0xFF2DD4BF), c(0xFFB79CFF), c(0xFFFFD23F), c(0xFFFF7A59)), c(0xFF1A0B3A),
        ) else Palette(
            false, c(0xFFFFF6EC), c(0xFFFFFFFF), c(0xFFFFE9D6), c(0xFFF2D9C4), c(0xFF1A0B3A), c(0xFF5E4F7A),
            c(0xFFFF7A59), c(0xFF1A0B3A), c(0xFFFFE0D6), c(0xFFB83A1C), c(0xFFFFD23F), c(0xFF1A0B3A),
            c(0xFFEDE6FF), c(0xFFD9CCFF), listOf(c(0xFF2DD4BF), c(0xFFB79CFF), c(0xFFFFD23F), c(0xFFFF7A59)), c(0xFF1A0B3A),
        )
        ClockStyle.RETRO -> if (dark) Palette(
            true, c(0xFF1E1914), c(0xFF2B231B), c(0xFF3A2D22), c(0xFF4A3D2E), c(0xFFF2E2C4), c(0xFFB8A687),
            c(0xFFF2B544), c(0xFF2B231B), c(0xFF3D3020), c(0xFFF2B544), c(0xFFF2B544), c(0xFF2B231B),
            c(0xFF2B231B), c(0xFF4A3D2E), listOf(c(0xFF2B231B)), c(0xFFF2E2C4),
        ) else Palette(
            false, c(0xFFF2E2C4), c(0xFFE8D3AA), c(0xFFDCC395), c(0xFFC9AE7E), c(0xFF2B231B), c(0xFF6B5A43),
            c(0xFFB3402A), c(0xFFF2E2C4), c(0xFFE4C9A0), c(0xFF9A3522), c(0xFFB3402A), c(0xFFF2E2C4),
            c(0xFFE8D3AA), c(0xFFC9AE7E), listOf(c(0xFFE8D3AA)), c(0xFF2B231B),
        )
        ClockStyle.RADIO -> if (dark) Palette(
            true, c(0xFF1F1A17), c(0xFF0A0606), c(0xFF2C2724), c(0xFF3A3430), c(0xFFE9DCC4), c(0xFF9A8F84),
            c(0xFFFF2D4F), c(0xFF0A0606), c(0xFF3A1A20), c(0xFFFF5A73), c(0xFFFF2D4F), c(0xFF0A0606),
            c(0xFF0A0606), c(0xFF3A3430), listOf(c(0xFF0A0606)), c(0xFFE9DCC4),
        ) else Palette(
            false, c(0xFFD8CDBE), c(0xFFEFE7DC), c(0xFFC9BCAB), c(0xFFA99A86), c(0xFF2A2420), c(0xFF5E5246),
            c(0xFFC41E3A), c(0xFFFFFFFF), c(0xFFEBC9CC), c(0xFFA3172F), c(0xFFC41E3A), c(0xFFFFFFFF),
            c(0xFFEFE7DC), c(0xFFC9BCAB), listOf(c(0xFFEFE7DC)), c(0xFF2A2420),
        )
        // Green phosphor on black. "Light" is the inverted green-screen look.
        ClockStyle.TERMINAL -> if (dark) Palette(
            true, c(0xFF000000), c(0xFF030D05), c(0xFF0A1C0D), c(0xFF1E5A2A), c(0xFF33FF66), c(0xFF22B84C),
            c(0xFF33FF66), c(0xFF000000), c(0xFF0B2E14), c(0xFF7CFF9E), c(0xFFB6FF3D), c(0xFF000000),
            c(0xFF030D05), c(0xFF0E2A14), listOf(c(0xFF030D05)), c(0xFF33FF66),
        ) else Palette(
            false, c(0xFFE6F5E6), c(0xFFF4FBF4), c(0xFFD3EDD3), c(0xFF8DC79A), c(0xFF0A3A12), c(0xFF2E6B3A),
            c(0xFF0A7A1F), c(0xFFFFFFFF), c(0xFFCDEBD2), c(0xFF06651A), c(0xFF4C7A00), c(0xFFFFFFFF),
            c(0xFFF4FBF4), c(0xFFD3EDD3), listOf(c(0xFFF4FBF4)), c(0xFF0A3A12),
        )
    }
    val f = when (style) {
        ClockStyle.STOCK -> Fonts(Figtree, Figtree, FontWeight.SemiBold, false)
        ClockStyle.COLORS -> Fonts(Fredoka, Figtree, FontWeight.Bold, false)
        ClockStyle.RETRO -> Fonts(Bebas, SpaceMono, FontWeight.Bold, true)
        ClockStyle.RADIO -> Fonts(Archivo, Archivo, FontWeight.Bold, true)
        ClockStyle.TERMINAL -> Fonts(Vt323, JetMono, FontWeight.Bold, false)
    }
    val radius = when (style) {
        ClockStyle.STOCK -> 22.dp
        ClockStyle.COLORS -> 26.dp
        ClockStyle.RETRO -> 10.dp
        ClockStyle.RADIO -> 10.dp
        ClockStyle.TERMINAL -> 0.dp
    }
    return Db(style, p, f, radius)
}

@Composable
fun DaybreakTheme(style: ClockStyle, mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val db = buildTheme(style, dark)
    val p = db.p
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, secondary = p.link,
        background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, surfaceContainerHigh = p.surface2,
        surfaceContainerHighest = p.surface2, surfaceContainer = p.surface, primaryContainer = p.accentSoft,
        onPrimaryContainer = p.text, outline = p.border, tertiaryContainer = p.accentSoft,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, secondary = p.link,
        background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surface2, surfaceContainer = p.surface, primaryContainer = p.accentSoft,
        onPrimaryContainer = p.text, outline = p.border, tertiaryContainer = p.accentSoft,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalDb provides db, content = content)
    }
}
