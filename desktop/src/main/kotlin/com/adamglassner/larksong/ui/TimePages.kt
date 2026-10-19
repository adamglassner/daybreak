package com.adamglassner.larksong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.larksong.alarm.Scheduler
import com.adamglassner.larksong.data.ClockStyle
import com.adamglassner.larksong.data.Settings
import com.adamglassner.larksong.data.StopwatchState
import com.adamglassner.larksong.data.Store
import com.adamglassner.larksong.data.TimerState

/** Big readout: LED digits in the radio style, the display face otherwise. */
@Composable
private fun Readout(main: String, small: String?, accent: androidx.compose.ui.graphics.Color) {
    val db = LocalDb.current
    val p = db.p
    if (db.style == ClockStyle.RADIO) {
        LedWindow {
            Row(verticalAlignment = Alignment.Bottom) {
                SevenSeg(main, 88.dp, p.led, p.ledOff)
                if (small != null) { Spacer(Modifier.width(8.dp)); SevenSeg(small, 40.dp, p.led, p.ledOff) }
            }
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            DText(main, if (db.style == ClockStyle.RETRO) 96.sp else 72.sp, p.text,
                if (db.style == ClockStyle.RETRO) FontWeight.Normal else FontWeight.SemiBold, family = db.f.display)
            if (small != null) DText(".$small", 32.sp, accent, FontWeight.Medium, family = db.f.display, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

// ---------- timer ----------

private val PRESETS = listOf(1, 3, 5, 10, 15, 20, 30, 45, 60)

private fun setTimer(total: Long) {
    Scheduler.cancelTimer()
    Store.setTimer(TimerState(totalMs = total, remainingMs = total, endAt = 0L))
}

@Composable
fun TimerPage(settings: Settings) {
    val db = LocalDb.current
    val p = db.p
    val t by Store.timer.collectAsState()
    val running = t.endAt > 0
    val now by rememberNow(if (running) 200L else 1000L)
    val remaining = if (running) (t.endAt - now).coerceAtLeast(0) else t.remainingMs
    val accent = if (db.style == ClockStyle.STOCK || db.style == ClockStyle.COLORS) p.warm else p.accent
    var setMin by remember { mutableIntStateOf(5) }
    var setSec by remember { mutableIntStateOf(0) }

    ScrollPage {
        PageHeader("Timer", if (running) "Running" else null)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 820.dp
            val main: @Composable (Modifier) -> Unit = { m ->
                FCard(m, padding = 28.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            if (db.style != ClockStyle.RADIO) ProgressRing(if (t.totalMs > 0) remaining.toFloat() / t.totalMs else 0f, 320.dp, p.surface2, accent)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Readout(formatDuration(((remaining + 999) / 1000) * 1000, false), null, accent)
                                DText("of ${formatDuration(t.totalMs, false)}", 14.sp, p.muted)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FButton(if (running) "Pause" else "Start", Modifier.widthIn(min = 120.dp), accent = true,
                                icon = if (running) null else DbIcons.Play) {
                                if (running) {
                                    Scheduler.cancelTimer()
                                    Store.setTimer(t.copy(remainingMs = (t.endAt - System.currentTimeMillis()).coerceAtLeast(0), endAt = 0L))
                                } else {
                                    val left = if (t.remainingMs <= 0) t.totalMs else t.remainingMs
                                    val end = System.currentTimeMillis() + left
                                    Store.setTimer(t.copy(remainingMs = left, endAt = end))
                                    Scheduler.scheduleTimer(end)
                                }
                            }
                            FButton("Reset", Modifier.widthIn(min = 120.dp)) { setTimer(t.totalMs) }
                        }
                    }
                }
            }
            val side: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                        DText("Quick start", 16.sp, p.text, FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        PRESETS.chunked(3).forEach { row ->
                            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { m2 ->
                                    FButton(if (m2 == 60) "1 hr" else "$m2 min", Modifier.weight(1f), accent = !running && t.totalMs == m2 * 60_000L) {
                                        setTimer(m2 * 60_000L)
                                    }
                                }
                            }
                        }
                    }
                    FCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                        DText("Custom time", 16.sp, p.text, FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NumberSpinner(setMin, 0..180, "minutes", format = { it.toString() }) { setMin = it }
                            DText(":", 32.sp, p.muted)
                            NumberSpinner(setSec, 0..59, "seconds") { setSec = it }
                            Spacer(Modifier.width(6.dp))
                            FButton("Set") {
                                val total = (setMin * 60L + setSec) * 1000L
                                if (total > 0) setTimer(total)
                            }
                        }
                        DText("Scroll over a number to change it", 12.sp, p.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                    FCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                        SoundPicker("Timer sound", settings.timerSoundName) { uri, name ->
                            Store.updateSettings { it.copy(timerSoundUri = uri, timerSoundName = name) }
                        }
                    }
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { main(Modifier.weight(1.2f)); side(Modifier.weight(1f)) }
            else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { main(Modifier.fillMaxWidth()); side(Modifier.fillMaxWidth()) }
        }
    }
}

// ---------- stopwatch ----------

@Composable
fun StopwatchPage() {
    val db = LocalDb.current
    val p = db.p
    val sw by Store.stopwatch.collectAsState()
    val running = sw.startedAt > 0
    val now by rememberNow(if (running) 31L else 1000L)
    val elapsed = sw.elapsed(now)
    val text = formatDuration(elapsed, true)

    ScrollPage {
        PageHeader("Stopwatch")
        FCard(Modifier.fillMaxWidth(), padding = 32.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Readout(text.substringBeforeLast('.'), text.substringAfterLast('.'), p.accent)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FButton(if (running) "Pause" else "Start", Modifier.widthIn(min = 120.dp), accent = true, icon = if (running) null else DbIcons.Play) {
                        val t = System.currentTimeMillis()
                        if (running) Store.setStopwatch(sw.copy(baseMs = sw.elapsed(t), startedAt = 0L)) else Store.setStopwatch(sw.copy(startedAt = t))
                    }
                    FButton("Lap", Modifier.widthIn(min = 120.dp), enabled = running) {
                        val e = sw.elapsed(System.currentTimeMillis())
                        Store.setStopwatch(sw.copy(laps = listOf(e - sw.laps.sum()) + sw.laps))
                    }
                    FButton("Reset", Modifier.widthIn(min = 120.dp), enabled = !running && elapsed > 0) { Store.setStopwatch(StopwatchState()) }
                }
                DText("Space starts and pauses · L adds a lap", 12.sp, p.muted, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (sw.laps.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            val fastest = sw.laps.minOrNull()
            val slowest = sw.laps.maxOrNull()
            FCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    DText("Lap", 13.sp, p.muted, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    DText("Lap time", 13.sp, p.muted, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    DText("Total", 13.sp, p.muted, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                var total = sw.laps.sum()
                sw.laps.forEachIndexed { i, lap ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                    Row(Modifier.fillMaxWidth().background(if (i % 2 == 1) hoverTint() else p.surface.copy(alpha = 0f)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                        val tag = when {
                            sw.laps.size > 2 && lap == fastest -> "  Fastest"
                            sw.laps.size > 2 && lap == slowest -> "  Slowest"
                            else -> ""
                        }
                        DText("${sw.laps.size - i}$tag", 14.sp, if (tag.isNotEmpty()) p.link else p.text, modifier = Modifier.weight(1f))
                        DText(formatDuration(lap, true), 14.sp, p.text, modifier = Modifier.weight(1f))
                        DText(formatDuration(total, true), 14.sp, p.muted, modifier = Modifier.weight(1f))
                    }
                    total -= lap
                }
            }
        }
    }
}

/** Keyboard: Space = start/pause, L = lap (used while the Stopwatch page is open). */
fun stopwatchToggle() {
    val sw = Store.stopwatch.value
    val t = System.currentTimeMillis()
    if (sw.startedAt > 0) Store.setStopwatch(sw.copy(baseMs = sw.elapsed(t), startedAt = 0L)) else Store.setStopwatch(sw.copy(startedAt = t))
}

fun stopwatchLap() {
    val sw = Store.stopwatch.value
    if (sw.startedAt == 0L) return
    val e = sw.elapsed(System.currentTimeMillis())
    Store.setStopwatch(sw.copy(laps = listOf(e - sw.laps.sum()) + sw.laps))
}
