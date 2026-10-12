package com.adamglassner.daybreak.radio

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.adamglassner.daybreak.MainActivity
import com.adamglassner.daybreak.data.Station

/** Plays the radio in the background, with lock-screen and notification controls. */
class RadioService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true, // handle audio focus: pauses while an alarm rings
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        val open = PendingIntent.getActivity(
            this, 5, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** Connects the tuner to [RadioService] through a MediaController. */
class AndroidRadioEngine(context: Context) : RadioEngine {
    private val ctx = context.applicationContext
    private var controller: MediaController? = null
    private var connecting = false
    private var pending: ((MediaController) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = Radio.onPlaying(isPlaying)
        override fun onPlaybackStateChanged(playbackState: Int) {
            Radio.onBuffering(playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) Radio.onPlaying(false)
        }
        override fun onPlayerError(error: PlaybackException) = Radio.onError("This station isn't playing right now. Try another.")
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = Radio.onNowPlaying(mediaMetadata.title?.toString())
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { block(it); return }
        pending = block
        if (connecting) return
        connecting = true
        val token = SessionToken(ctx, ComponentName(ctx, RadioService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) {
                Radio.onError("The radio couldn't start")
            } else {
                controller = c
                c.addListener(listener)
                pending?.invoke(c)
            }
            pending = null
        }, ContextCompat.getMainExecutor(ctx))
    }

    override fun start(station: Station) = withController { c ->
        val meta = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(listOf(station.country, station.tags.split(',').firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
            .apply { if (station.favicon.startsWith("http")) setArtworkUri(Uri.parse(station.favicon)) }
            .build()
        val item = MediaItem.Builder()
            .setUri(station.url)
            .setMediaId(station.uuid.ifBlank { station.url })
            .setMediaMetadata(meta)
            .apply { if (station.url.contains(".m3u8")) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8) }
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    override fun halt() {
        controller?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    override fun setVolume(volume: Float) {
        controller?.volume = volume
    }
}
