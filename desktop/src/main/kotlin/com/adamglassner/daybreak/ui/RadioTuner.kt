package com.adamglassner.daybreak.ui

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
import com.adamglassner.daybreak.data.PRESET_COUNT
import com.adamglassner.daybreak.data.Station
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.radio.Country
import com.adamglassner.daybreak.radio.Radio
import com.adamglassner.daybreak.radio.RadioBrowser
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
    Canvas(Modifier.fillMaxWidth().height(46.dp).semantics { contentDescription = "Tuning dial" }) {
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
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .heightIn(min = if (big) 52.dp else 44.dp)
            .clip(shape)
            .background(if (lit) c(0xFF3A1A20) else c(0xFF2C2724))
            .border(BorderStroke(1.5.dp, if (lit) c(0xFFFF2D4F) else c(0xFF4A433E)), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        DText(
            label, if (big) 17.sp else 14.sp, if (lit) c(0xFFFF5A73) else c(0xFFE9DCC4), FontWeight.Bold,
            italic = true, letterSpacing = 1.sp, maxLines = 1, align = TextAlign.Center,
        )
    }
}

/** The tuner on the Clock tab (Clock radio style only). */
@Composable
fun TunerPanel(onBrowse: (() -> Unit)?, showVolume: Boolean, modifier: Modifier = Modifier.padding(horizontal = 16.dp)) {
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
        else -> "PRESS SEEK TO SPIN THE GLOBE"
    }

    LedWindow(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DText("WORLD RADIO", 12.sp, p.panelMuted, FontWeight.Bold, italic = true, letterSpacing = 1.2.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (r.playing) p.led else p.ledOff))
                Spacer(Modifier.weight(1f))
                if (onBrowse != null) LinkButton("Tune in", color = p.panelText, onClick = onBrowse)
            }
            Dial(fraction, r.tuning || r.buffering)
            DText(st?.name?.uppercase() ?: "NO STATION", 19.sp, p.led, FontWeight.Bold, italic = true, maxLines = 1)
            DText(status, 13.sp, if (r.error != null) c(0xFFFF8A9B) else p.panelText, FontWeight.Bold, maxLines = 1)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Key("◀", Modifier.weight(1f), description = "Previous station") { Radio.step(-1) }
                Key(if (r.playing || r.buffering) "STOP" else "PLAY", Modifier.weight(1.6f), lit = r.playing, big = true) { Radio.toggle() }
                Key("▶", Modifier.weight(1f), description = "Next station") { Radio.step(1) }
                Key("SEEK", Modifier.weight(1.2f), description = "Random station from anywhere") { Radio.seek() }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until PRESET_COUNT) {
                    val preset = settings.radioPresets.getOrNull(i)
                    val active = preset != null && preset.uuid == st?.uuid
                    Key(
                        "${i + 1}", Modifier.weight(1f), lit = active,
                        description = if (preset != null) "Preset ${i + 1}: ${preset.name}" else "Preset ${i + 1}, empty",
                        onLongClick = {
                            if (st != null) {
                                Radio.savePreset(i)
                                Notices.show("Saved ${st.name} to preset ${i + 1}")
                            }
                        },
                    ) {
                        if (!Radio.playPreset(i)) {
                            if (st != null) {
                                Radio.savePreset(i)
                                Notices.show("Saved ${st.name} to preset ${i + 1}")
                            } else {
                                Notices.show("Tune in to a station, then long-press a number to save it")
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val left = if (r.sleepAt > 0) ((r.sleepAt - now) / 60_000L).coerceAtLeast(0) + 1 else 0
                Key(if (r.sleepAt > 0) "SLEEP $left MIN" else "SLEEP OFF", lit = r.sleepAt > 0) { Radio.cycleSleep() }
                if (showVolume) {
                    DText("VOL", 12.sp, p.panelMuted, FontWeight.Bold, italic = true)
                    Slider(
                        value = r.volume,
                        onValueChange = { Radio.setVolume(it) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Radio volume" },
                        colors = SliderDefaults.colors(
                            thumbColor = p.panelText, activeTrackColor = p.led, inactiveTrackColor = c(0xFF3A3430),
                        ),
                    )
                } else {
                    DText("HOLD A NUMBER TO SAVE A PRESET", 11.sp, p.panelMuted, FontWeight.Bold, italic = true, maxLines = 1)
                }
            }
        }
    }
}

// ---------- browsing stations ----------

@Composable
fun StationsScreen(showHeader: Boolean = true, onBack: () -> Unit) {
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
        if (showHeader) BackHeader(if (db.f.caps) "TUNE IN" else "Tune in", onBack)
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
