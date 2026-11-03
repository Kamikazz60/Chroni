package com.doanh.chroni.core.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Détecteur d'événements acoustiques impulsionnels (départ, impact, échos, parasites).
 *
 * Tout est exprimé en indices d'échantillons : aucune horloge système n'intervient.
 * Le détecteur est pur (sans Android) pour être testable sur des signaux synthétiques.
 *
 * Étapes :
 * 1. filtre passe-haut (supprime vent, grondements, composante continue) ;
 * 2. enveloppe RMS par trames de 1 ms et bruit de fond robuste (percentile) ;
 * 3. fronts montants : l'énergie doit dépasser à la fois le bruit de fond (SNR) et
 *    le niveau des 20 ms précédentes (montée brusque, ce qui écarte les traînes de réverbération) ;
 * 4. contrôle contextuel : durée minimale (un pic isolé d'un échantillon est rejeté),
 *    contenu haute fréquence, saturation ;
 * 5. localisation de l'attaque à l'échantillon près, avec une incertitude estimée ;
 * 6. appariement départ / impact dans une fenêtre de délais physiquement plausibles.
 */
class EventDetector(private val config: DetectorConfig = DetectorConfig()) {

    fun detect(
        samples: ShortArray,
        sampleCount: Int = samples.size,
        sampleRate: Int,
        window: SearchWindow?,
        ignoreBeforeSample: Int = 0,
        ignoreAfterSample: Int = sampleCount,
    ): DetectionResult {
        require(sampleRate > 0)
        val n = min(sampleCount, samples.size)
        val frame = max(1, (sampleRate * config.frameMs / 1000.0).roundToInt())
        val nFrames = n / frame
        if (nFrames < 10) return DetectionResult.failed(DetectionWarning.NO_EVENT, -120.0)

        val y = highPass(samples, n, sampleRate, config.highPassHz)
        val rms = DoubleArray(nFrames)
        for (k in 0 until nFrames) {
            var s = 0.0
            val base = k * frame
            for (i in 0 until frame) { val v = y[base + i]; s += v * v }
            rms[k] = sqrt(s / frame)
        }
        val noise = max(percentile(rms, config.noisePercentile), 1e-6)
        val noiseDb = 20 * log10(noise)
        val snrRatio = dbToRatio(config.minSnrDb)
        val riseRatio = dbToRatio(config.minRiseDb)

        val prefix = DoubleArray(nFrames + 1)
        for (k in 0 until nFrames) prefix[k + 1] = prefix[k] + rms[k]
        val bgFrames = max(1, (config.backgroundMs / config.frameMs).roundToInt())
        val guardFrames = max(1, (config.guardMs / config.frameMs).roundToInt())
        val peakFrames = max(1, (config.peakSearchMs / config.frameMs).roundToInt())
        val holdFrames = max(1, (config.holdOffMs / config.frameMs).roundToInt())

        val events = mutableListOf<AcousticEvent>()
        var k = 0
        var lastEventEndSample = 0
        while (k < nFrames) {
            val end = k - guardFrames
            val start = end - bgFrames
            val bg = if (start >= 0) max(noise, (prefix[end] - prefix[start]) / bgFrames) else noise
            val triggered = rms[k] >= noise * snrRatio && rms[k] >= bg * riseRatio
            if (!triggered) { k++; continue }

            // Pic d'énergie juste après le déclenchement.
            var kp = k
            for (j in k until min(nFrames, k + peakFrames)) if (rms[j] > rms[kp]) kp = j
            val peakRms = rms[kp]

            // Durée au-dessus du seuil de détection (contrôle anti-pic isolé).
            var durFrames = 0
            var j = k
            while (j < nFrames && rms[j] >= noise * snrRatio && durFrames < 1000) { durFrames++; j++ }

            val segStart = max(lastEventEndSample, (k - 2) * frame)
            val segEnd = min(n, (kp + 1) * frame)
            var peakAbs = 0.0
            for (i in segStart until segEnd) peakAbs = max(peakAbs, abs(y[i]).toDouble())
            val noiseAbs = noise * sqrt(2.0)
            val thrHigh = max(noiseAbs * 4, 0.2 * peakAbs)
            val thrLow = max(noiseAbs * 4, 0.05 * peakAbs)
            var nHigh = segStart
            while (nHigh < segEnd && abs(y[nHigh]) < thrHigh) nHigh++
            val lookBack = max(segStart, nHigh - (sampleRate * 0.002).toInt())
            var nLow = lookBack
            while (nLow < nHigh && abs(y[nLow]) < thrLow) nLow++
            val onset = nLow
            val uncertaintySamples = max(1.0, (nHigh - nLow) / 2.0)

            // Contenu haute fréquence sur les 3 premières ms (les échos sont plus « mous »).
            val hfEnd = min(n, onset + (sampleRate * 0.003).toInt())
            var e = 0.0; var d = 0.0
            for (i in max(1, onset) until hfEnd) { e += y[i] * y[i]; val df = y[i] - y[i - 1]; d += df * df }
            val hfRatio = if (e > 0) min(1.0, d / e / 4.0) else 0.0

            // Saturation (sur le signal brut).
            val clipEnd = min(n, onset + (sampleRate * 0.010).toInt())
            var clipped = 0
            for (i in onset until clipEnd) if (abs(samples[i].toInt()) >= config.clipLevel) clipped++

            val snrDb = 20 * log10(peakRms / noise)
            val riseDb = 20 * log10(peakRms / bg)
            // Contrôle sur le signal brut : un clic d'un ou deux échantillons n'est pas un événement.
            val spikeEnd = min(n, onset + (sampleRate * 0.002).toInt())
            val rawStart = max(0, onset - frame)
            var rawPeak = 0
            for (i in rawStart until spikeEnd) rawPeak = max(rawPeak, abs(samples[i].toInt()))
            var strong = 0
            for (i in rawStart until spikeEnd) if (abs(samples[i].toInt()) >= rawPeak / 4) strong++
            val isSpike = durFrames < config.minDurationFrames || strong < config.minStrongSamples
            val score = snrDb + 0.5 * min(riseDb, 20.0) + 10.0 * hfRatio

            val peakSample = run {
                var best = segStart; var bv = 0.0
                for (i in segStart until min(n, (kp + 1) * frame)) { val a = abs(y[i]).toDouble(); if (a > bv) { bv = a; best = i } }
                best
            }
            events += AcousticEvent(
                onsetSample = onset.toLong(),
                peakSample = peakSample.toLong(),
                peakAmplitude = peakAbs,
                snrDb = snrDb,
                riseDb = riseDb,
                hfRatio = hfRatio,
                durationMs = durFrames * config.frameMs,
                clipped = clipped >= 3,
                onsetUncertaintySamples = uncertaintySamples,
                score = score,
                role = when {
                    isSpike -> EventRole.SPIKE
                    onset < ignoreBeforeSample || onset >= ignoreAfterSample -> EventRole.IGNORED
                    else -> EventRole.CANDIDATE
                },
            )
            lastEventEndSample = (kp + 1) * frame
            k = kp + holdFrames
        }
        return pair(events, sampleRate, window, noiseDb)
    }

    /** Appariement départ / impact parmi les événements valides. */
    private fun pair(all: List<AcousticEvent>, sampleRate: Int, window: SearchWindow?, noiseDb: Double): DetectionResult {
        val warnings = mutableListOf<DetectionWarning>()
        val valid = all.withIndex().filter { it.value.role == EventRole.CANDIDATE }
        if (valid.isEmpty()) {
            return DetectionResult(all, null, null, Quality.FAILED, listOf(DetectionWarning.NO_EVENT), noiseDb)
        }
        val maxPeak = valid.maxOf { it.value.peakAmplitude }
        val dep = valid.first { it.value.peakAmplitude >= config.departureRelativeLevel * maxPeak }
        val loud = valid.count { it.value.peakAmplitude >= 0.7 * maxPeak }
        if (loud > 2) warnings += DetectionWarning.MULTIPLE_LOUD_EVENTS
        if (dep.value.clipped) warnings += DetectionWarning.CLIPPING

        val roles = all.map { it.role }.toMutableList()
        roles[dep.index] = EventRole.DEPARTURE

        val depOnset = dep.value.onsetSample
        val inWindow = valid.filter { iv ->
            val dtS = (iv.value.onsetSample - depOnset).toDouble() / sampleRate
            iv.index != dep.index && dtS > 0 && (window == null || (dtS >= window.minDelayS && dtS <= window.maxDelayS))
        }
        for (iv in valid) if (iv.index != dep.index && iv !in inWindow) roles[iv.index] = EventRole.OUT_OF_WINDOW

        if (inWindow.isEmpty()) {
            warnings += DetectionWarning.NO_IMPACT_IN_WINDOW
            return DetectionResult(all.withRoles(roles), dep.index, null, Quality.FAILED, warnings, noiseDb)
        }
        val best = inWindow.maxOf { it.value.score }
        // Les échos de l'impact arrivent après lui : à score comparable, on retient le plus précoce.
        val imp = inWindow.first { it.value.score >= best - config.ambiguityDb }
        roles[imp.index] = EventRole.IMPACT
        val rivals = inWindow.count { it.index != imp.index && it.value.score >= imp.value.score - config.ambiguityDb }
        if (rivals > 0) warnings += DetectionWarning.AMBIGUOUS_IMPACT
        if (imp.value.snrDb < config.lowSnrDb) warnings += DetectionWarning.LOW_SNR_IMPACT
        if (imp.value.clipped) warnings += DetectionWarning.CLIPPING

        val quality = when {
            DetectionWarning.AMBIGUOUS_IMPACT in warnings || imp.value.snrDb < config.lowSnrDb -> Quality.POOR
            DetectionWarning.MULTIPLE_LOUD_EVENTS in warnings || imp.value.snrDb < config.goodSnrDb -> Quality.FAIR
            else -> Quality.GOOD
        }
        return DetectionResult(all.withRoles(roles), dep.index, imp.index, quality, warnings.distinct(), noiseDb)
    }

    private fun List<AcousticEvent>.withRoles(roles: List<EventRole>) = mapIndexed { i, e -> e.copy(role = roles[i]) }

    companion object {
        fun dbToRatio(db: Double): Double = Math.pow(10.0, db / 20.0)

        fun percentile(values: DoubleArray, p: Double): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.copyOf().also { it.sort() }
            val idx = ((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }

        /** Filtre passe-haut du 1er ordre ; renvoie un signal normalisé dans [-1, 1]. */
        fun highPass(samples: ShortArray, n: Int, sampleRate: Int, cutoffHz: Double): FloatArray {
            val out = FloatArray(n)
            if (n == 0) return out
            val rc = 1.0 / (2 * PI * cutoffHz)
            val dt = 1.0 / sampleRate
            val a = rc / (rc + dt)
            var prevX = samples[0] / 32768.0
            var prevY = 0.0
            for (i in 0 until n) {
                val x = samples[i] / 32768.0
                val yv = a * (prevY + x - prevX)
                out[i] = yv.toFloat()
                prevX = x; prevY = yv
            }
            return out
        }
    }
}

data class DetectorConfig(
    val highPassHz: Double = 150.0,
    val frameMs: Double = 1.0,
    val noisePercentile: Double = 0.3,
    val minSnrDb: Double = 15.0,
    val minRiseDb: Double = 10.0,
    val backgroundMs: Double = 20.0,
    val guardMs: Double = 3.0,
    val peakSearchMs: Double = 5.0,
    val holdOffMs: Double = 8.0,
    val minDurationFrames: Int = 2,
    val minStrongSamples: Int = 8,
    val departureRelativeLevel: Double = 0.5,
    val ambiguityDb: Double = 3.0,
    val lowSnrDb: Double = 20.0,
    val goodSnrDb: Double = 28.0,
    val clipLevel: Int = 32000,
)

/** Fenêtre de délais plausibles entre départ et impact. */
data class SearchWindow(val minDelayS: Double, val maxDelayS: Double) {
    companion object {
        fun forShot(distanceM: Double, speedOfSound: Double, vMin: Double, vMax: Double) = SearchWindow(
            minDelayS = distanceM / vMax + distanceM / speedOfSound,
            maxDelayS = distanceM / vMin + distanceM / speedOfSound,
        )
    }
}

enum class EventRole { DEPARTURE, IMPACT, CANDIDATE, OUT_OF_WINDOW, SPIKE, IGNORED }

data class AcousticEvent(
    val onsetSample: Long,
    val peakSample: Long,
    val peakAmplitude: Double,
    val snrDb: Double,
    val riseDb: Double,
    val hfRatio: Double,
    val durationMs: Double,
    val clipped: Boolean,
    val onsetUncertaintySamples: Double,
    val score: Double,
    val role: EventRole,
)

enum class Quality { GOOD, FAIR, POOR, FAILED }

enum class DetectionWarning {
    NO_EVENT,
    NO_IMPACT_IN_WINDOW,
    AMBIGUOUS_IMPACT,
    LOW_SNR_IMPACT,
    MULTIPLE_LOUD_EVENTS,
    CLIPPING,
}

data class DetectionResult(
    val events: List<AcousticEvent>,
    val departureIndex: Int?,
    val impactIndex: Int?,
    val quality: Quality,
    val warnings: List<DetectionWarning>,
    val noiseFloorDb: Double,
) {
    val departure: AcousticEvent? get() = departureIndex?.let { events[it] }
    val impact: AcousticEvent? get() = impactIndex?.let { events[it] }

    companion object {
        fun failed(w: DetectionWarning, noiseDb: Double) = DetectionResult(emptyList(), null, null, Quality.FAILED, listOf(w), noiseDb)
    }
}
