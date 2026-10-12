package com.adamglassner.daybreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.Settings
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.weather.Weather
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun local(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

@Composable
fun ClockPage(settings: Settings, ui: AppUi) {
    var addingZone by remember { mutableStateOf(false) }
    var weatherIndex by remember { mutableIntStateOf(0) }
    val weather = rememberWeather(settings, true, weatherIndex)
    val now by rememberNow(30_000L)

    ScrollPage {
        PageHeader("Clock", local(now).format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))) {
            FButton("Add city", icon = DbIcons.Plus) { addingZone = true }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth > 820.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1.1f)) { HeroCard(settings) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        WeatherPanel(weather, settings, onNext = { weatherIndex++ }, onOpenSettings = { ui.page = Page.SETTINGS })
                        WorldPanel(settings, onAdd = { addingZone = true })
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeroCard(settings)
                    WeatherPanel(weather, settings, onNext = { weatherIndex++ }, onOpenSettings = { ui.page = Page.SETTINGS })
                    WorldPanel(settings, onAdd = { addingZone = true })
                }
            }
        }
    }

    if (addingZone) {
        ZonePickerDialog(onDismiss = { addingZone = false }) { zone ->
            addingZone = false
            Store.updateSettings { s -> if (zone in s.worldZones) s else s.copy(worldZones = s.worldZones + zone) }
        }
    }
}

@Composable
private fun HeroCard(settings: Settings) {
    val db = LocalDb.current
    val p = db.p
    if (db.style == ClockStyle.RADIO) {
        RadioHero(settings)
        return
    }
    if (db.style == ClockStyle.TERMINAL) {
        FCard(Modifier.fillMaxWidth(), padding = 28.dp) { TerminalHero(settings.use24h, settings.showSeconds, 250.dp) }
        return
    }
    FCard(Modifier.fillMaxWidth(), padding = 28.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LiveAnalogClock(if (db.style == ClockStyle.RETRO) 240.dp else 280.dp)
            Spacer(Modifier.height(20.dp))
            val now by rememberNow(250L)
            val t = local(now)
            if (db.style == ClockStyle.RETRO) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val h = if (settings.use24h) t.hour else (if (t.hour % 12 == 0) 12 else t.hour % 12)
                    FlipTile("%02d".format(h), true)
                    FlipTile("%02d".format(t.minute), true)
                    if (settings.showSeconds) FlipTile("%02d".format(t.second), false)
                    if (!settings.use24h) DText(ampm(t.hour), 22.sp, p.accent, family = BebasFont, modifier = Modifier.padding(bottom = 6.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    DText(clockDigits(t.hour, t.minute, settings.use24h), 64.sp, p.text, FontWeight.SemiBold, family = db.f.display)
                    if (settings.showSeconds) {
                        DText(":%02d".format(t.second), 32.sp, if (db.style == ClockStyle.COLORS) p.warm else p.accent, FontWeight.SemiBold,
                            family = db.f.display, modifier = Modifier.padding(bottom = 9.dp))
                    }
                    if (!settings.use24h) DText(" " + ampm(t.hour), 20.sp, p.muted, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
            val zone = ZoneId.systemDefault()
            DText(zone.id.replace('_', ' '), 13.sp, p.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RadioHero(settings: Settings) {
    val p = LocalDb.current.p
    val now by rememberNow(250L)
    val t = local(now)
    val alarms by Store.alarms.collectAsState()
    val next = remember(alarms, t.minute) { nextAlarmMillis() }
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c(0xFF1F1A17))) {
        WoodGrille(Modifier.fillMaxWidth().height(96.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(2.dp).background(p.stripe))
        LedWindow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!settings.use24h) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Lamp("AM", t.hour < 12)
                        Lamp("PM", t.hour >= 12)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                val hh = if (settings.use24h) "%02d".format(t.hour) else "%2d".format(if (t.hour % 12 == 0) 12 else t.hour % 12)
                SevenSeg("$hh:%02d".format(t.minute), 104.dp, p.led, p.ledOff, colonOn = t.second % 2 == 0)
                if (settings.showSeconds) {
                    Spacer(Modifier.width(8.dp))
                    SevenSeg("%02d".format(t.second), 48.dp, p.led, p.ledOff, Modifier.align(Alignment.Bottom))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DText("DAYBREAK", 12.sp, c(0xFF9A8F84), FontWeight.Bold, italic = true, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
            val nt = next?.let { local(it) }
            DText(if (nt != null) "WAKE ${formatTime(nt.hour, nt.minute, settings.use24h)}" else "WAKE OFF", 12.sp, c(0xFF9A8F84), FontWeight.Bold, italic = true, letterSpacing = 1.2.sp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (nt != null) p.led else p.ledOff))
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
            LiveAnalogClock(150.dp)
        }
    }
}

@Composable
private fun Lamp(label: String, lit: Boolean) {
    val p = LocalDb.current.p
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DText(label, 12.sp, p.panelMuted, FontWeight.Bold)
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (lit) p.led else p.ledOff))
    }
}

@Composable
private fun WeatherPanel(w: WeatherUi, settings: Settings, onNext: () -> Unit, onOpenSettings: () -> Unit) {
    val db = LocalDb.current
    val p = db.p
    FCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DText("Weather", 16.sp, p.text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (w.places.size > 1) {
                ComboBox(w.places.getOrNull(w.index)?.name ?: "", w.places.map { it.name }) { i ->
                    val steps = (i - w.index + w.places.size) % w.places.size
                    repeat(steps) { onNext() }
                }
            } else {
                FButton("Locations", icon = DbIcons.Pin, onClick = onOpenSettings)
            }
        }
        Spacer(Modifier.height(14.dp))
        val d = w.data
        when {
            w.places.isEmpty() -> DText("Add a city in Settings to see the weather here.", 14.sp, p.muted)
            d == null -> DText(if (w.loading) "Getting the weather…" else "Weather unavailable right now.", 14.sp, p.muted)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(weatherIcon(d.code), null, tint = p.warm, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(18.dp))
                if (db.style == ClockStyle.RADIO) {
                    SevenSeg(d.temp.toString().padStart(2), 52.dp, p.led, p.ledOff)
                    DText("°", 30.sp, p.led, FontWeight.Bold)
                } else {
                    DText("${d.temp}°", 52.sp, p.text, FontWeight.SemiBold, family = db.f.display)
                }
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DText(Weather.describe(d.code), 16.sp, p.text, FontWeight.SemiBold)
                    DText("High ${d.high}° · Low ${d.low}°", 13.sp, p.muted)
                    DText(if (d.gps) "${d.place} (approximate)" else d.place, 13.sp, p.muted)
                }
            }
        }
    }
}

@Composable
private fun WorldPanel(settings: Settings, onAdd: () -> Unit) {
    val p = LocalDb.current.p
    val now by rememberNow(15_000L)
    FCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DText("World clock", 16.sp, p.text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            FIconButton(DbIcons.Plus, "Add city", onClick = onAdd)
        }
        if (settings.worldZones.isEmpty()) {
            DText("Add a city to compare times.", 14.sp, p.muted, modifier = Modifier.padding(start = 20.dp, bottom = 18.dp))
        }
        key(now) {
            settings.worldZones.forEach { zone -> ZoneRow(zone, settings.use24h) }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ZoneRow(zone: String, use24: Boolean) {
    val db = LocalDb.current
    val p = db.p
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().hoverable(src).background(if (hovered) hoverTint() else p.surface.copy(alpha = 0f))
            .heightIn(min = 56.dp).padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            DText(zoneCity(zone), 15.sp, p.text, FontWeight.SemiBold)
            DText(zoneOffsetLabel(zone), 12.sp, p.muted)
        }
        DText(
            zoneTime(zone, use24), 22.sp, if (db.style == ClockStyle.RADIO) p.led else p.text,
            if (db.style == ClockStyle.RETRO) FontWeight.Normal else FontWeight.Medium, family = db.f.display,
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(36.dp)) {
            if (hovered) FIconButton(DbIcons.Close, "Remove ${zoneCity(zone)}") {
                Store.updateSettings { s -> s.copy(worldZones = s.worldZones - zone) }
            }
        }
    }
}
