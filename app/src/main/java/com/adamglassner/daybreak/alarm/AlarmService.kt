package com.adamglassner.daybreak.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.adamglassner.daybreak.DaybreakApp
import com.adamglassner.daybreak.R
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.ui.formatTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

class AlarmService : Service() {

    data class Ringing(
        val kind: String,
        val id: Int,
        val title: String,
        val soundName: String,
        val volume: Float,
        val canSnooze: Boolean,
        val snoozeMinutes: Int,
        val gradual: Boolean,
    )

    companion object {
        const val ACTION_RING = "com.adamglassner.daybreak.action.RING"
        const val ACTION_STOP = "com.adamglassner.daybreak.action.STOP"
        const val ACTION_SNOOZE = "com.adamglassner.daybreak.action.SNOOZE"

        const val EXTRA_SOUND = "previewSound"
        const val EXTRA_SOUND_NAME = "previewSoundName"
        const val EXTRA_GRADUAL = "previewGradual"
        const val EXTRA_RAMP = "previewRamp"
        const val EXTRA_VIBRATE = "previewVibrate"
        const val EXTRA_LABEL = "previewLabel"

        private const val NOTIF_ID = 77
        private const val AUTO_STOP_MS = 10 * 60_000L

        private val _ringing = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP))
        }

        fun snooze(ctx: Context) {
            ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_SNOOZE))
        }
    }

    private var player: MediaPlayer? = null
    private var radioPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var currentVolume = 1f
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rampStart = 0L
    private var rampMs = 0L
    private var focus: android.media.AudioFocusRequest? = null

    private fun takeFocus() {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs).setOnAudioFocusChangeListener { }.build()
        runCatching { getSystemService(android.media.AudioManager::class.java).requestAudioFocus(req) }
        focus = req
    }

    private fun dropFocus() {
        focus?.let { f -> runCatching { getSystemService(android.media.AudioManager::class.java).abandonAudioFocusRequest(f) } }
        focus = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        when (intent?.action) {
            ACTION_RING -> ring(intent)
            ACTION_SNOOZE -> {
                val r = _ringing.value
                if (r != null && r.canSnooze) Store.alarm(r.id)?.let { Scheduler.snooze(this, it) }
                finish()
            }
            ACTION_STOP -> finish()
            else -> if (_ringing.value == null) finish()
        }
        return START_NOT_STICKY
    }

    private fun ring(intent: Intent) {
        stopPlayback()
        if (intent.getStringExtra(Scheduler.EXTRA_KIND) != Scheduler.KIND_PREVIEW) com.adamglassner.daybreak.sleep.SleepSounds.stop()
        val kind = intent.getStringExtra(Scheduler.EXTRA_KIND) ?: Scheduler.KIND_ALARM
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, 0)
        val settings = Store.settings.value
        val alarm = Store.alarm(id)

        val title: String
        val soundUri: String?
        val soundName: String
        val gradual: Boolean
        val rampSec: Int
        val vibrate: Boolean
        val canSnooze: Boolean
        when (kind) {
            Scheduler.KIND_TIMER -> {
                title = "Time's up"
                soundUri = settings.timerSoundUri; soundName = settings.timerSoundName
                gradual = false; rampSec = 0; vibrate = true; canSnooze = false
            }
            Scheduler.KIND_PREVIEW -> {
                title = intent.getStringExtra(EXTRA_LABEL)?.ifBlank { null } ?: "Alarm preview"
                soundUri = intent.getStringExtra(EXTRA_SOUND)
                soundName = intent.getStringExtra(EXTRA_SOUND_NAME) ?: "Default alarm"
                gradual = intent.getBooleanExtra(EXTRA_GRADUAL, false)
                rampSec = intent.getIntExtra(EXTRA_RAMP, 30)
                vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
                canSnooze = false
            }
            else -> {
                title = alarm?.label?.ifBlank { null } ?: "Alarm"
                soundUri = alarm?.soundUri; soundName = alarm?.soundName ?: "Default alarm"
                gradual = alarm?.gradual ?: false; rampSec = alarm?.rampSeconds ?: 60
                vibrate = alarm?.vibrate ?: true; canSnooze = alarm != null
            }
        }

        val r = Ringing(kind, id, title, soundName, if (gradual) 0f else 1f, canSnooze, alarm?.snoozeMinutes ?: 10, gradual)
        _ringing.value = r

        val n = buildNotification(r)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "daybreak:ring").apply { acquire(AUTO_STOP_MS + 5_000) }

        takeFocus()
        currentVolume = if (gradual) 0.02f else 1f
        val radioUrl = RadioAlarm.url(soundUri)
        if (radioUrl != null) {
            com.adamglassner.daybreak.radio.Radio.stop() // the alarm's own player takes over
            startRadio(radioUrl)
        } else {
            startPlayer(soundUri, currentVolume)
        }
        if (vibrate) startVibration()
        if (gradual) {
            rampStart = System.currentTimeMillis()
            rampMs = rampSec.coerceAtLeast(5) * 1000L
            handler.post(rampTick)
        }
        handler.postDelayed(autoStop, AUTO_STOP_MS)

        try {
            startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Background start blocked: the full-screen notification takes over.
        }
    }

    private val rampTick = object : Runnable {
        override fun run() {
            val frac = ((System.currentTimeMillis() - rampStart).toFloat() / rampMs).coerceIn(0f, 1f)
            val vol = (frac * frac).coerceAtLeast(0.02f)
            currentVolume = vol
            runCatching { player?.setVolume(vol, vol) }
            runCatching { radioPlayer?.volume = vol }
            _ringing.value = _ringing.value?.copy(volume = frac)
            if (frac < 1f) handler.postDelayed(this, 200)
        }
    }

    private val autoStop = Runnable { finish() }

    /**
     * Wake to the radio: plays the station on the alarm volume. If it can't start within
     * [RadioAlarm.START_TIMEOUT_MS] (no internet, station down) or stops later, the alarm
     * falls back to the regular alarm sound so it still wakes you.
     */
    private fun startRadio(url: String) {
        val attrs = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_ALARM)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        try {
            val p = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
            p.setAudioAttributes(attrs, false)
            p.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) = fallbackToTone()
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_ENDED) fallbackToTone()
                }
            })
            p.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            p.volume = currentVolume
            p.prepare()
            p.play()
            radioPlayer = p
            handler.postDelayed(radioWatchdog, RadioAlarm.START_TIMEOUT_MS)
        } catch (_: Exception) {
            fallbackToTone()
        }
    }

    private val radioWatchdog = Runnable { if (radioPlayer?.isPlaying != true) fallbackToTone() }

    private fun fallbackToTone() {
        handler.removeCallbacks(radioWatchdog)
        radioPlayer?.let { runCatching { it.release() } }
        radioPlayer = null
        if (_ringing.value != null && player == null) startPlayer(null, currentVolume)
    }

    private fun startPlayer(uriString: String?, startVolume: Float) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val candidates = listOfNotNull(
            uriString?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.setVolume(startVolume, startVolume)
                mp.start()
                player = mp
                return
            } catch (_: Exception) {
                // try the next sound
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        runCatching { v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0)) }
    }

    private fun buildNotification(r: Ringing): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val full = PendingIntent.getActivity(this, 10, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        val stop = PendingIntent.getService(this, 11, Intent(this, AlarmService::class.java).setAction(ACTION_STOP), flags)
        val snooze = PendingIntent.getService(this, 12, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE), flags)
        val now = LocalTime.now()
        val b = NotificationCompat.Builder(this, DaybreakApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.title)
            .setContentText(formatTime(now.hour, now.minute, Store.settings.value.use24h))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
        if (r.canSnooze) b.addAction(0, "Snooze ${r.snoozeMinutes} min", snooze)
        b.addAction(0, if (r.kind == Scheduler.KIND_TIMER) "Stop" else "Dismiss", stop)
        return b.build()
    }

    private fun stopPlayback() {
        handler.removeCallbacks(rampTick)
        handler.removeCallbacks(autoStop)
        handler.removeCallbacks(radioWatchdog)
        runCatching { radioPlayer?.release() }
        radioPlayer = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        dropFocus()
    }

    private fun finish() {
        stopPlayback()
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        _ringing.value = null
        super.onDestroy()
    }
}
