package com.adamglassner.daybreak.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    Font("font/figtree_regular.ttf", FontWeight.Normal),
    Font("font/figtree_medium.ttf", FontWeight.Medium),
    Font("font/figtree_semibold.ttf", FontWeight.SemiBold),
    Font("font/figtree_bold.ttf", FontWeight.Bold),
)
private val Fredoka = FontFamily(Font("font/fredoka_semibold.ttf", FontWeight.SemiBold))
private val Bebas = FontFamily(Font("font/bebas_neue.ttf", FontWeight.Normal))
private val SpaceMono = FontFamily(
    Font("font/space_mono_regular.ttf", FontWeight.Normal),
    Font("font/space_mono_bold.ttf", FontWeight.Bold),
)
private val Archivo = FontFamily(
    Font("font/archivo_narrow_bold.ttf", FontWeight.Bold),
    Font("font/archivo_narrow_bold_italic.ttf", FontWeight.Bold, FontStyle.Italic),
)
private val Vt323 = FontFamily(Font("font/vt323.ttf", FontWeight.Normal))
private val JetMono = FontFamily(
    Font("font/jetbrains_mono_regular.ttf", FontWeight.Normal),
    Font("font/jetbrains_mono_bold.ttf", FontWeight.Bold),
)
private val Pixel = FontFamily(
    Font("font/pixelify_regular.ttf", FontWeight.Normal),
    Font("font/pixelify_bold.ttf", FontWeight.Bold),
)
private val Outfit = FontFamily(
    Font("font/outfit_light.ttf", FontWeight.Light),
    Font("font/outfit_regular.ttf", FontWeight.Normal),
    Font("font/outfit_regular.ttf", FontWeight.Medium),
    Font("font/outfit_semibold.ttf", FontWeight.SemiBold),
    Font("font/outfit_semibold.ttf", FontWeight.Bold),
)
/** Modern style's big numbers: every weight one step thinner, for light, airy digits. */
private val OutfitThin = FontFamily(
    Font("font/outfit_extralight.ttf", FontWeight.Normal),
    Font("font/outfit_extralight.ttf", FontWeight.Medium),
    Font("font/outfit_light.ttf", FontWeight.SemiBold),
    Font("font/outfit_regular.ttf", FontWeight.Bold),
)
val PixelFont = Pixel
val ModernFont = OutfitThin
val TerminalFont = Vt323
val MonoFont = JetMono
val RadioFont = Archivo
val BebasFont = Bebas
val FredokaFont = Fredoka
val FigtreeFont = Figtree

fun c(hex: Long) = Color(hex)

fun buildTheme(style: ClockStyle, dark: Boolean): Db {
    val p = when (style) {
        // Windows 11 (Fluent) colors for the Stock style on the desktop.
        ClockStyle.STOCK -> if (dark) Palette(
            true, c(0xFF202020), c(0xFF2B2B2B), c(0xFF323232), c(0xFF3A3A3A), c(0xFFFFFFFF), c(0xFFC5C5C5),
            c(0xFF60CDFF), c(0xFF003049), c(0xFF27384A), c(0xFF60CDFF), c(0xFFFCE100), c(0xFF201A00),
            c(0xFF2A2938), c(0xFF3D3B55), listOf(c(0xFF2B2B2B)), c(0xFFFFFFFF),
        ) else Palette(
            false, c(0xFFF3F3F3), c(0xFFFFFFFF), c(0xFFF9F9F9), c(0xFFE5E5E5), c(0xFF1A1A1A), c(0xFF5D5D5D),
            c(0xFF005FB8), c(0xFFFFFFFF), c(0xFFDCEBF8), c(0xFF005FB8), c(0xFF9D5D00), c(0xFFFFFFFF),
            c(0xFFEEEDFB), c(0xFFDEDCF7), listOf(c(0xFFFFFFFF)), c(0xFF1A1A1A),
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
        // "Classic Daybreak": Windows 95/98 gray, bevels and navy title bars.
        ClockStyle.MODERN -> if (dark) Palette(
            true, c(0xFF0B0D12), c(0xFF151821), c(0xFF1E2230), c(0xFF252A37), c(0xFFF3F4F8), c(0xFF8B93A7),
            c(0xFF7C6CFF), c(0xFFFFFFFF), c(0xFF2A2550), c(0xFFA99FFF), c(0xFFFFB86B), c(0xFF2A1600),
            c(0xFF151821), c(0xFF252040), listOf(c(0xFF151821)), c(0xFFF3F4F8),
        ) else Palette(
            false, c(0xFFF3F4F8), c(0xFFFFFFFF), c(0xFFECEEF4), c(0xFFE3E6EE), c(0xFF0F1220), c(0xFF6A7286),
            c(0xFF5B4BFF), c(0xFFFFFFFF), c(0xFFE8E5FF), c(0xFF4B3BEA), c(0xFFE08A1E), c(0xFFFFFFFF),
            c(0xFFEEECFF), c(0xFFE0DCFF), listOf(c(0xFFFFFFFF)), c(0xFF0F1220),
        )
        ClockStyle.CLASSIC -> if (dark) Palette(
            true, c(0xFF303030), c(0xFF3C3C3C), c(0xFF1E1E1E), c(0xFF1A1A1A), c(0xFFFFFFFF), c(0xFFC8C8C8),
            c(0xFF3A6EA5), c(0xFFFFFFFF), c(0xFF4A4A4A), c(0xFF9DC3F0), c(0xFFFFD700), c(0xFF000000),
            c(0xFF3C3C3C), c(0xFF4A4A4A), listOf(c(0xFF3C3C3C)), c(0xFFFFFFFF),
        ) else Palette(
            false, c(0xFFC0C0C0), c(0xFFC0C0C0), c(0xFFFFFFFF), c(0xFF808080), c(0xFF000000), c(0xFF303030),
            c(0xFF000080), c(0xFFFFFFFF), c(0xFFDFDFDF), c(0xFF000080), c(0xFF808000), c(0xFFFFFFFF),
            c(0xFFC0C0C0), c(0xFFDFDFDF), listOf(c(0xFFC0C0C0)), c(0xFF000000),
        )
    }
    val f = when (style) {
        ClockStyle.STOCK -> if (com.adamglassner.daybreak.Platform.isWindows) Fonts(FontFamily.Default, FontFamily.Default, FontWeight.SemiBold, false) // Segoe UI
            else Fonts(Figtree, Figtree, FontWeight.SemiBold, false)
        ClockStyle.COLORS -> Fonts(Fredoka, Figtree, FontWeight.Bold, false)
        ClockStyle.RETRO -> Fonts(Bebas, SpaceMono, FontWeight.Bold, true)
        ClockStyle.RADIO -> Fonts(Archivo, Archivo, FontWeight.Bold, true)
        ClockStyle.TERMINAL -> Fonts(Vt323, JetMono, FontWeight.Bold, false)
        ClockStyle.CLASSIC -> Fonts(Pixel, Pixel, FontWeight.Bold, false)
        ClockStyle.MODERN -> Fonts(OutfitThin, Outfit, FontWeight.SemiBold, false)
    }
    val radius = when (style) {
        ClockStyle.STOCK -> 8.dp
        ClockStyle.COLORS -> 16.dp
        ClockStyle.RETRO -> 6.dp
        ClockStyle.RADIO -> 6.dp
        ClockStyle.TERMINAL -> 0.dp
        ClockStyle.CLASSIC -> 0.dp
        ClockStyle.MODERN -> 24.dp
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
