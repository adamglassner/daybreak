package com.adamglassner.larksong.ui

import androidx.compose.foundation.verticalScroll
import com.adamglassner.larksong.radio.SeekMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.larksong.data.PRESET_COUNT
import com.adamglassner.larksong.data.Station
import com.adamglassner.larksong.data.Store
import com.adamglassner.larksong.radio.Country
import com.adamglassner.larksong.radio.Radio
import com.adamglassner.larksong.radio.RadioBrowser
import kotlinx.coroutines.launch
import java.util.Locale

private fun stationLine(st: Station): String {
    val parts = mutableListOf<String>()
    if (st.country.isNotBlank()) parts += st.country
    if (st.codec.isNotBlank()) parts += st.codec + if (st.bitrate > 0) " ${st.bitrate}K" else ""
    return parts.joinToString(" · ").uppercase()
}

/** The tuning dial: an FM scale with a red needle, like the clock radio's. */
@Composable
private fun Dial(fraction: Float, moving: Boolean) {
    val p = LocalDb.current.p
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = RadioFont, fontSize = 13.sp, color = p.panelText)
    Canvas(Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = "Tuning dial" }) {
        val left = 10.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val w = right - left
        listOf("88", "92", "96", "100", "104", "108").forEachIndexed { i, label ->
            val x = left + w * i / 5f
            val layout = measurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, 0f))
        }
        val tickTop = 20.dp.toPx()
        for (i in 0..40) {
            val x = left + w * i / 40f
            val major = i % 8 == 0
            drawLine(if (major) p.panelText else p.panelMuted, Offset(x, tickTop + if (major) 0f else 5.dp.toPx()), Offset(x, tickTop + 12.dp.toPx()), 1.5.dp.toPx())
        }
        drawRect(c(0xFF5A1620), Offset(left, size.height - 6.dp.toPx()), androidx.compose.ui.geometry.Size(w, 4.dp.toPx()))
        val nx = left + w * fraction.coerceIn(0f, 1f)
        drawLine(if (moving) p.led.copy(alpha = 0.6f) else p.led, Offset(nx, tickTop - 4.dp.toPx()), Offset(nx, size.height), 3.dp.toPx())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    label: String,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
    big: Boolean = false,
    small: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .heightIn(min = if (big) 52.dp else if (small) 40.dp else 44.dp)
            .clip(shape)
            .background(if (lit) c(0xFF3A1A20) else c(0xFF2C2724))
            .border(BorderStroke(1.5.dp, if (lit) c(0xFFFF2D4F) else c(0xFF4A433E)), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = if (small) 12.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(icon, null, tint = if (lit) c(0xFFFF5A73) else c(0xFFE9DCC4), modifier = Modifier.size(22.dp))
        } else DText(
            label, if (big) 17.sp else if (small) 12.sp else 14.sp, if (lit) c(0xFFFF5A73) else c(0xFFE9DCC4), FontWeight.Bold,
            italic = true, letterSpacing = 1.sp, maxLines = 1, align = TextAlign.Center,
        )
    }
}

/** The tuner on the Clock tab (Clock radio style only). */
@Composable
fun TunerPanel(onBrowse: () -> Unit, showVolume: Boolean) {
    val p = LocalDb.current.p
    val r by Radio.state.collectAsState()
    val settings by Store.settings.collectAsState()
    val now by rememberNow(15_000L)
    val st = r.station
    val index = r.lineup.indexOfFirst { it.uuid == st?.uuid }
    val fraction = when {
        index >= 0 -> (index + 0.5f) / r.lineup.size
        st != null -> ((st.uuid.hashCode().toLong() and 0xFFFF) / 65535f) * 0.9f + 0.05f
        else -> 0.5f
    }
    val status = when {
        r.error != null -> r.error!!.uppercase()
        r.tuning -> "TUNING…"
        r.buffering -> "TUNING IN…"
        r.nowPlaying != null -> "♪ " + r.nowPlaying
        st != null && r.playing -> stationLine(st)
        st != null -> stationLine(st).ifBlank { "READY" } + " · PRESS PLAY"
        else -> "PICK A STYLE BELOW TO SPIN THE GLOBE"
    }
    val mode = SeekMode.of(settings.radioSeek)
    val country = Radio.homeCountry()
    val countryName = Locale("", country).getDisplayCountry(Locale.US).ifBlank { country }
    var choosingCountry by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    fun seekLabel(m: SeekMode) = if (m == SeekMode.NEAR_ME) countryName.uppercase() else m.label

    LedWindow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (r.playing) p.led else p.ledOff))
                Spacer(Modifier.width(8.dp))
                DText("WORLD RADIO", 12.sp, p.panelMuted, FontWeight.Bold, italic = true, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                if (onBrowse != null) LinkButton("Stations", color = p.panelText, onClick = onBrowse)
            }
            Dial(fraction, r.tuning || r.buffering)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DText(st?.name?.uppercase() ?: "NO STATION", 18.sp, p.led, FontWeight.Bold, italic = true, maxLines = 1)
                DText(status, 12.sp, if (r.error != null) c(0xFFFF8A9B) else p.panelText, FontWeight.Bold, maxLines = 1)
            }

            // Main controls: tune down / play / tune up, then SEEK and the two menus.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Key("", Modifier.weight(1f), icon = DbIcons.Back, description = "Previous station") { Radio.step(-1) }
                Key(if (r.playing || r.buffering) "STOP" else "PLAY", Modifier.weight(1.8f), lit = r.playing, big = true) { Radio.toggle() }
                Key("", Modifier.weight(1f), icon = DbIcons.Chevron, description = "Next station") { Radio.step(1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Key("SEEK", Modifier.weight(1f), description = "Random ${seekLabel(mode).lowercase()} station") { Radio.seek() }
                Key("PRESETS", Modifier.weight(1f), description = "Your saved stations") { menu = "presets" }
                Key("MORE ▾", Modifier.weight(1f), lit = r.sleepAt > 0, description = "Country, style, sleep timer and all stations") { menu = "more" }
            }
            val sleepLeft = if (r.sleepAt > 0) ((r.sleepAt - now) / 60_000L).coerceAtLeast(0) + 1 else 0
            DText(
                "SEEK: ${seekLabel(mode)}" + (if (sleepLeft > 0) " · SLEEP IN $sleepLeft MIN" else ""),
                11.sp, p.panelMuted, FontWeight.Bold, italic = true, letterSpacing = 1.sp, maxLines = 1,
            )
            if (showVolume) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DText("VOL", 12.sp, p.panelMuted, FontWeight.Bold, italic = true)
                    Slider(
                        value = r.volume,
                        onValueChange = { Radio.setVolume(it) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Radio volume" },
                        colors = SliderDefaults.colors(
                            thumbColor = p.panelText, activeTrackColor = p.led, inactiveTrackColor = c(0xFF3A3430),
                        ),
                    )
                }
            }
        }
    }

    when (menu) {
        "presets" -> PresetsMenu(st, settings.radioPresets, onDismiss = { menu = null })
        "more" -> MoreMenu(
            countryName = countryName,
            mode = mode,
            sleepAt = r.sleepAt,
            onCountry = { menu = null; choosingCountry = true },
            onBrowse = onBrowse?.let { b -> { menu = null; b() } },
            onDismiss = { menu = null },
        )
    }

    if (choosingCountry) {
        CountryDialog(onDismiss = { choosingCountry = false }) { picked ->
            choosingCountry = false
            Radio.chooseCountry(picked.code)
        }
    }
}

/** The radio's presets: tap a station to play it, or save the current station in a slot. */
@Composable
private fun PresetsMenu(current: Station?, presets: List<Station?>, onDismiss: () -> Unit) {
    val p = LocalDb.current.p
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.panel,
        title = { DText("PRESETS", 18.sp, p.panelText, FontWeight.Bold, italic = true, letterSpacing = 1.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until PRESET_COUNT) {
                    val preset = presets.getOrNull(i)
                    val playingThis = preset != null && preset.uuid == current?.uuid
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (playingThis) c(0xFF3A1A20) else c(0xFF1C1816))
                            .clickable(enabled = preset != null) { Radio.playPreset(i); onDismiss() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DText("${i + 1}", 18.sp, if (playingThis) p.led else p.panelText, FontWeight.Bold, italic = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            DText(preset?.name ?: "Empty", 15.sp, if (preset != null) p.panelText else p.panelMuted, FontWeight.Bold, maxLines = 1)
                            if (preset != null) DText(stationLine(preset), 11.sp, p.panelMuted, maxLines = 1)
                        }
                        if (current != null && !playingThis) {
                            LinkButton("Save here", color = p.led) {
                                Radio.savePreset(i)
                                Notices.show("Saved ${current.name} to preset ${i + 1}")
                            }
                        }
                    }
                }
                if (current == null) DText("Tune in to a station to save it here.", 13.sp, p.panelMuted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { DText("Done", 16.sp, p.led, FontWeight.Bold) } },
    )
}

/** Radio settings: country, what SEEK spins to, the sleep timer, and the full station list. */
@Composable
private fun MoreMenu(
    countryName: String,
    mode: SeekMode,
    sleepAt: Long,
    onCountry: () -> Unit,
    onBrowse: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val p = LocalDb.current.p
    val now by rememberNow(15_000L)
    val sleepLeft = if (sleepAt > 0) ((sleepAt - now) / 60_000L).coerceAtLeast(0) + 1 else 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.panel,
        title = { DText("RADIO", 18.sp, p.panelText, FontWeight.Bold, italic = true, letterSpacing = 1.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DText("COUNTRY", 11.sp, p.panelMuted, FontWeight.Bold, italic = true, letterSpacing = 1.sp)
                    Key("${countryName.uppercase()} ▾", Modifier.fillMaxWidth(), lit = mode == SeekMode.NEAR_ME, description = "Choose a country") { onCountry() }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DText("SEEK PLAYS", 11.sp, p.panelMuted, FontWeight.Bold, italic = true, letterSpacing = 1.sp)
                    SeekMode.entries.map { m -> m }.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { m ->
                                val label = if (m == SeekMode.NEAR_ME) countryName.uppercase() else m.label
                                Key(label, Modifier.weight(1f), lit = m == mode, small = true, description = "Random ${label.lowercase()} station") {
                                    Radio.seek(m); onDismiss()
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DText(if (sleepLeft > 0) "SLEEP TIMER · OFF IN $sleepLeft MIN" else "SLEEP TIMER", 11.sp, p.panelMuted, FontWeight.Bold, italic = true, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 15, 30, 60, 90).forEach { m ->
                            val on = if (m == 0) sleepAt == 0L else sleepAt > 0 && sleepLeft in (m - 14)..m
                            Key(if (m == 0) "OFF" else "$m", Modifier.weight(1f), lit = on, small = true,
                                description = if (m == 0) "Sleep timer off" else "Turn off in $m minutes") { Radio.setSleep(m) }
                        }
                    }
                }
                if (onBrowse != null) {
                    Key("ALL STATIONS…", Modifier.fillMaxWidth(), description = "Browse and search all stations") { onBrowse() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { DText("Done", 16.sp, p.led, FontWeight.Bold) } },
    )
}

// ---------- browsing stations ----------

@Composable
fun StationsScreen(onBack: () -> Unit) {
    val db = LocalDb.current
    val p = db.p
    val scope = rememberCoroutineScope()
    val settings by Store.settings.collectAsState()
    val radio by Radio.state.collectAsState()
    val country = Radio.homeCountry()
    var countryName by remember { mutableStateOf(Locale("", country).getDisplayCountry(Locale.US).ifBlank { country }) }
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf<String?>(null) }
    var stations by remember { mutableStateOf<List<Station>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var choosingCountry by remember { mutableStateOf(false) }

    fun load(block: suspend () -> List<Station>) {
        stations = null; failed = false
        scope.launch {
            val list = runCatching { block() }.getOrNull()
            failed = list == null
            stations = list ?: emptyList()
        }
    }

    LaunchedEffect(settings.radioCountry) {
        searched = null
        countryName = Locale("", Radio.homeCountry()).getDisplayCountry(Locale.US).ifBlank { Radio.homeCountry() }
        load { RadioBrowser.topStations(Radio.homeCountry()) }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searched = q
        load { RadioBrowser.searchByName(q) }
    }

    Column(Modifier.fillMaxSize()) {
        BackHeader(if (db.f.caps) "TUNE IN" else "Tune in", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { DText("Station, city or genre (jazz, news…)", 15.sp, p.muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = p.text, unfocusedTextColor = p.text,
                    focusedBorderColor = p.accent, unfocusedBorderColor = p.border, cursorColor = p.accent,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Search", false, Modifier.weight(1f)) { search() }
                Chip(countryName, searched == null, Modifier.weight(1.4f)) { choosingCountry = true }
                Chip("Spin the globe", false, Modifier.weight(1.4f)) { Radio.seek(); onBack() }
            }
            SectionLabel(if (searched != null) "Results for \"$searched\"" else "Top stations · $countryName")
        }
        val list = stations
        when {
            list == null -> DText("Tuning in…", 15.sp, p.muted, modifier = Modifier.padding(24.dp))
            failed -> DText("Couldn't reach the station directory. Check your connection and try again.", 15.sp, p.muted, modifier = Modifier.padding(24.dp))
            list.isEmpty() -> DText("No stations found.", 15.sp, p.muted, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(list, key = { it.uuid + it.url }) { s ->
                    val current = radio.station?.uuid == s.uuid
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (current) p.accentSoft else Color.Transparent)
                            .clickable { Radio.play(s, list); onBack() }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(p.panel),
                            contentAlignment = Alignment.Center,
                        ) {
                            DText(s.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪", 18.sp, p.led, FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            DText(s.name, 16.sp, p.text, FontWeight.Bold, maxLines = 1)
                            val tags = s.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(", ")
                            DText(listOf(stationLine(s), tags).filter { it.isNotBlank() }.joinToString(" · "), 12.sp, p.muted, maxLines = 1)
                        }
                        if (current && radio.playing) DText("ON AIR", 11.sp, p.link, FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (choosingCountry) {
        CountryDialog(onDismiss = { choosingCountry = false }) { c ->
            choosingCountry = false
            Store.updateSettings { it.copy(radioCountry = c.code) }
        }
    }
}

@Composable
private fun CountryDialog(onDismiss: () -> Unit, onPick: (Country) -> Unit) {
    val p = LocalDb.current.p
    var all by remember { mutableStateOf<List<Country>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val list = runCatching { RadioBrowser.countries() }.getOrNull()
        failed = list == null
        all = list ?: emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { DText("Choose a country", 20.sp, weight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { DText("Search countries", 15.sp, p.muted) }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = all
                when {
                    list == null -> DText("Loading…", 15.sp, p.muted)
                    failed -> DText("Couldn't load the country list.", 15.sp, p.muted)
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list.filter { it.name.contains(filter.trim(), ignoreCase = true) }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DText(c.name, 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                DText("${c.stations}", 12.sp, p.muted)
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

// ---------- wake to the radio ----------

/** Chooses the station an alarm wakes you with: your presets and last station, popular ones, or a search. */
@Composable
fun RadioStationDialog(onDismiss: () -> Unit, onPick: (Station) -> Unit) {
    val p = LocalDb.current.p
    val settings by Store.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Station>?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun load(block: suspend () -> List<Station>) {
        results = null; failed = false
        scope.launch {
            val list = runCatching { block() }.getOrNull()
            failed = list == null
            results = list ?: emptyList()
        }
    }
    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searched = q
        load { RadioBrowser.searchByName(q, 40) }
    }
    LaunchedEffect(Unit) { load { RadioBrowser.topStations(Radio.homeCountry(), 40) } }
    val mine = (listOfNotNull(settings.radioStation) + settings.radioPresets.filterNotNull())
        .filter { it.url.isNotBlank() }.distinctBy { it.url }

    @Composable
    fun StationPick(s: Station) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(s) }.padding(vertical = 9.dp, horizontal = 6.dp),
        ) {
            DText(s.name, 15.sp, p.text, FontWeight.Bold, maxLines = 1)
            val line = stationLine(s)
            if (line.isNotBlank()) DText(line, 12.sp, p.muted, maxLines = 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { DText("Wake to the radio", 20.sp, weight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DText("If the station can't play when the alarm goes off, the regular alarm sound plays instead.", 13.sp, p.muted)
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { DText("Search: station, city or style", 14.sp, p.muted) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    trailingIcon = { LinkButton("Search") { search() } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = p.text, unfocusedTextColor = p.text,
                        focusedBorderColor = p.accent, unfocusedBorderColor = p.border, cursorColor = p.accent,
                    ),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                    if (mine.isNotEmpty() && searched == null) {
                        item { DText("Your stations", 13.sp, p.muted, FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                        items(mine) { StationPick(it) }
                    }
                    item {
                        DText(if (searched != null) "Results for \"$searched\"" else "Popular near you", 13.sp, p.muted, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                    val list = results
                    when {
                        list == null -> item { DText("Loading…", 14.sp, p.muted, modifier = Modifier.padding(vertical = 8.dp)) }
                        failed -> item { DText("Couldn't reach the station directory. Check your connection.", 14.sp, p.muted, modifier = Modifier.padding(vertical = 8.dp)) }
                        list.isEmpty() -> item { DText("No stations found.", 14.sp, p.muted, modifier = Modifier.padding(vertical = 8.dp)) }
                        else -> items(list) { StationPick(it) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { DText("Cancel", 16.sp, p.muted, FontWeight.Bold) } },
    )
}
