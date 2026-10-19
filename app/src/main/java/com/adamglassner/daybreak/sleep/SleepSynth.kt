package com.adamglassner.daybreak.sleep

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** The sleep sounds. All of them are generated as they play, so nothing needs downloading. */
enum class SleepSound(val label: String) {
    RAIN("Rain"),
    OCEAN("Ocean"),
    FAN("Fan"),
    CRICKETS("Crickets"),
    FIRE("Fireplace"),
    WHITE("White noise"),
    PINK("Pink noise"),
    BROWN("Brown noise");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: RAIN
    }
}

/** One stereo channel's noise sources and filter state. */
private class Ch(seed: Long) {
    private var s = seed or 1L
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var b3 = 0.0
    private var b4 = 0.0; private var b5 = 0.0; private var b6 = 0.0
    private var br = 0.0
    var lp1 = 0.0; var lp2 = 0.0; var lp3 = 0.0
    var env1 = 0.0; var env2 = 0.0

    /** Uniform noise in -1..1 (xorshift). */
    fun white(): Double {
        s = s xor (s shl 13); s = s xor (s ushr 7); s = s xor (s shl 17)
        return (s ushr 11).toDouble() / (1L shl 53).toDouble() * 2.0 - 1.0
    }

    /** Pink noise (Paul Kellet's filter): softer, like steady rain. */
    fun pink(): Double {
        val w = white()
        b0 = 0.99886 * b0 + w * 0.0555179; b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520; b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522; b5 = -0.7616 * b5 - w * 0.0168980
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
        b6 = w * 0.115926
        return out * 0.11
    }

    /** Brown noise: deep and rumbly, like a distant waterfall. */
    fun brown(): Double {
        br = (br + 0.02 * white()) / 1.02
        return br * 3.5
    }
}

/** A chirping cricket: short bursts of a high tone, a few at a time. */
private class Cricket(val freq: Double, val pulses: Int, val gap: Double, val left: Double, var start: Long)

/** Generates a sleep sound as 16-bit stereo PCM. */
class SleepSynth(private val sound: SleepSound, private val rate: Int = 44100) {
    private val ch = arrayOf(Ch(0x2545F4914F6CDD1DL), Ch(0x9E3779B97F4A7C15uL.toLong()))
    private val rng = Ch(0x1234567L)
    private var n = 0L
    private var wavePos = 0.0
    private var wavePeriod = 9.0
    private var humPhase = 0.0
    private val crickets = listOf(
        Cricket(4300.0, 3, 0.75, 0.8, (rate * 0.2).toLong()),
        Cricket(4700.0, 4, 1.1, 0.3, (rate * 0.6).toLong()),
        Cricket(5100.0, 2, 0.6, 0.55, (rate * 0.9).toLong()),
    )
    private val cricketPhase = DoubleArray(crickets.size)

    /** Fills [out] with [frames] interleaved stereo frames at loudness [gain] (0..1). */
    fun fill(out: ShortArray, frames: Int, gain: Float) {
        val g = gain.toDouble()
        for (f in 0 until frames) {
            if (sound == SleepSound.OCEAN) {
                wavePos += 1.0 / (wavePeriod * rate)
                if (wavePos >= 1.0) { wavePos -= 1.0; wavePeriod = 7.0 + 4.0 * abs(rng.white()) }
            }
            if (sound == SleepSound.FAN) humPhase += 2 * PI * 58.0 / rate
            for (c in 0..1) {
                val v = sample(c) * g
                val clipped = if (v > 0.98) 0.98 else if (v < -0.98) -0.98 else v
                out[f * 2 + c] = (clipped * 32767).toInt().toShort()
            }
            n++
        }
    }

    private fun sample(c: Int): Double {
        val k = ch[c]
        return when (sound) {
            SleepSound.WHITE -> k.white() * 0.22
            SleepSound.PINK -> k.pink() * 0.8
            SleepSound.BROWN -> k.brown() * 0.9
            SleepSound.RAIN -> {
                // Hiss (high-passed pink noise), a low patter, and individual drops.
                val p = k.pink()
                k.lp1 += (p - k.lp1) * 0.06
                val hiss = p - k.lp1
                val rumble = k.brown() * 0.22
                if (k.white() > 0.996) k.env1 = 0.08 + 0.3 * abs(k.white())
                val drop = k.env1 * k.white()
                k.env1 *= 0.992
                k.lp2 += (drop - k.lp2) * 0.3
                hiss * 0.75 + rumble + k.lp2 * 0.9
            }
            SleepSound.OCEAN -> {
                // Waves: a slow swell and a longer fade, with surf hiss riding on top.
                val x = (wavePos + c * 0.035) % 1.0
                val env = if (x < 0.35) (x / 0.35).let { it * it } else exp(-(x - 0.35) * 4.5)
                val surf = k.pink() * (0.15 + 0.65 * env)
                val body = k.brown() * (0.35 + 0.45 * env)
                k.lp1 += (surf - k.lp1) * 0.25
                k.lp1 + body * 0.8
            }
            SleepSound.FAN -> {
                // Moving air (low-passed noise) and a faint motor hum.
                val b = k.brown()
                k.lp1 += (b - k.lp1) * 0.15
                val p = k.pink()
                k.lp2 += (p - k.lp2) * 0.07
                k.lp1 * 0.85 + k.lp2 * 0.9 + sin(humPhase) * 0.018 + sin(humPhase * 2) * 0.008
            }
            SleepSound.CRICKETS -> {
                var v = k.brown() * 0.12
                crickets.forEachIndexed { i, cr ->
                    val pulse = (rate * 0.016).toLong()
                    val t = n - cr.start
                    if (t >= 0) {
                        val idx = t / (pulse * 2)
                        if (idx < cr.pulses) {
                            val inPulse = t % (pulse * 2)
                            if (inPulse < pulse) {
                                val amp = sin(PI * inPulse / pulse)
                                val pan = if (c == 0) cr.left else 1.0 - cr.left
                                v += sin(cricketPhase[i]) * amp * pan * 0.09
                            }
                        } else if (c == 1) {
                            cr.start = n + (rate * (cr.gap * (0.8 + 0.4 * abs(rng.white())))).toLong()
                        }
                    }
                    if (c == 1) cricketPhase[i] += 2 * PI * cr.freq / rate
                }
                v
            }
            SleepSound.FIRE -> {
                // A low roar, soft hiss, and random crackles and pops.
                val b = k.brown()
                k.lp1 += (b - k.lp1) * 0.04
                val hiss = k.pink() * 0.035
                if (k.white() > 0.9993) k.env1 = 0.15 + 0.6 * abs(k.white())
                val crack = k.env1 * k.white()
                k.env1 *= 0.965
                if (k.white() > 0.99996) k.env2 = 0.7
                k.lp3 += (k.env2 * k.white() - k.lp3) * 0.5
                k.env2 *= 0.996
                k.lp1 * 1.4 + hiss + crack * 0.6 + k.lp3 * 0.6
            }
        }
    }
}
