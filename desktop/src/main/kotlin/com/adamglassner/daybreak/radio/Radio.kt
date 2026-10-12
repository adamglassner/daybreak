package com.adamglassner.daybreak.radio

import com.adamglassner.daybreak.data.PRESET_COUNT
import com.adamglassner.daybreak.data.Station
import com.adamglassner.daybreak.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** The platform part: actually plays a stream. */
interface RadioEngine {
    fun start(station: Station)
    fun halt()
    fun setVolume(volume: Float)
}

data class RadioState(
    val station: Station? = null,
    val lineup: List<Station> = emptyList(),
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val nowPlaying: String? = null,
    val error: String? = null,
    val sleepAt: Long = 0L,
    val volume: Float = 0.8f,
    val tuning: Boolean = false,
)

/** The clock radio's tuner: what's on, the dial lineup, presets and the sleep timer. */
object Radio {
    var engine: RadioEngine? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(RadioState())
    val state: StateFlow<RadioState> = _state.asStateFlow()
    private var sleepJob: Job? = null

    fun restoreLastStation() {
        val last = Store.settings.value.radioStation
        if (last != null && _state.value.station == null) _state.update { it.copy(station = last) }
    }

    fun play(station: Station, lineup: List<Station>? = null) {
        _state.update {
            it.copy(
                station = station, lineup = lineup ?: it.lineup, playing = false, buffering = true,
                nowPlaying = null, error = null,
            )
        }
        engine?.start(station)
        Store.updateSettings { it.copy(radioStation = station) }
        scope.launch { RadioBrowser.reportClick(station.uuid) }
    }

    fun stop() {
        engine?.halt()
        sleepJob?.cancel()
        _state.update { it.copy(playing = false, buffering = false, sleepAt = 0L) }
    }

    fun toggle() {
        val s = _state.value
        if (s.playing || s.buffering) stop()
        else if (s.station != null) play(s.station) else seek()
    }

    /** Turn the dial: the next or previous station in the lineup. */
    fun step(delta: Int) {
        scope.launch {
            var s = _state.value
            if (s.lineup.isEmpty()) {
                _state.update { it.copy(tuning = true) }
                val code = s.station?.countryCode?.ifBlank { null } ?: homeCountry()
                val list = runCatching { RadioBrowser.topStations(code) }.getOrDefault(emptyList())
                _state.update { it.copy(lineup = list, tuning = false) }
                s = _state.value
            }
            if (s.lineup.isEmpty()) { onError("Couldn't reach the station directory"); return@launch }
            val i = s.lineup.indexOfFirst { it.uuid == s.station?.uuid }
            val next = if (i < 0) 0 else (i + delta + s.lineup.size) % s.lineup.size
            play(s.lineup[next])
        }
    }

    /** "Spin the globe": a random station from anywhere in the world. */
    fun seek() {
        scope.launch {
            _state.update { it.copy(tuning = true, error = null) }
            val list = runCatching { RadioBrowser.randomStations() }.getOrDefault(emptyList())
            _state.update { it.copy(tuning = false) }
            if (list.isEmpty()) onError("Couldn't reach the station directory") else play(list.first(), list)
        }
    }

    fun loadCountry(code: String) {
        scope.launch {
            _state.update { it.copy(tuning = true) }
            val list = runCatching { RadioBrowser.topStations(code) }.getOrDefault(emptyList())
            _state.update { it.copy(lineup = list, tuning = false) }
        }
    }

    fun setLineup(list: List<Station>) = _state.update { it.copy(lineup = list) }

    fun savePreset(slot: Int) {
        val st = _state.value.station ?: return
        Store.updateSettings { s ->
            val presets = s.radioPresets.toMutableList()
            while (presets.size < PRESET_COUNT) presets.add(null)
            presets[slot] = st
            s.copy(radioPresets = presets)
        }
    }

    fun playPreset(slot: Int): Boolean {
        val st = Store.settings.value.radioPresets.getOrNull(slot) ?: return false
        play(st)
        return true
    }

    /** Sleep timer: cycles off → 15 → 30 → 60 minutes → off. */
    fun cycleSleep() {
        val s = _state.value
        val left = if (s.sleepAt > 0) ((s.sleepAt - System.currentTimeMillis()) / 60_000L).toInt() + 1 else 0
        val next = when {
            s.sleepAt == 0L -> 15
            left <= 15 -> 30
            left <= 30 -> 60
            else -> 0
        }
        setSleep(next)
    }

    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _state.update { it.copy(sleepAt = 0L) }; return }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepAt = at) }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            stop()
        }
    }

    fun setVolume(v: Float) {
        _state.update { it.copy(volume = v.coerceIn(0f, 1f)) }
        engine?.setVolume(v.coerceIn(0f, 1f))
    }

    fun homeCountry(): String =
        Store.settings.value.radioCountry.ifBlank { Locale.getDefault().country.ifBlank { "US" } }

    // ---- called by the engine (any thread) ----

    fun onPlaying(playing: Boolean) = _state.update { it.copy(playing = playing, buffering = if (playing) false else it.buffering, error = if (playing) null else it.error) }
    fun onBuffering(buffering: Boolean) = _state.update { it.copy(buffering = buffering) }
    fun onError(message: String) = _state.update { it.copy(error = message, playing = false, buffering = false) }
    fun onNowPlaying(title: String?) = _state.update {
        val t = title?.trim()?.takeIf { s -> s.isNotEmpty() && !s.equals(it.station?.name, ignoreCase = true) }
        it.copy(nowPlaying = t)
    }
}
