package com.adamglassner.daybreak.alarm

import com.adamglassner.daybreak.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Plays a ringing alarm or timer and exposes its state to the ring window. */
object AlarmService {

    data class Ringing(
        val kind: String,
        val id: Int,
        val title: String,
        val soundName: String,
        val volume: Float,
        val canSnooze: Boolean,
        val snoozeMinutes: Int,
        val gradual: Boolean,
        val startedAt: Long = System.currentTimeMillis(),
    )

    data class Preview(val soundPath: String?, val soundName: String, val gradual: Boolean, val rampSeconds: Int, val label: String)

    private const val AUTO_STOP_MS = 10 * 60_000L

    private val _ringing = MutableStateFlow<Ringing?>(null)
    val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var player: LoopPlayer? = null
    private var job: Job? = null
    @Volatile private var volume = 1f
    @Volatile private var usingRadio = false
    private var radioVolumeBefore = -1f

    @Synchronized
    fun ring(kind: String, id: Int, preview: Preview? = null) {
        stopPlayback()
        com.adamglassner.daybreak.radio.Radio.stop() // the alarm takes over from the radio
        if (preview == null) com.adamglassner.daybreak.sleep.SleepSounds.stop()
        val settings = Store.settings.value
        val alarm = Store.alarm(id)
        val title: String
        val sound: String?
        val soundName: String
        val gradual: Boolean
        val rampSec: Int
        val canSnooze: Boolean
        when {
            kind == Scheduler.KIND_TIMER -> {
                title = "Time's up"; sound = settings.timerSoundUri; soundName = settings.timerSoundName
                gradual = false; rampSec = 0; canSnooze = false
            }
            preview != null -> {
                title = preview.label.ifBlank { "Alarm preview" }; sound = preview.soundPath; soundName = preview.soundName
                gradual = preview.gradual; rampSec = preview.rampSeconds; canSnooze = false
            }
            else -> {
                title = alarm?.label?.ifBlank { null } ?: "Alarm"; sound = alarm?.soundUri; soundName = alarm?.soundName ?: "Default alarm"
                gradual = alarm?.gradual ?: false; rampSec = alarm?.rampSeconds ?: 60; canSnooze = alarm != null
            }
        }
        volume = if (gradual) 0.03f else 1f
        _ringing.value = Ringing(kind, id, title, soundName, if (gradual) 0f else 1f, canSnooze, alarm?.snoozeMinutes ?: 10, gradual)

        val radioUrl = RadioAlarm.url(sound)
        job = scope.launch {
            fun startTone(path: String?) {
                val p = LoopPlayer(Audio.load(path)) { volume }
                synchronized(this@AlarmService) {
                    if (!isActive) return
                    player = p
                    p.start()
                }
            }
            if (radioUrl != null) {
                // Wake to the radio, through the radio player at the alarm's volume.
                val radio = com.adamglassner.daybreak.radio.Radio
                radioVolumeBefore = radio.state.value.volume
                radio.setVolume(volume)
                radio.play(com.adamglassner.daybreak.data.Station(uuid = "", name = soundName.removePrefix("Radio: "), url = radioUrl))
                usingRadio = true
            } else {
                startTone(sound)
            }
            val start = System.currentTimeMillis()
            val rampMs = rampSec.coerceAtLeast(5) * 1000L
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                if (gradual) {
                    val frac = (elapsed.toFloat() / rampMs).coerceIn(0f, 1f)
                    volume = maxOf(0.03f, frac)
                    _ringing.value = _ringing.value?.copy(volume = frac)
                }
                if (usingRadio) {
                    val r = com.adamglassner.daybreak.radio.Radio.state.value
                    val failed = r.error != null || (!r.playing && elapsed > RadioAlarm.START_TIMEOUT_MS)
                    if (failed) {
                        // The station didn't start (no internet, station down): ring normally instead.
                        stopRadio()
                        startTone(null)
                    } else {
                        com.adamglassner.daybreak.radio.Radio.setVolume(volume)
                    }
                }
                if (elapsed > AUTO_STOP_MS) { stop(); break }
                delay(200)
            }
        }
    }

    fun snooze() {
        val r = _ringing.value
        if (r != null && r.canSnooze) Store.alarm(r.id)?.let { Scheduler.snooze(it) }
        stop()
    }

    @Synchronized
    fun stop() {
        stopPlayback()
        _ringing.value = null
    }

    @Synchronized
    private fun stopPlayback() {
        job?.cancel()
        job = null
        player?.stop()
        player = null
        stopRadio()
    }

    private fun stopRadio() {
        if (!usingRadio) return
        usingRadio = false
        com.adamglassner.daybreak.radio.Radio.stop()
        if (radioVolumeBefore >= 0f) com.adamglassner.daybreak.radio.Radio.setVolume(radioVolumeBefore)
        radioVolumeBefore = -1f
    }
}
