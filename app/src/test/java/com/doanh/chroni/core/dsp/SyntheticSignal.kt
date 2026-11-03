package com.doanh.chroni.core.dsp

import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Générateur de signaux synthétiques représentatifs (bruit, détonation, impact, écho). */
class SyntheticSignal(val sampleRate: Int, durationS: Double, seed: Long = 42) {
    private val rnd = Random(seed)
    val data = DoubleArray((sampleRate * durationS).toInt())

    fun noise(rmsAmplitude: Double): SyntheticSignal {
        for (i in data.indices) data[i] += rnd.nextGaussian() * rmsAmplitude
        return this
    }

    /** Détonation : bruit large bande à attaque quasi instantanée et décroissance exponentielle. */
    fun blast(atS: Double, amplitude: Double, decayMs: Double = 15.0): SyntheticSignal {
        val start = (atS * sampleRate).toInt()
        val tau = decayMs / 1000.0 * sampleRate
        val len = (tau * 8).toInt()
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= data.size) break
            data[idx] += amplitude * exp(-i / tau) * (rnd.nextDouble() * 2 - 1)
        }
        return this
    }

    /** Impact sur cible métallique : attaque franche + résonance (plusieurs modes). */
    fun ring(atS: Double, amplitude: Double, decayMs: Double = 30.0): SyntheticSignal {
        val start = (atS * sampleRate).toInt()
        val tau = decayMs / 1000.0 * sampleRate
        val len = (tau * 8).toInt()
        val freqs = doubleArrayOf(1800.0, 3100.0, 5200.0)
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= data.size) break
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for (f in freqs) v += sin(2 * PI * f * t)
            v = v / freqs.size + 0.3 * (rnd.nextDouble() * 2 - 1) * exp(-i / (tau / 6))
            data[idx] += amplitude * exp(-i / tau) * v
        }
        return this
    }

    /** Écho : copie atténuée, filtrée passe-bas et à attaque adoucie d'une portion du signal. */
    fun echo(sourceS: Double, delayS: Double, gain: Double, lengthMs: Double = 60.0): SyntheticSignal {
        val src = (sourceS * sampleRate).toInt()
        val dst = ((sourceS + delayS) * sampleRate).toInt()
        val len = (lengthMs / 1000.0 * sampleRate).toInt()
        val attack = (0.004 * sampleRate) // montée sur 4 ms
        var lp = 0.0
        val alpha = 0.15 // passe-bas marqué
        val copy = DoubleArray(len) { i -> if (src + i < data.size) data[src + i] else 0.0 }
        for (i in 0 until len) {
            if (dst + i >= data.size) break
            lp += alpha * (copy[i] - lp)
            val env = if (i < attack) i / attack else 1.0
            data[dst + i] += gain * env * lp * 2.5
        }
        return this
    }

    fun spike(atS: Double, amplitude: Double): SyntheticSignal {
        val idx = (atS * sampleRate).toInt()
        if (idx in data.indices) data[idx] += amplitude
        return this
    }

    fun toShorts(): ShortArray = ShortArray(data.size) { i ->
        (data[i] * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
    }
}
