package com.adamglassner.larksong.radio

import com.adamglassner.larksong.data.Station
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

/**
 * Plays MP3 internet radio streams on Windows: follows redirects and playlists, decodes
 * with JLayer, and reads Shoutcast/Icecast "now playing" titles from the stream.
 */
class StreamRadioEngine : RadioEngine {
    @Volatile private var generation = 0
    @Volatile private var volume = 0.8f
    @Volatile private var openConn: HttpURLConnection? = null

    override fun start(station: Station) {
        val my = ++generation
        runCatching { openConn?.disconnect() }
        thread(isDaemon = true, name = "larksong-radio") { play(station.url, my) }
    }

    override fun halt() {
        generation++
        runCatching { openConn?.disconnect() }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
    }

    private fun connect(url: String, depth: Int = 0): Pair<HttpURLConnection, InputStream> {
        if (depth > 5) error("Too many redirects")
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Larksong/1.0")
        conn.setRequestProperty("Icy-MetaData", "1")
        val code = conn.responseCode
        if (code in 300..399) {
            val loc = conn.getHeaderField("Location") ?: error("Bad redirect")
            conn.disconnect()
            return connect(URI(url).resolve(loc).toString(), depth + 1)
        }
        if (code !in 200..299) error("HTTP $code")
        val type = (conn.contentType ?: "").lowercase()
        val lower = url.lowercase().substringBefore('?')
        if ("mpegurl" in type || "scpls" in type || "x-pls" in type || lower.endsWith(".m3u") || lower.endsWith(".pls")) {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val next = text.lineSequence()
                .map { it.trim().substringAfter('=', it.trim()).trim() }
                .firstOrNull { it.startsWith("http") } ?: error("Empty playlist")
            return connect(next, depth + 1)
        }
        val metaInt = conn.getHeaderFieldInt("icy-metaint", 0)
        val raw = BufferedInputStream(conn.inputStream, 64 * 1024)
        val stream = if (metaInt > 0) IcyStream(raw, metaInt) { Radio.onNowPlaying(it) } else raw
        return conn to stream
    }

    private fun play(url: String, my: Int) {
        var line: SourceDataLine? = null
        try {
            Radio.onBuffering(true)
            val (conn, stream) = connect(url)
            openConn = conn
            if (my != generation) { conn.disconnect(); return }
            val bitstream = Bitstream(stream)
            val decoder = Decoder()
            var bytes = ByteArray(0)
            var frames = 0
            while (my == generation) {
                val header = bitstream.readFrame() ?: break
                val sb = try {
                    decoder.decodeFrame(header, bitstream) as SampleBuffer
                } catch (e: Exception) {
                    bitstream.closeFrame(); continue
                }
                if (line == null) {
                    val format = AudioFormat(sb.sampleFrequency.toFloat(), 16, sb.channelCount, true, false)
                    line = AudioSystem.getSourceDataLine(format).apply {
                        open(format, (format.sampleRate * format.channels * 2).toInt()) // ~1 s buffer
                        start()
                    }
                }
                val n = sb.bufferLength
                if (bytes.size < n * 2) bytes = ByteArray(n * 2)
                val v = volume.let { it * it }
                val buf = sb.buffer
                for (i in 0 until n) {
                    val s = (buf[i] * v).toInt()
                    bytes[2 * i] = (s and 0xFF).toByte()
                    bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
                }
                line!!.write(bytes, 0, n * 2)
                bitstream.closeFrame()
                if (++frames == 8 && my == generation) Radio.onPlaying(true)
            }
            if (my == generation) Radio.onError("The station stopped sending audio")
        } catch (e: Exception) {
            if (my == generation) Radio.onError("This station isn't playing right now. Try another.")
        } finally {
            runCatching { line?.stop(); line?.flush(); line?.close() }
            runCatching { openConn?.disconnect() }
            if (my == generation) Radio.onPlaying(false)
        }
    }
}

/** Strips Icecast/Shoutcast metadata blocks out of the audio and reports StreamTitle. */
private class IcyStream(input: InputStream, private val metaInt: Int, private val onTitle: (String?) -> Unit) : FilterInputStream(input) {
    private var untilMeta = metaInt

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (untilMeta == 0) readMeta()
        val n = super.read(b, off, minOf(len, untilMeta))
        if (n > 0) untilMeta -= n
        return n
    }

    private fun readMeta() {
        val lenByte = `in`.read()
        if (lenByte < 0) return
        val size = lenByte * 16
        if (size > 0) {
            val meta = ByteArray(size)
            var got = 0
            while (got < size) {
                val r = `in`.read(meta, got, size - got)
                if (r < 0) break
                got += r
            }
            val text = String(meta, 0, got, Charsets.UTF_8)
            val title = Regex("StreamTitle='(.*?)';").find(text)?.groupValues?.get(1)
            onTitle(title)
        }
        untilMeta = metaInt
    }

    override fun skip(n: Long): Long {
        val buf = ByteArray(4096)
        var left = n
        while (left > 0) {
            val r = read(buf, 0, minOf(left, buf.size.toLong()).toInt())
            if (r <= 0) break
            left -= r
        }
        return n - left
    }

    override fun markSupported() = false
}
