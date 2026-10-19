package com.adamglassner.larksong.alarm

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.sin

/** 16-bit PCM held in memory, looped while an alarm rings. */
class Pcm(val samples: ShortArray, val sampleRate: Float, val channels: Int)

object Audio {
    private const val MAX_SECONDS = 180

    /**
     * Loads a sound file (WAV, AIFF, AU or MP3). When [path] is null or unreadable, plays
     * Larksong's own alarm sound, and if even that can't load, a simple built-in beep.
     */
    fun load(path: String?): Pcm {
        if (path != null) {
            val f = File(path)
            if (f.isFile) {
                val pcm = runCatching { if (f.extension.equals("mp3", true)) decodeMp3(f) else decodeSampled(f) }.getOrNull()
                if (pcm != null && pcm.samples.isNotEmpty()) return pcm
            }
        }
        return larksongAlarm() ?: defaultTone()
    }

    @Volatile private var bundled: Pcm? = null

    /** The default alarm sound, packaged with the app. */
    fun larksongAlarm(): Pcm? {
        bundled?.let { return it }
        val pcm = runCatching {
            val res = Audio::class.java.getResourceAsStream("/sounds/larksong-alarm.wav") ?: return null
            AudioSystem.getAudioInputStream(java.io.BufferedInputStream(res)).use { decodeStream(it) }
        }.getOrNull()?.takeIf { it.samples.isNotEmpty() }
        bundled = pcm
        return pcm
    }

    fun canPlay(f: File) = f.extension.lowercase() in setOf("mp3", "wav", "aif", "aiff", "au")

    private fun decodeSampled(f: File): Pcm = AudioSystem.getAudioInputStream(f).use { decodeStream(it) }

    private fun decodeStream(src: javax.sound.sampled.AudioInputStream): Pcm {
        val base = src.format
        val target = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels,
            base.channels * 2, base.sampleRate, false,
        )
        val bytes = AudioSystem.getAudioInputStream(target, src).use { pcmIn ->
            pcmIn.readNBytes((target.sampleRate * target.channels * 2 * MAX_SECONDS).toInt())
        }
        val out = ShortArray(bytes.size / 2)
        for (i in out.indices) {
            out[i] = ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort()
        }
        return Pcm(out, target.sampleRate, target.channels)
    }

    private fun decodeMp3(f: File): Pcm {
        val bitstream = Bitstream(BufferedInputStream(FileInputStream(f)))
        val decoder = Decoder()
        var rate = 44100
        var channels = 2
        var buf = ShortArray(44100 * 2 * 10)
        var n = 0
        try {
            while (true) {
                val header = bitstream.readFrame() ?: break
                val sb = decoder.decodeFrame(header, bitstream) as SampleBuffer
                rate = sb.sampleFrequency
                channels = sb.channelCount
                val len = sb.bufferLength
                if (n + len > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, n + len))
                System.arraycopy(sb.buffer, 0, buf, n, len)
                n += len
                bitstream.closeFrame()
                if (n >= rate * channels * MAX_SECONDS) break
            }
        } finally {
            runCatching { bitstream.close() }
        }
        return Pcm(buf.copyOf(n), rate.toFloat(), channels)
    }

    /** A classic beep-beep-beep alarm, two seconds long. */
    fun defaultTone(): Pcm {
        val rate = 44100
        val out = ShortArray(rate * 2)
        val beepLen = (rate * 0.13).toInt()
        val gap = (rate * 0.09).toInt()
        for (b in 0 until 4) {
            val start = b * (beepLen + gap)
            for (i in 0 until beepLen) {
                val env = minOf(1.0, i / 300.0, (beepLen - i) / 300.0)
                val v = sin(2 * PI * 1046.5 * i / rate) * 0.55 + sin(2 * PI * 2093.0 * i / rate) * 0.15
                out[start + i] = (v * env * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return Pcm(out, rate.toFloat(), 1)
    }
}

/** Plays [pcm] on a loop on its own thread; [volume] is read continuously (0..1). */
class LoopPlayer(private val pcm: Pcm, private val volume: () -> Float) {
    @Volatile private var running = true
    private var line: SourceDataLine? = null

    private val thread = Thread({
        try {
            val format = AudioFormat(pcm.sampleRate, 16, pcm.channels, true, false)
            val l = AudioSystem.getSourceDataLine(format)
            line = l
            l.open(format, (pcm.sampleRate * pcm.channels * 2 / 5).toInt()) // ~200 ms buffer
            l.start()
            val chunk = 2048 * pcm.channels
            val bytes = ByteArray(chunk * 2)
            var pos = 0
            val s = pcm.samples
            while (running) {
                val v = volume().coerceIn(0f, 1f)
                val g = v * v // perceived loudness grows more evenly
                for (i in 0 until chunk) {
                    val sample = (s[pos] * g).toInt()
                    bytes[2 * i] = (sample and 0xFF).toByte()
                    bytes[2 * i + 1] = ((sample shr 8) and 0xFF).toByte()
                    pos++
                    if (pos >= s.size) pos = 0
                }
                l.write(bytes, 0, bytes.size)
            }
            l.stop()
            l.flush()
            l.close()
        } catch (_: Exception) {
            // No audio device: the ring window still shows.
        }
    }, "larksong-sound")

    fun start() { thread.isDaemon = true; thread.start() }

    fun stop() {
        running = false
        runCatching { line?.stop() }
    }
}
