package com.adamglassner.daybreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.daybreak.alarm.Scheduler
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.Settings
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.data.TimerState

@Composable
private fun TabTitle(title: String) {
    val db = LocalDb.current
    DText(
        if (db.f.caps) title.uppercase() else title, 28.sp, weight = FontWeight.Bold, family = db.f.display,
        italic = db.style == ClockStyle.RADIO, modifier = Modifier.padding(start = 24.dp, top = 20.dp),
    )
}

/** Big centered time inside a ring (or an LED window for the Clock radio style). */
@Composable
private fun RingReadout(fraction: Float, main: String, small: String?, caption: String?, ringColor: androidx.compose.ui.graphics.Color) {
    val db = LocalDb.current
    val p = db.p
    if (db.style == ClockStyle.RADIO) {
        LedWindow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (caption != null) DText(caption.uppercase(), 12.sp, p.panelMuted, FontWeight.Bold, italic = true)
                Row(verticalAlignment = Alignment.Bottom) {
                    SevenSeg(main, 76.dp, p.led, p.ledOff)
                    if (small != null) {
                        Spacer(Modifier.width(6.dp))
                        SevenSeg(small, 36.dp, p.led, p.ledOff)
                    }
                }
            }
        }
        return
    }
    Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
        ProgressRing(fraction, 260.dp, p.surface, ringColor)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (caption != null) DText(caption, 13.sp, p.muted)
            DText(
                main, if (db.style == ClockStyle.RETRO) 72.sp else 54.sp,
                weight = if (db.style == ClockStyle.RETRO) FontWeight.Normal else FontWeight.SemiBold,
                family = db.f.display, letterSpacing = if (db.style == ClockStyle.RETRO) 2.sp else (-0.5).sp,
            )
            if (small != null) DText(small, 24.sp, ringColor, FontWeight.Medium, family = db.f.display)
        }
    }
}

// ---------- stopwatch ----------

@Composable
fun StopwatchScreen() {
    val db = LocalDb.current
    val p = db.p
    val sw by Store.stopwatch.collectAsState()
    val now by rememberNow(if (sw.startedAt > 0) 31L else 1000L)
    val elapsed = sw.elapsed(now)
    val running = sw.startedAt > 0
    val text = formatDuration(elapsed, true)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        TabTitle("Stopwatch")
        RingReadout(
            (elapsed % 60_000L) / 60_000f,
            text.substringBeforeLast('.'),
            if (db.style == ClockStyle.RADIO) text.substringAfterLast('.') else "." + text.substringAfterLast('.'),
            null, p.accent,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton(if (running) "Lap" else "Reset", Modifier.weight(1f), primary = false) {
                if (running) {
                    val e = sw.elapsed(System.currentTimeMillis())
                    val done = sw.laps.sum()
                    Store.setStopwatch(sw.copy(laps = listOf(e - done) + sw.laps))
                } else {
                    Store.setStopwatch(com.adamglassner.daybreak.data.StopwatchState())
                }
            }
            BigButton(if (running) "Pause" else "Start", Modifier.weight(1f)) {
                val t = System.currentTimeMillis()
                if (running) Store.setStopwatch(sw.copy(baseMs = sw.elapsed(t), startedAt = 0L))
                else Store.setStopwatch(sw.copy(startedAt = t))
            }
        }
        if (sw.laps.isNotEmpty()) {
            val fastest = sw.laps.minOrNull()
            val slowest = sw.laps.maxOrNull()
            Column(Modifier.padding(horizontal = 24.dp)) {
                var total = sw.laps.sum()
                sw.laps.forEachIndexed { i, lap ->
                    val color = when {
                        sw.laps.size > 2 && lap == fastest -> p.link
                        sw.laps.size > 2 && lap == slowest -> p.muted
                        else -> p.text
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        DText("Lap ${sw.laps.size - i}", 16.sp, p.muted, modifier = Modifier.weight(1f))
                        DText(formatDuration(lap, true), 16.sp, color, FontWeight.Medium, modifier = Modifier.weight(1f))
                        DText(formatDuration(total, true), 16.sp, p.muted)
                    }
                    Divider()
                    total -= lap
                }
            }
        }
    }
}

// ---------- timer ----------

private val PRESETS = listOf(1, 3, 5, 10, 15, 30)

@Composable
fun TimerScreen(settings: Settings) {
    val db = LocalDb.current
    val p = db.p
    val t by Store.timer.collectAsState()
    val running = t.endAt > 0
    val now by rememberNow(if (running) 200L else 1000L)
    val remaining = if (running) (t.endAt - now).coerceAtLeast(0) else t.remainingMs
    var custom by remember { mutableStateOf(false) }
    val accent = if (db.style == ClockStyle.STOCK || db.style == ClockStyle.COLORS) p.warm else p.accent

    fun set(total: Long) {
        Scheduler.cancelTimer()
        Store.setTimer(TimerState(totalMs = total, remainingMs = total, endAt = 0L))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        TabTitle("Timer")
        val secs = (remaining + 999) / 1000
        RingReadout(
            if (t.totalMs > 0) remaining.toFloat() / t.totalMs else 0f,
            formatDuration(secs * 1000, false), null,
            "of ${formatDuration(t.totalMs, false)}", accent,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("Reset", Modifier.weight(1f), primary = false) { set(t.totalMs) }
            BigButton(
                if (running) "Pause" else "Start", Modifier.weight(1f),
                color = accent, onColor = if (accent == p.warm) p.onWarm else p.onAccent,
            ) {
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
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Quick start")
            PRESETS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        Chip("$m min", !running && t.totalMs == m * 60_000L, Modifier.weight(1f)) { set(m * 60_000L) }
                    }
                }
            }
            Chip("Custom time…", false, Modifier.fillMaxWidth()) { custom = true }
        }
        DbCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), padding = 0.dp) {
            SoundPicker("Timer sound", settings.timerSoundName) { uri, name ->
                Store.updateSettings { it.copy(timerSoundUri = uri, timerSoundName = name) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (custom) {
        var mins by remember { mutableStateOf("") }
        var secsText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { custom = false },
            containerColor = p.surface,
            title = { DText("Set a timer", 20.sp, weight = FontWeight.Bold) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        mins, { mins = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f),
                        label = { DText("Minutes", 13.sp, p.muted) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        secsText, { secsText = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f),
                        label = { DText("Seconds", 13.sp, p.muted) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val total = ((mins.toLongOrNull() ?: 0) * 60 + (secsText.toLongOrNull() ?: 0).coerceAtMost(59)) * 1000
                    if (total > 0) set(total)
                    custom = false
                }) { DText("Set", 16.sp, p.link, FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { DText("Cancel", 16.sp, p.muted, FontWeight.Bold) } },
        )
    }
}
