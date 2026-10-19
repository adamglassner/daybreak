package com.adamglassner.larksong.sleep

import com.adamglassner.larksong.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The platform part: runs an audio thread that pulls from [SleepSounds.render]. */
interface SleepOutput {
    fun start()
    fun stop()
}

data class SleepState(
    val playing: Boolean = false,
    val sound: SleepSound = SleepSound.RAIN,
    /** When the timer turns the sound off (epoch ms), or 0 to play until stopped. */
    val endsAt: Long = 0L,
)

/** Sleep sounds: rain, ocean, fan and more, with a sleep timer that fades out at the end. */
object SleepSounds {
    /** Timer choices, in minutes (0 = until you stop it). */
    val TIMERS = listOf(15, 30, 45, 60, 90, 120, 0)
    private const val FADE_IN_MS = 4_000L
    private const val FADE_OUT_MS = 90_000L

    var output: SleepOutput? = null

    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()

    @Volatile private var synth: SleepSynth? = null
    @Volatile private var startedAt = 0L

    fun restore() {
        _state.update { it.copy(sound = SleepSound.of(Store.settings.value.sleepSound)) }
    }

    /** Plays [sound] (switching from another one if playing), with the saved timer. */
    fun play(sound: SleepSound = _state.value.sound) {
        val minutes = Store.settings.value.sleepMinutes
        val now = System.currentTimeMillis()
        val wasPlaying = _state.value.playing
        synth = SleepSynth(sound)
        if (!wasPlaying) startedAt = now
        _state.update {
            it.copy(playing = true, sound = sound, endsAt = if (minutes > 0) now + minutes * 60_000L else 0L)
        }
        Store.updateSettings { it.copy(sleepSound = sound.name) }
        com.adamglassner.larksong.radio.Radio.stop()
        output?.start()
    }

    fun stop() {
        synth = null
        _state.update { it.copy(playing = false, endsAt = 0L) }
        output?.stop()
    }

    fun toggle() = if (_state.value.playing) stop() else play()

    /** Sets the timer; if a sound is playing, it counts from now. */
    fun setTimer(minutes: Int) {
        Store.updateSettings { it.copy(sleepMinutes = minutes) }
        if (_state.value.playing) {
            _state.update { it.copy(endsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L) }
            output?.start() // refreshes the "turns off at" note
        }
    }

    fun cycleTimer() {
        val cur = Store.settings.value.sleepMinutes
        val i = TIMERS.indexOf(cur)
        setTimer(TIMERS[(i + 1) % TIMERS.size])
    }

    fun setVolume(v: Float) = Store.updateSettings { it.copy(sleepVolume = v.coerceIn(0f, 1f)) }

    fun timerLabel(minutes: Int) = when {
        minutes <= 0 -> "No timer"
        minutes % 60 == 0 -> "${minutes / 60} hr"
        minutes > 60 -> "${minutes / 60} hr ${minutes % 60} min"
        else -> "$minutes min"
    }

    /**
     * Called by the audio thread: fills [buf] with [frames] stereo frames.
     * Returns false when there's nothing more to play (stopped, or the timer ran out).
     */
    fun render(buf: ShortArray, frames: Int): Boolean {
        val s = synth ?: return false
        val st = _state.value
        val now = System.currentTimeMillis()
        if (st.endsAt in 1..now) {
            synth = null
            _state.update { it.copy(playing = false, endsAt = 0L) }
            return false
        }
        val v = Store.settings.value.sleepVolume
        var g = v * v
        g *= ((now - startedAt).toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
        if (st.endsAt > 0) g *= ((st.endsAt - now).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
        s.fill(buf, frames, g)
        return true
    }

    /** The audio thread ended on its own (e.g. the system stopped it). */
    fun onOutputEnded() {
        if (_state.value.playing && synth == null) _state.update { it.copy(playing = false, endsAt = 0L) }
    }
}
