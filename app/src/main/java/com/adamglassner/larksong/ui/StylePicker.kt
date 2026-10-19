package com.adamglassner.larksong.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.larksong.data.ClockStyle

fun styleName(style: ClockStyle): String = when (style) {
    ClockStyle.LARKSONG -> "Larksong"
    ClockStyle.DAWN -> "Dawn"
    ClockStyle.DUSK -> "Dusk"
    ClockStyle.STOCK -> "Stock"
    ClockStyle.MODERN -> "Modern"
    ClockStyle.COLORS -> "Colors"
    ClockStyle.RETRO -> "Retro"
    ClockStyle.RADIO -> "Radio"
    ClockStyle.TERMINAL -> "Terminal"
    ClockStyle.CLASSIC -> "Classic Larksong"
}

data class StyleGroup(val title: String, val blurb: String, val styles: List<ClockStyle>)

/** How styles are grouped in Settings. */
val STYLE_GROUPS = listOf(
    StyleGroup("Larksong", "Sky colors from the Larksong icon", listOf(ClockStyle.LARKSONG, ClockStyle.DAWN, ClockStyle.DUSK)),
    StyleGroup("Everyday", "Clean and simple", listOf(ClockStyle.STOCK, ClockStyle.MODERN, ClockStyle.COLORS)),
    StyleGroup("Throwback", "Retro looks · Radio adds a world radio tuner", listOf(ClockStyle.RETRO, ClockStyle.RADIO, ClockStyle.TERMINAL, ClockStyle.CLASSIC)),
)

/** The style picker: a labeled row of preview cards for each group. */
@Composable
fun StyleGroups(current: ClockStyle, cardWidth: Dp = 108.dp, onPick: (ClockStyle) -> Unit) {
    val p = LocalDb.current.p
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        STYLE_GROUPS.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    DText(g.title, 15.sp, p.text, FontWeight.Bold)
                    DText("  ${g.blurb}", 12.sp, p.muted, maxLines = 1)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.styles.forEach { s -> StyleCard(s, current == s, Modifier.width(cardWidth)) { onPick(s) } }
                }
            }
        }
    }
}

@Composable
fun StyleCard(style: ClockStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalDb.current.p
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(p.surface)
            .border(BorderStroke(2.dp, if (selected) p.accent else p.border), shape)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(10.dp))) { StyleSwatch(style) }
        DText(styleName(style), 14.sp, p.text, FontWeight.Bold, maxLines = 2, align = TextAlign.Center)
    }
}

/** A tiny preview of each style. */
@Composable
private fun StyleSwatch(style: ClockStyle) {
    val full = Modifier.fillMaxSize()
    when (style) {
        ClockStyle.LARKSONG -> Box(full.background(Brush.verticalGradient(LARKSONG_SKY)), contentAlignment = Alignment.Center) {
            Box(Modifier.offset(y = 14.dp).size(40.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(c(0xFFFFD36B), c(0xFFFF7A59)))))
            DText("10:09", 20.sp, c(0xFFFFF4DE), FontWeight.SemiBold, family = OutfitFont)
        }
        ClockStyle.DAWN -> Box(full.background(Brush.verticalGradient(DAWN_SKY_LIGHT)), contentAlignment = Alignment.Center) {
            DText("10:09", 24.sp, c(0xFF3B2240), family = ModernFont)
        }
        ClockStyle.DUSK -> Box(full.background(Brush.verticalGradient(DUSK_SKY_DARK)), contentAlignment = Alignment.Center) {
            DText("10:09", 24.sp, c(0xFFF7E8D6), family = ModernFont)
        }
        ClockStyle.STOCK -> Box(full.background(c(0xFF1C1F26)), contentAlignment = Alignment.Center) {
            DText("10:09", 20.sp, c(0xFFECEEF2), FontWeight.SemiBold, family = FigtreeFont)
        }
        ClockStyle.COLORS -> Box(full.background(c(0xFFFF7A59)), contentAlignment = Alignment.Center) {
            DText("10:09", 20.sp, c(0xFF1A0B3A), FontWeight.SemiBold, family = FredokaFont)
        }
        ClockStyle.RETRO -> Box(full.background(c(0xFF2B231B)), contentAlignment = Alignment.Center) {
            DText("10:09", 26.sp, c(0xFFF2B544), family = BebasFont, letterSpacing = 1.sp)
        }
        ClockStyle.TERMINAL -> Box(full.background(c(0xFF000000)), contentAlignment = Alignment.Center) {
            DText(">10:09", 24.sp, c(0xFF33FF66), family = TerminalFont)
        }
        ClockStyle.MODERN -> Box(full.background(Brush.linearGradient(listOf(c(0xFF0B0D12), c(0xFF2A2550)))), contentAlignment = Alignment.Center) {
            DText("10:09", 24.sp, c(0xFFF3F4F8), family = ModernFont)
        }
        ClockStyle.CLASSIC -> Column(full.background(c(0xFFC0C0C0))) {
            Box(Modifier.fillMaxWidth().height(12.dp).background(Brush.horizontalGradient(listOf(c(0xFF000080), c(0xFF1084D0)))))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                DText("10:09", 20.sp, c(0xFF000000), FontWeight.Bold, family = PixelFont)
            }
        }
        ClockStyle.RADIO -> Column(full.background(c(0xFF0A0606))) {
            Box(Modifier.fillMaxWidth().height(10.dp).background(c(0xFF6A4428)))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                SevenSeg(" 8:54", 26.dp, c(0xFFFF2D4F), c(0xFF2B0B12))
            }
        }
    }
}
