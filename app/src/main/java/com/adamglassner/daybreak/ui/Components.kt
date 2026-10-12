package com.adamglassner.daybreak.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.daybreak.data.ClockStyle
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------- formatting ----------

fun formatTime(hour: Int, minute: Int, use24: Boolean): String =
    if (use24) "%02d:%02d".format(hour, minute)
    else "${if (hour % 12 == 0) 12 else hour % 12}:%02d %s".format(minute, if (hour < 12) "AM" else "PM")

fun clockDigits(hour: Int, minute: Int, use24: Boolean): String =
    if (use24) "%02d:%02d".format(hour, minute)
    else "${if (hour % 12 == 0) 12 else hour % 12}:%02d".format(minute)

fun ampm(hour: Int) = if (hour < 12) "AM" else "PM"

val DAY_LETTERS = listOf(7 to "S", 1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S")
val DAY_NAMES = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

fun describeDays(days: Set<Int>): String = when {
    days.isEmpty() -> "Once"
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    else -> listOf(7, 1, 2, 3, 4, 5, 6).filter { it in days }.joinToString(", ") { DAY_NAMES.getValue(it) }
}

fun formatDuration(ms: Long, withCentis: Boolean): String {
    val total = ms.coerceAtLeast(0)
    val h = total / 3_600_000
    val m = (total / 60_000) % 60
    val s = (total / 1000) % 60
    val cs = (total / 10) % 100
    val base = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return if (withCentis) "$base.%02d".format(cs) else base
}

@Composable
fun rememberNow(stepMs: Long = 1000L): State<Long> = produceState(System.currentTimeMillis(), stepMs) {
    while (true) {
        value = System.currentTimeMillis()
        delay(stepMs)
    }
}

// ---------- text ----------

@Composable
fun DText(
    text: String,
    size: TextUnit = 16.sp,
    color: Color = LocalDb.current.p.text,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily? = null,
    modifier: Modifier = Modifier,
    italic: Boolean = false,
    align: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = size,
            color = color,
            fontWeight = weight,
            fontFamily = family ?: LocalDb.current.f.body,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = letterSpacing,
            textAlign = align ?: TextAlign.Unspecified,
            // A soft CRT glow on the Terminal style's green text.
            shadow = LocalDb.current.let { db ->
                if (db.style == ClockStyle.TERMINAL && db.p.dark) Shadow(color.copy(alpha = 0.55f), blurRadius = 9f) else null
            },
        ),
        maxLines = maxLines,
    )
}

/** Small section label: upper-case and spaced in Retro and Radio. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val db = LocalDb.current
    if (db.style == ClockStyle.TERMINAL) {
        DText("# " + text.lowercase(), 13.sp, db.p.muted, FontWeight.Bold, modifier = modifier)
        return
    }
    if (db.style == ClockStyle.CLASSIC) {
        DText(text, 14.sp, db.p.text, FontWeight.Bold, modifier = modifier)
        return
    }
    DText(
        text.uppercase(), 13.sp, db.p.muted, FontWeight.Bold, modifier = modifier,
        italic = db.style == ClockStyle.RADIO, letterSpacing = 1.2.sp,
    )
}

// ---------- surfaces ----------

@Composable
fun DbCard(
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val db = LocalDb.current
    val shape = RoundedCornerShape(db.radius)
    var m = modifier.clip(shape).background(color ?: db.p.surface)
    if (db.style == ClockStyle.CLASSIC) {
        m = m.bevel(db.p.dark)
    } else if (db.style == ClockStyle.RETRO || db.style == ClockStyle.RADIO) {
        m = m.border(BorderStroke(2.dp, if (db.style == ClockStyle.RADIO) db.p.panelBorder else db.p.border), shape)
    } else if (!db.p.dark && db.style == ClockStyle.STOCK) {
        m = m.border(BorderStroke(1.dp, db.p.border), shape)
    }
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(padding)) { content() }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDb.current.p.border))
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val db = LocalDb.current
    val onPanel = db.style == ClockStyle.RADIO && db.p.dark
    var m = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DText(title, 16.sp, if (onPanel) db.p.panelText else db.p.text)
            if (subtitle != null) DText(subtitle, 13.sp, if (onPanel) db.p.panelMuted else db.p.muted)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun DbSwitch(checked: Boolean, onChange: (Boolean) -> Unit, description: String) {
    val p = LocalDb.current.p
    if (LocalDb.current.style == ClockStyle.CLASSIC) {
        ClassicCheckbox(checked, onChange, description)
        return
    }
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.semantics { contentDescription = description },
        colors = SwitchDefaults.colors(
            checkedThumbColor = p.onAccent,
            checkedTrackColor = p.accent,
            checkedBorderColor = p.accent,
            uncheckedThumbColor = p.muted,
            uncheckedTrackColor = p.surface2,
            uncheckedBorderColor = p.muted,
        ),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val db = LocalDb.current
    val p = db.p
    if (db.style == ClockStyle.CLASSIC) {
        Box(
            modifier.heightIn(min = 44.dp).background(if (selected) p.surface2 else p.surface)
                .bevel(p.dark, sunken = selected).clickable(onClick = onClick).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) { DText(text, 15.sp, p.text, if (selected) FontWeight.Bold else FontWeight.Normal, align = TextAlign.Center, maxLines = 1) }
        return
    }
    val shape = RoundedCornerShape(if (db.style == ClockStyle.RETRO || db.style == ClockStyle.RADIO) 8.dp else 14.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) p.accent else Color.Transparent)
            .border(BorderStroke(1.5.dp, if (selected) p.accent else p.border), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        DText(text, 15.sp, if (selected) p.onAccent else p.text, FontWeight.Bold, align = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun DayChips(days: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DAY_LETTERS.forEach { (d, letter) ->
            Chip(letter, d in days, Modifier.weight(1f)) { onToggle(d) }
        }
    }
}

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    color: Color? = null,
    onColor: Color? = null,
    onClick: () -> Unit,
) {
    val db = LocalDb.current
    val p = db.p
    val bg = color ?: if (primary) p.accent else p.surface2
    val fg = onColor ?: if (primary) p.onAccent else p.text
    if (db.style == ClockStyle.CLASSIC) {
        // A Windows 95 push button; the main one gets the dark "default button" outline.
        Box(
            modifier.heightIn(min = 52.dp).then(if (primary) Modifier.border(1.dp, Color.Black) else Modifier)
                .background(p.surface).bevel(p.dark).clickable(onClick = onClick).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { DText(text, 17.sp, p.text, if (primary) FontWeight.Bold else FontWeight.Normal) }
        return
    }
    val shape = RoundedCornerShape(if (db.style == ClockStyle.RETRO || db.style == ClockStyle.RADIO) 10.dp else 30.dp)
    Box(
        modifier.heightIn(min = 60.dp).clip(shape).background(bg).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        DText(
            if (db.f.caps) text.uppercase() else text, 17.sp, fg, FontWeight.Bold,
            italic = db.style == ClockStyle.RADIO, letterSpacing = if (db.f.caps) 1.sp else TextUnit.Unspecified,
        )
    }
}

@Composable
fun IconCircle(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val db = LocalDb.current
    val shape = RoundedCornerShape(if (db.style == ClockStyle.STOCK) 22.dp else if (db.style == ClockStyle.COLORS) 14.dp else 8.dp)
    val bg = when (db.style) {
        ClockStyle.COLORS -> db.p.cards[1]
        ClockStyle.RADIO -> db.p.bg
        else -> db.p.surface
    }
    val tint = when (db.style) {
        ClockStyle.COLORS -> db.p.onCard
        else -> db.p.text
    }
    Box(
        modifier.size(48.dp).clip(shape).background(bg)
            .then(if (db.style == ClockStyle.CLASSIC) Modifier.bevel(db.p.dark) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun LinkButton(text: String, icon: ImageVector? = null, color: Color? = null, onClick: () -> Unit) {
    val db = LocalDb.current
    val c = color ?: db.p.link
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = c, modifier = Modifier.size(16.dp))
        DText(if (db.f.caps) text.uppercase() else text, 14.sp, c, FontWeight.Bold, italic = db.style == ClockStyle.RADIO)
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val db = LocalDb.current
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) { Icon(DbIcons.Back, null, tint = db.p.text) }
        DText(title, 22.sp, weight = FontWeight.Bold, family = db.f.display, modifier = Modifier.weight(1f))
        trailing()
    }
}

// ---------- time picker ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(hour: Int, minute: Int, use24: Boolean, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(hour, minute, use24)
    val p = LocalDb.current.p
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { DText("OK", 16.sp, p.link, FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { DText("Cancel", 16.sp, p.muted, FontWeight.Bold) } },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = p.surface2,
                    selectorColor = p.accent,
                    containerColor = p.surface,
                    clockDialSelectedContentColor = p.onAccent,
                    clockDialUnselectedContentColor = p.text,
                    periodSelectorSelectedContainerColor = p.accentSoft,
                    periodSelectorSelectedContentColor = p.text,
                    periodSelectorUnselectedContentColor = p.muted,
                    periodSelectorBorderColor = p.border,
                    timeSelectorSelectedContainerColor = p.accentSoft,
                    timeSelectorUnselectedContainerColor = p.surface2,
                    timeSelectorSelectedContentColor = p.text,
                    timeSelectorUnselectedContentColor = p.text,
                ),
            )
        },
    )
}

// ---------- clocks ----------

/** Analog clock face, drawn per style. [time] includes fractional seconds for a sweeping hand. */
@Composable
fun AnalogClock(time: LocalTime, size: Dp, modifier: Modifier = Modifier) {
    val db = LocalDb.current
    val p = db.p
    val measurer = rememberTextMeasurer()
    val secF = time.second + time.nano / 1_000_000_000f
    val minF = time.minute + secF / 60f
    val hourF = (time.hour % 12) + minF / 60f
    Canvas(
        modifier.size(size).semantics {
            contentDescription = "Analog clock, ${formatTime(time.hour, time.minute, false)}"
        },
    ) {
        val r = this.size.minDimension / 2f
        val cx = center
        val u = r / 96f // design units from the mockups (radius 96)
        var face: Color
        var hand = p.text
        var sec = p.accent
        var tick = p.text
        var minorTick = p.muted
        when (db.style) {
            ClockStyle.STOCK -> {
                face = p.surface; hand = p.text; sec = p.accent; tick = p.text; minorTick = p.muted
                drawCircle(face, r, cx)
                drawCircle(p.border, r - u, cx, style = Stroke(2 * u))
            }
            ClockStyle.CLASSIC -> {
                face = Color.White; hand = Color.Black; sec = c(0xFFC00000); tick = Color.Black; minorTick = c(0xFF808080)
                drawCircle(c(0xFF808080), r, cx)
                drawCircle(Color.White, r - 2 * u, cx, style = Stroke(2 * u))
                drawCircle(face, r - 5 * u, cx)
            }
            ClockStyle.COLORS -> {
                face = p.accent; hand = p.onAccent; sec = Color.White; tick = p.onAccent; minorTick = p.onAccent
                drawCircle(face, r, cx)
            }
            ClockStyle.RETRO -> {
                face = if (p.dark) c(0xFFF2E2C4) else c(0xFFFFF4DE); hand = c(0xFF2B231B); sec = c(0xFFB3402A)
                tick = hand; minorTick = hand
                drawCircle(c(0xFFC8963E), r, cx)
                drawCircle(face, r * 86f / 96f, cx)
            }
            ClockStyle.TERMINAL -> {
                face = p.bg; hand = p.text; sec = p.link; tick = p.text; minorTick = p.muted
                drawCircle(p.surface, r, cx)
                drawCircle(p.border, r - u, cx, style = Stroke(2 * u))
                drawCircle(p.muted, 88 * u, cx, style = Stroke(1 * u))
            }
            ClockStyle.RADIO -> {
                face = p.panel; hand = p.panelText; sec = p.led; tick = p.panelText; minorTick = p.panelMuted
                drawCircle(p.panelBorder, r, cx)
                drawCircle(face, r - 6 * u, cx)
                drawCircle(p.stripe, 80 * u, cx, style = Stroke(2 * u))
            }
        }

        fun pt(angleDeg: Float, dist: Float): Offset {
            val a = (angleDeg - 90f) * PI.toFloat() / 180f
            return Offset(cx.x + cos(a) * dist, cx.y + sin(a) * dist)
        }

        when (db.style) {
            ClockStyle.STOCK -> for (i in 0 until 12) {
                val major = i % 3 == 0
                drawLine(if (major) tick else minorTick, pt(i * 30f, 88 * u), pt(i * 30f, (if (major) 76 else 80) * u), (if (major) 3 else 2) * u, StrokeCap.Round)
            }
            ClockStyle.COLORS -> for (i in 0 until 12) {
                drawCircle(tick, (if (i % 3 == 0) 6f else 3.5f) * u, pt(i * 30f, 82 * u))
            }
            ClockStyle.RETRO -> {
                val style = TextStyle(fontFamily = BebasFont, fontSize = (26 * u).toSp(), color = tick)
                listOf(0 to "12", 3 to "3", 6 to "6", 9 to "9").forEach { (h, label) ->
                    val layout = measurer.measure(label, style)
                    val at = pt(h * 30f, 66 * u)
                    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
                }
            }
            ClockStyle.TERMINAL -> for (i in 0 until 60) {
                val s = (if (i % 5 == 0) 6f else 2.5f) * u
                val at = pt(i * 6f, 80 * u)
                drawRect(if (i % 5 == 0) tick else minorTick, Offset(at.x - s / 2, at.y - s / 2), Size(s, s))
            }
            ClockStyle.RADIO -> for (i in 0 until 4) {
                drawLine(tick, pt(i * 90f, 76 * u), pt(i * 90f, 62 * u), 4 * u)
            }
            ClockStyle.CLASSIC -> for (i in 0 until 60) {
                val major = i % 5 == 0
                val s = (if (major) 5f else 2f) * u
                val at = pt(i * 6f, 80 * u)
                drawRect(if (major) tick else minorTick, Offset(at.x - s / 2, at.y - s / 2), Size(s, s))
            }
        }

        val square = db.style == ClockStyle.RETRO || db.style == ClockStyle.TERMINAL || db.style == ClockStyle.CLASSIC
        val cap = if (square) StrokeCap.Square else StrokeCap.Round
        val hourW = when (db.style) { ClockStyle.COLORS -> 10f; ClockStyle.RADIO -> 8f; ClockStyle.RETRO -> 6f; else -> 7f } * u
        val minW = when (db.style) { ClockStyle.COLORS -> 6f; ClockStyle.RADIO -> 5f; ClockStyle.RETRO -> 3.5f; else -> 4f } * u
        drawLine(hand, cx, pt(hourF * 30f, 46 * u), hourW, cap)
        drawLine(hand, cx, pt(minF * 6f, 70 * u), minW, cap)
        drawLine(sec, pt(secF * 6f + 180f, 16 * u), pt(secF * 6f, 78 * u), 2.5f * u, StrokeCap.Round)
        drawCircle(if (db.style == ClockStyle.COLORS) Color.White else sec, 6 * u, cx)
    }
}

// Seven-segment LED digits (Clock radio style)
private val SEGMENTS = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc", '5' to "afgcd",
    '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", ' ' to "", '-' to "g",
)

private fun DrawScope.segment(x: Float, y: Float, w: Float, h: Float, s: Float, color: Color) {
    val slant = 0.1f
    fun px(xx: Float, yy: Float) = Offset((xx + (90f - yy) * slant) * s, yy * s)
    val path = Path().apply {
        val a = px(x, y); moveTo(a.x, a.y)
        val b = px(x + w, y); lineTo(b.x, b.y)
        val c2 = px(x + w, y + h); lineTo(c2.x, c2.y)
        val d = px(x, y + h); lineTo(d.x, d.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.digit(ch: Char, on: Color, off: Color) {
    val s = size.height / 90f
    val lit = SEGMENTS[ch] ?: ""
    fun col(seg: Char) = if (seg in lit) on else off
    segment(10f, 2f, 30f, 7f, s, col('a'))
    segment(41f, 10f, 7f, 32f, s, col('b'))
    segment(41f, 48f, 7f, 32f, s, col('c'))
    segment(10f, 81f, 30f, 7f, s, col('d'))
    segment(2f, 48f, 7f, 32f, s, col('e'))
    segment(2f, 10f, 7f, 32f, s, col('f'))
    segment(10f, 41.5f, 30f, 7f, s, col('g'))
}

@Composable
fun SevenSeg(text: String, height: Dp, on: Color, off: Color, modifier: Modifier = Modifier, colonOn: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(height * 0.05f)) {
        text.forEach { ch ->
            when (ch) {
                ':' -> Canvas(Modifier.size(height * 0.16f, height)) {
                    val col = if (colonOn) on else off
                    val r = this.size.width * 0.32f
                    drawCircle(col, r, Offset(this.size.width * 0.62f, this.size.height * 0.32f))
                    drawCircle(col, r, Offset(this.size.width * 0.42f, this.size.height * 0.68f))
                }
                '.' -> Canvas(Modifier.size(height * 0.14f, height)) {
                    drawCircle(on, this.size.width * 0.38f, Offset(this.size.width * 0.5f, this.size.height * 0.92f))
                }
                else -> Canvas(Modifier.size(height * 0.64f, height)) { digit(ch, on, off) }
            }
        }
    }
}

/** A large time readout in the current style's display face. */
@Composable
fun BigReadout(text: String, size: TextUnit, color: Color? = null, modifier: Modifier = Modifier) {
    val db = LocalDb.current
    when (db.style) {
        ClockStyle.RADIO -> SevenSeg(text.padStart(1), (size.value * 1.15f).dp, db.p.led, db.p.ledOff, modifier)
        else -> DText(
            text, size, color ?: db.p.text,
            if (db.style == ClockStyle.RETRO) FontWeight.Normal else FontWeight.SemiBold,
            family = db.f.display, modifier = modifier,
            letterSpacing = if (db.style == ClockStyle.RETRO) 2.sp else (-0.5).sp,
        )
    }
}

/** A dark LED window for the Clock radio style. */
@Composable
fun LedWindow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalDb.current.p
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.clip(shape).background(p.panel).border(BorderStroke(3.dp, p.panelBorder), shape).padding(14.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun FlipTile(text: String, big: Boolean) {
    val p = LocalDb.current.p
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.clip(shape).background(if (p.dark) p.surface else c(0xFF2B231B))
            .border(BorderStroke(2.dp, if (p.dark) p.border else c(0xFF4A3D2E)), shape)
            .padding(horizontal = if (big) 12.dp else 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        DText(
            text, if (big) 84.sp else 46.sp,
            if (big) c(0xFFF2E2C4) else c(0xFFF2B544),
            family = BebasFont, letterSpacing = 2.sp,
        )
        Canvas(Modifier.matchParentSize()) {
            val t = 2.dp.toPx()
            drawRect(c(0xFF1E1914), Offset(0f, size.height / 2f - t / 2f), Size(size.width, t))
        }
    }
}

/** Woodgrain speaker grille (Clock radio style). */
@Composable
fun WoodGrille(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(c(0xFF57361F))
        var y = 0f
        val step = 10.dp.toPx()
        var i = 0
        while (y < size.height) {
            val tone = if (i % 3 == 0) c(0xFF6E482B) else if (i % 3 == 1) c(0xFF6A4428) else c(0xFF5E3B22)
            drawRect(tone, Offset(0f, y), Size(size.width, step * 0.62f))
            drawRect(c(0xFF2A1A0F), Offset(0f, y + step * 0.68f), Size(size.width, step * 0.32f))
            y += step; i++
        }
        drawRect(c(0xFF120D0A), Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx()))
    }
}

/** 24-hour ring with the sleep window filled in. */
@Composable
fun SleepRing(bedMin: Int, wakeMin: Int, size: Dp, track: Color, fill: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = 22.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        val span = ((wakeMin - bedMin + 1440) % 1440).toFloat()
        val start = bedMin / 1440f * 360f - 90f
        drawArc(fill, start, span / 1440f * 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** A progress ring (stopwatch / timer). */
@Composable
fun ProgressRing(fraction: Float, size: Dp, track: Color, fill: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = 10.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        if (fraction > 0f) {
            drawArc(fill, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** Terminal style: a shell prompt, a wireframe clock and a big VT323 readout with a blinking cursor. */
@Composable
fun TerminalHero(use24: Boolean, showSeconds: Boolean, clockSize: Dp) {
    val p = LocalDb.current.p
    val now by rememberNow(500L)
    val t = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), java.time.ZoneId.systemDefault())
    val cursorOn = (now / 500L) % 2L == 0L
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DText("user@daybreak:~$ clock --live", 13.sp, p.muted, family = MonoFont, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
        AnalogClock(t.toLocalTime(), clockSize)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val hm = clockDigits(t.hour, t.minute, use24)
            DText(if (showSeconds) "$hm:%02d".format(t.second) else hm, 84.sp, p.text, family = TerminalFont)
            DText(if (cursorOn) "█" else " ", 60.sp, p.text, family = TerminalFont, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            if (!use24) DText(ampm(t.hour), 28.sp, p.muted, family = TerminalFont, modifier = Modifier.padding(bottom = 14.dp))
        }
        DText(
            t.format(java.time.format.DateTimeFormatter.ofPattern("EEE dd MMM yyyy")).lowercase() + " · " + java.time.ZoneId.systemDefault().id.lowercase(),
            13.sp, p.muted, family = MonoFont,
        )
    }
}

/** Faint CRT scanlines over the whole screen (Terminal style, dark mode). Draw-only: never blocks taps. */
@Composable
fun Scanlines(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Size(size.width, step / 3f))
            y += step
        }
    }
}

// ---------- Classic Daybreak (Windows 95/98 look) ----------

/** The two-tone 3D edge of Windows 95 controls: raised (buttons, panels) or sunken (fields, pressed). */
fun Modifier.bevel(dark: Boolean, sunken: Boolean = false, width: Dp = 2.dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx() / 2f
    val highlight = if (dark) c(0xFF6A6A6A) else Color.White
    val light = if (dark) c(0xFF555555) else c(0xFFDFDFDF)
    val shadow = if (dark) c(0xFF1A1A1A) else c(0xFF808080)
    val black = Color.Black
    val outerTL = if (sunken) shadow else highlight
    val innerTL = if (sunken) black else light
    val outerBR = if (sunken) highlight else black
    val innerBR = if (sunken) light else shadow
    val sw = size.width
    val sh = size.height
    drawRect(outerTL, Offset(0f, 0f), Size(sw, w))
    drawRect(outerTL, Offset(0f, 0f), Size(w, sh))
    drawRect(outerBR, Offset(0f, sh - w), Size(sw, w))
    drawRect(outerBR, Offset(sw - w, 0f), Size(w, sh))
    drawRect(innerTL, Offset(w, w), Size(sw - 2 * w, w))
    drawRect(innerTL, Offset(w, w), Size(w, sh - 2 * w))
    drawRect(innerBR, Offset(w, sh - 2 * w), Size(sw - 2 * w, w))
    drawRect(innerBR, Offset(sw - 2 * w, w), Size(w, sh - 2 * w))
}

/** Navy gradient title bar, like a Windows 98 window. */
@Composable
fun ClassicTitleBar(title: String, modifier: Modifier = Modifier) {
    val p = LocalDb.current.p
    val colors = if (p.dark) listOf(c(0xFF3A6EA5), c(0xFF6A9AD0)) else listOf(c(0xFF000080), c(0xFF1084D0))
    Row(
        modifier.fillMaxWidth().heightIn(min = 26.dp).background(Brush.horizontalGradient(colors)).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DText(title, 15.sp, Color.White, FontWeight.Bold, maxLines = 1)
    }
}

/** A raised gray window with a title bar around [content]. */
@Composable
fun ClassicWindow(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalDb.current.p
    Column(modifier.background(p.surface).bevel(p.dark).padding(4.dp)) {
        ClassicTitleBar(title)
        Column(Modifier.fillMaxWidth().padding(10.dp), content = content)
    }
}

/** A sunken white check box with a check mark, used in place of switches. */
@Composable
fun ClassicCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, description: String) {
    val p = LocalDb.current.p
    Box(
        Modifier.size(44.dp).clickable { onChange(!checked) }.semantics {
            contentDescription = description
            stateDescription = if (checked) "Checked" else "Not checked"
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(22.dp).background(p.surface2).bevel(p.dark, sunken = true), contentAlignment = Alignment.Center) {
            if (checked) {
                Canvas(Modifier.size(12.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.05f, size.height * 0.5f)
                        lineTo(size.width * 0.4f, size.height * 0.85f)
                        lineTo(size.width * 0.95f, size.height * 0.1f)
                    }
                    drawPath(path, p.text, style = Stroke(width = 2.5.dp.toPx()))
                }
            }
        }
    }
}
