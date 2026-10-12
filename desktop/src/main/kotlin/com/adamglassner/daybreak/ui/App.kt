package com.adamglassner.daybreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.daybreak.alarm.AlarmService
import com.adamglassner.daybreak.alarm.Scheduler
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Short confirmation messages ("Alarm set for 7 hr from now"), shown at the bottom of the window. */
object Notices {
    data class Notice(val text: String, val at: Long = System.nanoTime())
    private val _current = MutableStateFlow<Notice?>(null)
    val current = _current.asStateFlow()
    fun show(text: String) { _current.value = Notice(text) }
    fun clear(n: Notice) { if (_current.value == n) _current.value = null }
}

private sealed interface Overlay {
    data class EditAlarm(val id: Int?) : Overlay
    data object Bedtime : Overlay
    data object Settings : Overlay
    data object Stations : Overlay
}

@Composable
fun App() {
    val db = LocalDb.current
    val p = db.p
    val settings by Store.settings.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    LaunchedEffect(settings.style) {
        if (settings.style != ClockStyle.RADIO) {
            com.adamglassner.daybreak.radio.Radio.stop()
            if (overlay == Overlay.Stations) overlay = null
        }
    }
    val notice by Notices.current.collectAsState()

    Box(Modifier.fillMaxSize().background(p.bg)) {
        val o = overlay
        if (o != null) {
            Box(Modifier.fillMaxSize()) {
                when (o) {
                    is Overlay.EditAlarm -> AlarmEditScreen(o.id, settings) { overlay = null }
                    Overlay.Bedtime -> BedtimeScreen(settings) { overlay = null }
                    Overlay.Stations -> StationsScreen { overlay = null }
                    Overlay.Settings -> SettingsScreen(settings, true, {}) { overlay = null }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> ClockScreen(settings, true, {}, { overlay = Overlay.Settings }, { tab = 1 }, onOpenStations = { overlay = Overlay.Stations }, showVolume = true)
                        1 -> AlarmsScreen(settings, emptyList(), { overlay = Overlay.EditAlarm(it) }, { overlay = Overlay.Bedtime }, { overlay = Overlay.Settings })
                        2 -> StopwatchScreen()
                        else -> TimerScreen(settings)
                    }
                }
                NavBar(tab) { tab = it }
            }
        }

        val n = notice
        if (n != null) {
            LaunchedEffect(n) { delay(2800); Notices.clear(n) }
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(14.dp)).background(if (p.dark) c(0xFFECEEF2) else c(0xFF15171A))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                DText(n.text, 14.sp, if (p.dark) c(0xFF15171A) else c(0xFFECEEF2), FontWeight.Medium)
            }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("Clock", DbIcons.Clock),
    TabItem("Alarms", DbIcons.Alarm),
    TabItem("Stopwatch", DbIcons.Stopwatch),
    TabItem("Timer", DbIcons.Timer),
)

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    val db = LocalDb.current
    val p = db.p
    val barColor = when (db.style) {
        ClockStyle.STOCK -> if (p.dark) c(0xFF181A20) else c(0xFFFFFFFF)
        ClockStyle.COLORS -> p.surface
        ClockStyle.RETRO -> if (p.dark) c(0xFF16120E) else p.surface
        ClockStyle.RADIO -> if (p.dark) c(0xFF2A2420) else p.surface2
    }
    Column(Modifier.fillMaxWidth().background(barColor)) {
        val lineColor = when (db.style) {
            ClockStyle.RADIO -> p.stripe
            ClockStyle.COLORS -> barColor
            else -> p.border
        }
        Box(Modifier.fillMaxWidth().height(if (db.style == ClockStyle.STOCK) 1.dp else 2.dp).background(lineColor))
        Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
            TABS.forEachIndexed { i, item ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).height(76.dp).clickable { onSelect(i) }
                        .semantics { role = Role.Tab; this.selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (db.style == ClockStyle.RADIO) {
                        Box(
                            Modifier.size(22.dp, 34.dp).clip(RoundedCornerShape(6.dp)).background(c(0xFF0A0606)).padding(3.dp),
                            contentAlignment = if (on) Alignment.TopCenter else Alignment.BottomCenter,
                        ) {
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(if (on) c(0xFFE9DCC4) else c(0xFF8E8378)))
                        }
                    } else {
                        val pill = when (db.style) {
                            ClockStyle.COLORS -> p.accent
                            ClockStyle.RETRO -> Color.Transparent
                            else -> p.accentSoft
                        }
                        val tint = when {
                            !on -> p.muted
                            db.style == ClockStyle.COLORS -> p.onAccent
                            db.style == ClockStyle.STOCK -> p.link
                            else -> p.accent
                        }
                        var m = Modifier.size(60.dp, 32.dp).clip(RoundedCornerShape(if (db.style == ClockStyle.RETRO) 6.dp else 16.dp))
                        if (on) m = m.background(pill)
                        Box(m, contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    DText(
                        if (db.f.caps) item.label.uppercase() else item.label,
                        if (db.f.caps) 11.sp else 12.sp,
                        if (on) p.text else p.muted,
                        if (on) FontWeight.Bold else FontWeight.Medium,
                        italic = db.style == ClockStyle.RADIO,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------- ring window content ----------

@Composable
fun RingScreen(r: AlarmService.Ringing, use24: Boolean) {
    val db = LocalDb.current
    val p = db.p
    val now by rememberNow(1000L)
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val isTimer = r.kind == Scheduler.KIND_TIMER
    Column(
        Modifier.fillMaxSize().background(p.bg).padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(80.dp).clip(RoundedCornerShape(40.dp)).background(p.accentSoft), contentAlignment = Alignment.Center) {
            Icon(if (isTimer) DbIcons.Timer else DbIcons.Alarm, null, tint = p.accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        DText(r.title, 20.sp, p.muted, FontWeight.Bold, family = db.f.display)
        Spacer(Modifier.height(8.dp))
        if (db.style == ClockStyle.RADIO) {
            LedWindow { BigReadout(clockDigits(t.hour, t.minute, use24), 64.sp) }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                BigReadout(clockDigits(t.hour, t.minute, use24), 84.sp)
                if (!use24) DText(" " + ampm(t.hour), 22.sp, p.muted, FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            }
        }
        DText(t.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), 18.sp, p.muted)

        if (r.gradual) {
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DText("Volume rising", 13.sp, p.muted)
                DText("${(r.volume * 100).toInt()}%", 13.sp, p.muted)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.surface2)) {
                Box(Modifier.fillMaxWidth(r.volume.coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
            }
        }
        Spacer(Modifier.height(10.dp))
        DText(r.soundName, 13.sp, p.muted)

        Spacer(Modifier.weight(1f))
        if (r.canSnooze) {
            BigButton("Snooze ${r.snoozeMinutes} min", Modifier.fillMaxWidth(), primary = false) { AlarmService.snooze() }
            Spacer(Modifier.height(12.dp))
        }
        BigButton(if (isTimer) "Stop" else "Dismiss", Modifier.fillMaxWidth()) { AlarmService.stop() }
        Spacer(Modifier.width(1.dp))
    }
}
