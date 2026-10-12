package com.adamglassner.daybreak.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.daybreak.Platform
import com.adamglassner.daybreak.data.City
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.Settings
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.data.ThemeMode
import com.adamglassner.daybreak.weather.Weather
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: Settings, hasLocation: Boolean, onRequestLocation: () -> Unit, onBack: () -> Unit) {
    val db = LocalDb.current
    val p = db.p
    var addingCity by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        BackHeader(if (db.f.caps) "SETTINGS" else "Settings", onBack)

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Style")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClockStyle.entries.forEach { s ->
                    StyleCard(s, settings.style == s, Modifier.weight(1f)) { Store.updateSettings { it.copy(style = s) } }
                }
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "System").forEach { (m, label) ->
                    Chip(label, settings.mode == m, Modifier.weight(1f)) { Store.updateSettings { it.copy(mode = m) } }
                }
            }
        }

        DbCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp), padding = 0.dp) {
            Column {
                SettingRow("24-hour time", if (settings.use24h) "18:30" else "6:30 PM") {
                    DbSwitch(settings.use24h, { v -> Store.updateSettings { it.copy(use24h = v) } }, "24-hour time")
                }
                Divider()
                SettingRow("Show seconds") {
                    DbSwitch(settings.showSeconds, { v -> Store.updateSettings { it.copy(showSeconds = v) } }, "Show seconds")
                }
                Divider()
                SettingRow("Temperature") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("°F", !settings.celsius, Modifier.size(56.dp, 44.dp)) { Store.updateSettings { it.copy(celsius = false) } }
                        Chip("°C", settings.celsius, Modifier.size(56.dp, 44.dp)) { Store.updateSettings { it.copy(celsius = true) } }
                    }
                }
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Weather location")
            DbCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                Column {
                    SettingRow(
                        "Use my approximate location",
                        when {
                            !settings.useGps -> "Off"
                            hasLocation -> "Based on your internet connection"
                            else -> "Needs location permission. Tap to allow."
                        },
                        onClick = if (settings.useGps && !hasLocation) onRequestLocation else null,
                    ) {
                        DbSwitch(settings.useGps, { v ->
                            Store.updateSettings { it.copy(useGps = v) }
                            if (v && !hasLocation) onRequestLocation()
                        }, "Use my location")
                    }
                    settings.cities.forEach { city ->
                        Divider()
                        SettingRow(city.name, city.region) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
                                    .clickable { Store.updateSettings { it.copy(cities = it.cities - city) } }
                                    .semantics { contentDescription = "Remove ${city.name}" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(DbIcons.Close, null, tint = p.muted, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    Divider()
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        LinkButton("Add a city", DbIcons.Plus) { addingCity = true }
                    }
                }
            }
            DText("Tap the weather card on the Clock tab to switch between places.", 13.sp, p.muted)
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Windows")
            DbCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                Column {
                    var autostart by remember { mutableStateOf(Platform.isAutostartOn()) }
                    SettingRow(
                        "Start with Windows",
                        if (Platform.canAutostart) "Opens quietly in the tray when you sign in, so alarms are ready"
                        else "Available in the installed app",
                    ) {
                        if (Platform.canAutostart) DbSwitch(autostart, { v ->
                            Platform.setAutostart(v)
                            autostart = Platform.isAutostartOn()
                        }, "Start with Windows")
                    }
                    Divider()
                    SettingRow(
                        "Alarms ring while Daybreak is running",
                        "Closing the window keeps Daybreak in the tray. Quit from the tray icon. Alarms can't wake a sleeping PC.",
                    )
                }
            }
        }

        DText(
            "Daybreak 1.1 for Windows · Weather by Open-Meteo · Radio by radio-browser.info",
            12.sp, p.muted, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), align = TextAlign.Center,
        )
    }

    if (addingCity) {
        CitySearchDialog(onDismiss = { addingCity = false }) { city ->
            addingCity = false
            Store.updateSettings { s -> if (s.cities.any { it.name == city.name && it.region == city.region }) s else s.copy(cities = s.cities + city) }
        }
    }
}

@Composable
fun StyleCard(style: ClockStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalDb.current.p
    val name = when (style) {
        ClockStyle.STOCK -> "Stock"
        ClockStyle.COLORS -> "Colors"
        ClockStyle.RETRO -> "Retro"
        ClockStyle.RADIO -> "Radio"
        ClockStyle.TERMINAL -> "Terminal"
        ClockStyle.CLASSIC -> "Classic Daybreak"
    }
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
        val swatchShape = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxWidth().height(60.dp).clip(swatchShape).background(
                when (style) {
                    ClockStyle.STOCK -> c(0xFF1C1F26)
                    ClockStyle.COLORS -> c(0xFFFF7A59)
                    ClockStyle.RETRO -> c(0xFF2B231B)
                    ClockStyle.RADIO -> c(0xFF0A0606)
                    ClockStyle.TERMINAL -> c(0xFF000000)
                    ClockStyle.CLASSIC -> c(0xFFC0C0C0)
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            when (style) {
                ClockStyle.STOCK -> DText("10:09", 20.sp, c(0xFFECEEF2), FontWeight.SemiBold, family = FigtreeFont)
                ClockStyle.COLORS -> DText("10:09", 20.sp, c(0xFF1A0B3A), FontWeight.SemiBold, family = FredokaFont)
                ClockStyle.RETRO -> DText("10:09", 26.sp, c(0xFFF2B544), family = BebasFont, letterSpacing = 1.sp)
                ClockStyle.TERMINAL -> DText(">10:09_", 24.sp, c(0xFF33FF66), family = TerminalFont)
                ClockStyle.CLASSIC -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(12.dp).background(Brush.horizontalGradient(listOf(c(0xFF000080), c(0xFF1084D0)))))
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        DText("10:09", 20.sp, c(0xFF000000), FontWeight.Bold, family = PixelFont)
                    }
                }
                ClockStyle.RADIO -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(10.dp).background(c(0xFF6A4428)))
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        SevenSeg(" 8:54", 26.dp, c(0xFFFF2D4F), c(0xFF2B0B12))
                    }
                }
            }
        }
        DText(name, 14.sp, p.text, FontWeight.Bold, maxLines = 2, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun CitySearchDialog(onDismiss: () -> Unit, onPick: (City) -> Unit) {
    val p = LocalDb.current.p
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>?>(null) }
    var searching by remember { mutableStateOf(false) }
    fun search() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = Weather.searchCities(query)
            searching = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { DText("Add a city", 20.sp, weight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        query, { query = it }, Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { DText("City name", 15.sp, p.muted) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinkButton(if (searching) "Searching…" else "Search") { search() }
                val r = results
                when {
                    r == null -> {}
                    r.isEmpty() -> DText("No places found. Check the spelling, or you may be offline.", 14.sp, p.muted)
                    else -> LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(r) { city ->
                            Column(Modifier.fillMaxWidth().clickable { onPick(city) }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                                DText(city.name, 16.sp)
                                DText(city.region, 12.sp, p.muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { DText("Cancel", 16.sp, p.muted, FontWeight.Bold) } },
    )
}
