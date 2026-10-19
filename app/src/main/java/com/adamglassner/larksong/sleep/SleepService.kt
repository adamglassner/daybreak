package com.adamglassner.larksong.sleep

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.adamglassner.larksong.LarksongApp
import com.adamglassner.larksong.MainActivity
import com.adamglassner.larksong.R
import com.adamglassner.larksong.data.Store
import com.adamglassner.larksong.ui.formatTime
import kotlin.concurrent.thread

class AndroidSleepOutput(private val ctx: Context) : SleepOutput {
    override fun start() {
        ContextCompat.startForegroundService(ctx, Intent(ctx, SleepService::class.java))
    }

    override fun stop() {
        ctx.stopService(Intent(ctx, SleepService::class.java))
    }
}

/** Plays sleep sounds in the background (screen off), with a notification to stop them. */
class SleepService : Service() {
    companion object {
        const val ACTION_STOP = "com.adamglassner.larksong.SLEEP_STOP"
        private const val NOTIF_ID = 78
        private const val RATE = 44100
    }

    @Volatile private var running = false
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        if (intent?.action == ACTION_STOP) {
            SleepSounds.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification(), type)
        if (!SleepSounds.state.value.playing) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker?.isAlive != true) startAudio()
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val st = SleepSounds.state.value
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 21, Intent(this, SleepService::class.java).setAction(ACTION_STOP), flags)
        val text = if (st.endsAt > 0) {
            val t = java.time.Instant.ofEpochMilli(st.endsAt).atZone(java.time.ZoneId.systemDefault())
            "Turns off at ${formatTime(t.hour, t.minute, Store.settings.value.use24h)}"
        } else "Playing until you stop it"
        return NotificationCompat.Builder(this, LarksongApp.CHANNEL_SLEEP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sleep sounds · ${st.sound.label}")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .build()
    }

    private fun startAudio() {
        running = true
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "larksong:sleep").apply { setReferenceCounted(false); acquire(13 * 60 * 60_000L) }
        worker = thread(name = "larksong-sleep") {
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build(),
                    )
                    .setBufferSizeInBytes(maxOf(minBuf * 2, RATE)) // ~250 ms
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                val frames = 2048
                val buf = ShortArray(frames * 2)
                while (running && SleepSounds.render(buf, frames)) {
                    track.write(buf, 0, buf.size)
                }
            } catch (_: Exception) {
                // No audio output available.
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
                SleepSounds.onOutputEnded()
                if (running) stopSelf() // the timer ran out
            }
        }
    }

    override fun onDestroy() {
        running = false
        runCatching { worker?.join(500) }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
