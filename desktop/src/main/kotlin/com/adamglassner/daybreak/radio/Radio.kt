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

/** What the SEEK button spins to. */
enum class SeekMode(val label: String, val tag: String? = null, val nearMe: Boolean = false) {
    ANYWHERE("ANYWHERE"),
    NEAR_ME("MY COUNTRY", nearMe = true),
    POP("POP", "pop"),
    ROCK("ROCK", "rock"),
    HITS("HITS", "hits"),
    HIPHOP("HIP-HOP", "hip-hop"),
    COUNTRY("COUNTRY", "country"),
    DANCE("DANCE", "dance"),
    JAZZ("JAZZ", "jazz"),
    CLASSICAL("CLASSICAL", "classical"),
    OLDIES("OLDIES", "oldies"),
    CHILL("CHILL", "chillout"),
    NEWS("NEWS", "news"),
    TALK("TALK", "talk");

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: ANYWHERE
    }
}

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

    /** Stations played lately, so SEEK doesn't land on them again. */
    private val recent = ArrayDeque<String>()

    /** How many more dead stations to skip past automatically after a SEEK or ◀ ▶. */
    @Volatile private var autoSkip = 0
    private var skipJob: Job? = null

    fun restoreLastStation() {
        val last = Store.settings.value.radioStation
        if (last != null && _state.value.station == null) _state.update { it.copy(station = last) }
    }

    fun play(station: Station, lineup: List<Station>? = null, skipDead: Int = 0) {
        autoSkip = skipDead
        skipJob?.cancel()
        recent.remove(station.uuid); recent.addLast(station.uuid)
        while (recent.size > 60) recent.removeFirst()
        _state.update {
            it.copy(
                station = station, lineup = lineup ?: it.lineup, playing = false, buffering = true,
                nowPlaying = null, error = null,
            )
        }
        engine?.start(station)
        if (skipDead > 0) {
            // A stream that never starts (stuck connecting) counts as dead too.
            skipJob = scope.launch {
                delay(12_000)
                val now = _state.value
                if (!now.playing && now.error == null && now.station?.uuid == station.uuid) {
                    onError("This station isn't playing right now. Try another.")
                }
            }
        }
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
            play(s.lineup[next], skipDead = 3)
        }
    }

    val seekMode: SeekMode get() = SeekMode.of(Store.settings.value.radioSeek)

    /** Chooses what SEEK spins to, and spins. */
    fun seek(mode: SeekMode) {
        Store.updateSettings { it.copy(radioSeek = mode.name) }
        seek()
    }

    /**
     * "Spin the globe": a random, reasonably popular station in the chosen style,
     * skipping ones played lately. Dead streams are skipped automatically.
     */
    fun seek() {
        val mode = seekMode
        scope.launch {
            _state.update { it.copy(tuning = true, error = null) }
            val list = runCatching {
                RadioBrowser.randomStations(mode.tag, if (mode.nearMe) homeCountry() else null)
            }.getOrDefault(emptyList())
            _state.update { it.copy(tuning = false) }
            val fresh = list.filter { it.uuid !in recent }.ifEmpty { list }
            if (fresh.isEmpty()) onError("Couldn't reach the station directory") else play(fresh.first(), fresh, skipDead = 5)
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

    fun onPlaying(playing: Boolean) {
        if (playing) { autoSkip = 0; skipJob?.cancel() }
        onPlayingState(playing)
    }

    private fun onPlayingState(playing: Boolean) = _state.update { it.copy(playing = playing, buffering = if (playing) false else it.buffering, error = if (playing) null else it.error) }
    fun onBuffering(buffering: Boolean) = _state.update { it.copy(buffering = buffering) }
    fun onError(message: String) {
        val s = _state.value
        if (autoSkip > 0 && s.lineup.size > 1) {
            // A station from SEEK or the dial that won't play: quietly move on to the next one.
            autoSkip--
            val left = autoSkip
            _state.update { it.copy(buffering = true, playing = false, error = null) }
            skipJob?.cancel()
            skipJob = scope.launch {
                delay(400)
                val now = _state.value
                val i = now.lineup.indexOfFirst { it.uuid == now.station?.uuid }
                play(now.lineup[(i + 1 + now.lineup.size) % now.lineup.size], skipDead = left)
            }
            return
        }
        autoSkip = 0
        _state.update { it.copy(error = message, playing = false, buffering = false) }
    }
    fun onNowPlaying(title: String?) = _state.update {
        val t = title?.trim()?.takeIf { s -> s.isNotEmpty() && !s.equals(it.station?.name, ignoreCase = true) }
        it.copy(nowPlaying = t)
    }
}
