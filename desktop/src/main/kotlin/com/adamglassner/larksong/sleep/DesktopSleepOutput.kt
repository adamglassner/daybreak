package com.adamglassner.larksong.sleep

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread

/** Plays sleep sounds through the default sound device on its own thread. */
class DesktopSleepOutput : SleepOutput {
    @Volatile private var worker: Thread? = null
    @Volatile private var generation = 0
    @Volatile private var running = -1

    @Synchronized
    override fun start() {
        if (worker?.isAlive == true && running == generation) return // already playing; SleepSounds swaps the sound in place
        val my = ++generation
        running = my
        worker = thread(isDaemon = true, name = "larksong-sleep") {
            try {
                val rate = 44100f
                val format = AudioFormat(rate, 16, 2, true, false)
                val line = AudioSystem.getSourceDataLine(format)
                line.open(format, (rate * 4 / 4).toInt()) // ~250 ms
                line.start()
                val frames = 2048
                val buf = ShortArray(frames * 2)
                val bytes = ByteArray(buf.size * 2)
                while (my == generation && SleepSounds.render(buf, frames)) {
                    for (i in buf.indices) {
                        val v = buf[i].toInt()
                        bytes[2 * i] = (v and 0xFF).toByte()
                        bytes[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    line.write(bytes, 0, bytes.size)
                }
                if (my != generation) line.flush()
                line.stop()
                line.close()
            } catch (_: Exception) {
                // No sound device.
            } finally {
                SleepSounds.onOutputEnded()
            }
        }
    }

    @Synchronized
    override fun stop() {
        generation++
    }
}
