package com.adamglassner.larksong.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.larksong.alarm.Scheduler
import com.adamglassner.larksong.data.ClockStyle
import com.adamglassner.larksong.data.Settings
import com.adamglassner.larksong.data.Store
import com.adamglassner.larksong.weather.Weather
import com.adamglassner.larksong.weather.WeatherNow
import com.adamglassner.larksong.weather.WeatherPlace
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private fun localNow(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

@Composable
fun LiveAnalogClock(size: androidx.compose.ui.unit.Dp) {
    val now by rememberNow(50L)
    AnalogClock(localNow(now).toLocalTime(), size)
}

fun zoneCity(zone: String): String = zone.substringAfterLast('/').replace('_', ' ')

fun zoneOffsetLabel(zone: String): String {
    val now = ZonedDateTime.now()
    val other = runCatching { now.withZoneSameInstant(ZoneId.of(zone)) }.getOrNull() ?: return ""
    val diff = other.offset.totalSeconds - now.offset.totalSeconds
    if (diff == 0) return "Same time"
    val h = diff / 3600
    val m = Math.abs(diff % 3600) / 60
    val sign = if (diff > 0) "+" else "−"
    val body = if (m == 0) "${Math.abs(h)} hr" else "${Math.abs(h)}:%02d hr".format(m)
    val day = when {
        other.toLocalDate().isAfter(now.toLocalDate()) -> ", tomorrow"
        other.toLocalDate().isBefore(now.toLocalDate()) -> ", yesterday"
        else -> ""
    }
    return "$sign$body$day"
}

fun zoneTime(zone: String, use24: Boolean): String {
    val t = runCatching { ZonedDateTime.now(ZoneId.of(zone)) }.getOrNull() ?: return "--:--"
    return formatTime(t.hour, t.minute, use24)
}

fun nextAlarmMillis(): Long? =
    Store.alarms.value.filter { it.enabled }.minOfOrNull { Scheduler.nextTrigger(it) }

fun weatherIcon(code: Int): ImageVector = when (code) {
    0, 1 -> DbIcons.WxClear
    2 -> DbIcons.WxPartly
    3 -> DbIcons.WxCloud
    45, 48 -> DbIcons.WxFog
    71, 73, 75, 77, 85, 86 -> DbIcons.WxSnow
    95, 96, 99 -> DbIcons.WxStorm
    in 51..67, in 80..82 -> DbIcons.WxRain
    else -> DbIcons.WxCloud
}

// ---------- weather state ----------

class WeatherUi(val places: List<WeatherPlace>, val index: Int, val data: WeatherNow?, val loading: Boolean, val needsPermission: Boolean)

@Composable
fun rememberWeather(settings: Settings, hasLocation: Boolean, index: Int): WeatherUi {
    val places = buildList {
        if (settings.useGps) add(WeatherPlace("My location", true, null))
        settings.cities.forEach { add(WeatherPlace(it.name, false, it)) }
    }
    val i = if (places.isEmpty()) 0 else index % places.size
    var data by remember { mutableStateOf<WeatherNow?>(null) }
    var loading by remember { mutableStateOf(false) }
    val place = places.getOrNull(i)
    val needsPermission = place?.gps == true && !hasLocation
    LaunchedEffect(place, settings.celsius, hasLocation) {
        data = null
        if (place == null || needsPermission) return@LaunchedEffect
        while (true) {
            loading = true
            data = loadWeather(place, settings.celsius) ?: data
            loading = false
            delay(30 * 60_000L)
        }
    }
    return WeatherUi(places, i, data, loading, needsPermission)
}

private suspend fun loadWeather(place: WeatherPlace, celsius: Boolean): WeatherNow? {
    if (!place.gps) {
        val c = place.city ?: return null
        return Weather.fetch(c.lat, c.lon, celsius, c.name, false)
    }
    val loc = Weather.approxLocation() ?: return null
    return Weather.fetch(loc.lat, loc.lon, celsius, loc.city.ifBlank { "My location" }, true)
}

// ---------- screen ----------

@Composable
fun ClockScreen(
    settings: Settings,
    hasLocation: Boolean,
    onRequestLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlarms: () -> Unit,
    onOpenStations: () -> Unit = {},
    showVolume: Boolean = false,
) {
    val db = LocalDb.current
    var weatherIndex by remember { mutableIntStateOf(0) }
    val weather = rememberWeather(settings, hasLocation, weatherIndex)
    var addingZone by remember { mutableStateOf(false) }
    var removingZone by remember { mutableStateOf<String?>(null) }

    if (db.style == ClockStyle.RADIO) {
        RadioClock(settings, weather, onNextWeather = { weatherIndex++ }, onRequestLocation, onOpenSettings, onOpenAlarms,
            onOpenStations, showVolume,
            onAddZone = { addingZone = true }, onRemoveZone = { removingZone = it })
    } else {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Header(onOpenSettings)
            when (db.style) {
                ClockStyle.RETRO -> RetroHero(settings)
                else -> StandardHero(settings)
            }
            Spacer(Modifier.height(14.dp))
            WeatherCard(weather, settings, onNext = { weatherIndex++ }, onRequestLocation, onOpenSettings)
            WorldClocks(settings, onAdd = { addingZone = true }, onRemove = { removingZone = it })
        }
    }

    if (addingZone) {
        ZonePickerDialog(onDismiss = { addingZone = false }) { zone ->
            addingZone = false
            Store.updateSettings { s -> if (zone in s.worldZones) s else s.copy(worldZones = s.worldZones + zone) }
        }
    }
    removingZone?.let { zone ->
        AlertDialog(
            onDismissRequest = { removingZone = null },
            containerColor = db.p.surface,
            title = { DText("Remove ${zoneCity(zone)}?", 18.sp, weight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    Store.updateSettings { s -> s.copy(worldZones = s.worldZones - zone) }
                    removingZone = null
                }) { DText("Remove", 16.sp, db.p.link, FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { removingZone = null }) { DText("Cancel", 16.sp, db.p.muted, FontWeight.Bold) } },
        )
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    val db = LocalDb.current
    val now by rememberNow(30_000L)
    val date = localNow(now).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            when (db.style) {
                ClockStyle.COLORS -> DText(date, 22.sp, weight = FontWeight.SemiBold, family = db.f.display)
                ClockStyle.RETRO -> DText(date.uppercase(), 14.sp, db.p.muted, letterSpacing = 1.5.sp)
                else -> {
                    DText("CLOCK", 13.sp, db.p.muted, letterSpacing = 1.sp)
                    DText(date, 17.sp, weight = FontWeight.SemiBold)
                }
            }
        }
        IconCircle(DbIcons.Settings, "Settings", onOpenSettings)
    }
}

@Composable
private fun StandardHero(settings: Settings) {
    val db = LocalDb.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LiveAnalogClock(220.dp)
        Spacer(Modifier.height(8.dp))
        DigitalReadout(settings, 58)
    }
}

@Composable
private fun DigitalReadout(settings: Settings, sizeSp: Int) {
    val db = LocalDb.current
    val now by rememberNow(250L)
    val t = localNow(now)
    Row(verticalAlignment = Alignment.Bottom) {
        DText(clockDigits(t.hour, t.minute, settings.use24h), sizeSp.sp, weight = FontWeight.SemiBold, family = db.f.display, letterSpacing = (-0.5).sp)
        if (settings.showSeconds) {
            DText(":%02d".format(t.second), (sizeSp / 2).sp, if (db.style == ClockStyle.COLORS) db.p.warm else db.p.accent,
                FontWeight.SemiBold, family = db.f.display, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (!settings.use24h) {
            DText(" " + ampm(t.hour), 18.sp, db.p.muted, FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun RetroHero(settings: Settings) {
    val db = LocalDb.current
    val now by rememberNow(250L)
    val t = localNow(now)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LiveAnalogClock(180.dp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val h = if (settings.use24h) t.hour else (if (t.hour % 12 == 0) 12 else t.hour % 12)
            FlipTile("%02d".format(h), true)
            FlipTile("%02d".format(t.minute), true)
            if (settings.showSeconds) FlipTile("%02d".format(t.second), false)
            if (!settings.use24h) DText(ampm(t.hour), 22.sp, db.p.accent, family = BebasFont, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun WeatherCard(w: WeatherUi, settings: Settings, onNext: () -> Unit, onRequestLocation: () -> Unit, onOpenSettings: () -> Unit) {
    val db = LocalDb.current
    val p = db.p
    val colorful = db.style == ClockStyle.COLORS
    val bg = if (colorful) p.warm else p.surface
    val fg = if (colorful) p.onWarm else p.text
    val sub = if (colorful) p.onWarm else p.muted
    DbCard(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        color = bg,
        onClick = when {
            w.places.isEmpty() -> onOpenSettings
            w.needsPermission -> onRequestLocation
            w.places.size > 1 -> onNext
            else -> null
        },
    ) {
        when {
            w.places.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DbIcons.WxPartly, null, tint = fg, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(14.dp))
                DText("Add a city in Settings to see the weather", 15.sp, fg)
            }
            w.needsPermission -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DbIcons.Pin, null, tint = fg, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    DText("Weather for where you are", 16.sp, fg, FontWeight.Bold)
                    DText("Tap to allow location access", 13.sp, sub)
                }
            }
            w.data == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DbIcons.WxPartly, null, tint = fg, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(14.dp))
                DText(if (w.loading) "Getting the weather…" else "Weather unavailable right now", 15.sp, fg)
            }
            else -> {
                val d = w.data
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(weatherIcon(d.code), null, tint = if (colorful) fg else p.warm, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (d.gps) Icon(DbIcons.Pin, null, tint = sub, modifier = Modifier.size(12.dp).padding(end = 2.dp))
                            DText(d.place, 13.sp, sub, FontWeight.Medium, maxLines = 1)
                            if (w.places.size > 1) DText("  ·  ${w.index + 1}/${w.places.size}", 13.sp, sub)
                        }
                        DText(Weather.describe(d.code), 17.sp, fg, FontWeight.Bold)
                        DText("H ${d.high}°  L ${d.low}°", 13.sp, sub)
                    }
                    DText("${d.temp}°", 42.sp, fg, FontWeight.SemiBold, family = db.f.display)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorldClocks(settings: Settings, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val db = LocalDb.current
    val p = db.p
    val now by rememberNow(15_000L)
    key(now) { WorldClocksBody(settings, onAdd, onRemove) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorldClocksBody(settings: Settings, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val db = LocalDb.current
    val p = db.p
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (db.style == ClockStyle.RETRO) SectionLabel("World clocks", Modifier.weight(1f))
        else DText("World clocks", if (db.style == ClockStyle.COLORS) 18.sp else 15.sp, weight = FontWeight.SemiBold, family = db.f.display, modifier = Modifier.weight(1f))
        LinkButton("Add city", DbIcons.Plus, onClick = onAdd)
    }
    if (settings.worldZones.isEmpty()) {
        DText("Add a city to see its time here.", 14.sp, p.muted, modifier = Modifier.padding(horizontal = 24.dp))
        return
    }
    if (db.style == ClockStyle.COLORS) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            settings.worldZones.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { i, zone ->
                        val color = p.cards[(row * 2 + i) % p.cards.size]
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(color)
                                .combinedClickable(onClick = {}, onLongClick = { onRemove(zone) })
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Column {
                                DText(zoneCity(zone), 14.sp, p.onCard, FontWeight.Bold, maxLines = 1)
                                DText(zoneTime(zone, settings.use24h), 26.sp, p.onCard, FontWeight.SemiBold, family = db.f.display, maxLines = 1)
                                DText(zoneOffsetLabel(zone), 12.sp, p.onCard, FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else if (db.style == ClockStyle.RETRO) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            settings.worldZones.forEach { zone ->
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onRemove(zone) }).padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        DText(zoneCity(zone).uppercase(), 15.sp, weight = FontWeight.Bold)
                        DText(zoneOffsetLabel(zone), 12.sp, p.muted)
                    }
                    DText(zoneTime(zone, settings.use24h), 34.sp, p.accent, family = BebasFont)
                }
                Divider()
            }
        }
    } else {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.worldZones.forEach { zone ->
                Box(Modifier.fillMaxWidth()) {
                    DbCard(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onRemove(zone) }), padding = 0.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                DText(zoneCity(zone), 16.sp, weight = FontWeight.SemiBold)
                                DText(zoneOffsetLabel(zone), 13.sp, p.muted)
                            }
                            DText(zoneTime(zone, settings.use24h), 24.sp, weight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
    DText("Long-press a city to remove it", 12.sp, p.muted, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
}

// ---------- Clock radio ----------

@Composable
private fun RadioClock(
    settings: Settings,
    weather: WeatherUi,
    onNextWeather: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlarms: () -> Unit,
    onOpenStations: () -> Unit,
    showVolume: Boolean,
    onAddZone: () -> Unit,
    onRemoveZone: (String) -> Unit,
) {
    val db = LocalDb.current
    val p = db.p
    val now by rememberNow(250L)
    val t = localNow(now)
    val alarms by Store.alarms.collectAsState()
    val next = remember(alarms, t.minute) { nextAlarmMillis() }
    val nextText = next?.let { val n = localNow(it); formatTime(n.hour, n.minute, settings.use24h) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            WoodGrille(Modifier.matchParentSize())
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(p.bg).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    DText(t.format(DateTimeFormatter.ofPattern("EEE, MMM d")).uppercase(), 15.sp, p.text, FontWeight.Bold, italic = true, letterSpacing = 1.sp)
                }
                Spacer(Modifier.weight(1f))
                IconCircle(DbIcons.Settings, "Settings", onOpenSettings)
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(2.dp).background(p.stripe))

        LedWindow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics {
            contentDescription = "Time ${formatTime(t.hour, t.minute, settings.use24h)}"
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!settings.use24h) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Indicator("AM", t.hour < 12)
                        Indicator("PM", t.hour >= 12)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                val hh = if (settings.use24h) "%02d".format(t.hour) else "%2d".format(if (t.hour % 12 == 0) 12 else t.hour % 12)
                Row(verticalAlignment = Alignment.Bottom) {
                    SevenSeg("$hh:%02d".format(t.minute), 84.dp, p.led, p.ledOff, colonOn = t.second % 2 == 0)
                    if (settings.showSeconds) {
                        Spacer(Modifier.width(6.dp))
                        SevenSeg("%02d".format(t.second), 40.dp, p.led, p.ledOff)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            DText("LARKSONG", 12.sp, p.muted, FontWeight.Bold, italic = true, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
            DText(if (nextText != null) "WAKE $nextText" else "WAKE OFF", 12.sp, p.muted, FontWeight.Bold, italic = true, letterSpacing = 1.2.sp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (nextText != null) p.led else p.ledOff))
        }

        Spacer(Modifier.height(4.dp))
        TunerPanel(onOpenStations, showVolume)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveAnalogClock(132.dp)
            LedWindow(
                Modifier.weight(1f).heightIn(min = 132.dp).clickable {
                    when {
                        weather.places.isEmpty() -> onOpenSettings()
                        weather.needsPermission -> onRequestLocation()
                        else -> onNextWeather()
                    }
                },
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val d = weather.data
                    DText(
                        (if (d != null) "WEATHER · ${d.place}" else "WEATHER").uppercase(),
                        11.sp, p.panelMuted, FontWeight.Bold, italic = true, letterSpacing = 1.sp, maxLines = 1,
                    )
                    when {
                        weather.places.isEmpty() -> DText("ADD A CITY IN SETTINGS", 15.sp, p.panelText, FontWeight.Bold)
                        weather.needsPermission -> DText("TAP TO ALLOW LOCATION", 15.sp, p.panelText, FontWeight.Bold)
                        d == null -> DText(if (weather.loading) "TUNING IN…" else "NO SIGNAL", 15.sp, p.panelText, FontWeight.Bold)
                        else -> {
                            Row(verticalAlignment = Alignment.Bottom) {
                                SevenSeg(d.temp.toString().padStart(2), 40.dp, p.led, p.ledOff)
                                DText("°", 26.sp, p.led, FontWeight.Bold)
                            }
                            DText(Weather.describe(d.code).uppercase(), 15.sp, p.panelText, FontWeight.Bold, maxLines = 1)
                            DText("HI ${d.high}°  ·  LO ${d.low}°", 13.sp, p.panelText, FontWeight.Bold)
                        }
                    }
                }
            }
        }

        LedWindow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DText("WORLD", 12.sp, p.panelMuted, FontWeight.Bold, italic = true, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                    LinkButton("Add city", color = p.panelText, onClick = onAddZone)
                }
                if (settings.worldZones.isEmpty()) DText("NO CITIES YET", 14.sp, p.panelText, FontWeight.Bold)
                RadioZones(settings, onRemoveZone)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            DText("SNOOZE", 14.sp, p.text, FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (p.dark) c(0xFF2C2724) else c(0xFF3A3430)).clickable(onClick = onOpenAlarms),
                contentAlignment = Alignment.Center,
            ) {
                DText(if (nextText != null) "NEXT ALARM · $nextText" else "NO ALARMS SET", 14.sp, c(0xFFC9BDAD), FontWeight.Bold, italic = true, letterSpacing = 1.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RadioZones(settings: Settings, onRemove: (String) -> Unit) {
    val p = LocalDb.current.p
    settings.worldZones.forEach { zone ->
        Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onRemove(zone) }).padding(vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                DText(zoneCity(zone).uppercase(), 17.sp, p.panelText, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                DText(zoneOffsetLabel(zone).uppercase(), 12.sp, p.panelMuted, FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                DText(zoneTime(zone, settings.use24h), 17.sp, p.led, FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c(0xFF5A1620)))
        }
    }
}

@Composable
private fun Indicator(label: String, lit: Boolean) {
    val p = LocalDb.current.p
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DText(label, 12.sp, p.panelMuted, FontWeight.Bold)
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (lit) p.led else p.ledOff))
    }
}

// ---------- add a world clock ----------

private val ALL_ZONES: List<String> by lazy {
    ZoneId.getAvailableZoneIds()
        .filter { z ->
            val region = z.substringBefore('/')
            '/' in z && region in setOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific") &&
                !z.startsWith("Etc/")
        }
        .sortedBy { zoneCity(it) }
}

@Composable
fun ZonePickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val p = LocalDb.current.p
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) ALL_ZONES.take(60)
        else ALL_ZONES.filter { zoneCity(it).lowercase().contains(q) || it.lowercase().contains(q) }.take(60)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { DText("Add a city", 20.sp, weight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { DText("Search, e.g. Paris", 15.sp, p.muted) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results) { z ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(z) }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                            DText(zoneCity(z), 16.sp)
                            DText(z.substringBefore('/').replace('_', ' ') + " · " + zoneOffsetLabel(z), 12.sp, p.muted)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { DText("Cancel", 16.sp, p.muted, FontWeight.Bold) } },
    )
}
