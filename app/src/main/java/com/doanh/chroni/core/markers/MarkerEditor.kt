package com.doanh.chroni.core.markers

import kotlin.math.abs
import kotlin.math.roundToLong

enum class MarkerId { DEPARTURE, IMPACT }

/**
 * État d'édition des deux repères, en indices d'échantillons.
 * Les positions automatiques d'origine sont gardées séparément pour pouvoir y revenir.
 */
data class MarkerEditor(
    val sampleCount: Int,
    val sampleRate: Int,
    val autoDeparture: Long?,
    val autoImpact: Long?,
    val departure: Long?,
    val impact: Long?,
    val selected: MarkerId = MarkerId.IMPACT,
) {
    private val maxIndex: Long get() = (sampleCount - 1).coerceAtLeast(0).toLong()

    fun clamp(s: Long): Long = s.coerceIn(0L, maxIndex)

    fun position(id: MarkerId): Long? = if (id == MarkerId.DEPARTURE) departure else impact

    fun select(id: MarkerId) = copy(selected = id)

    fun moveTo(id: MarkerId, sample: Long): MarkerEditor {
        val s = clamp(sample)
        return if (id == MarkerId.DEPARTURE) copy(departure = s, selected = id) else copy(impact = s, selected = id)
    }

    fun nudge(id: MarkerId, deltaSamples: Long): MarkerEditor {
        val current = position(id) ?: return this
        return moveTo(id, current + deltaSamples)
    }

    /** Décalage exprimé en millisecondes, converti en échantillons entiers. */
    fun nudgeMs(id: MarkerId, deltaMs: Double): MarkerEditor =
        nudge(id, (deltaMs / 1000.0 * sampleRate).roundToLong())

    fun reset(): MarkerEditor = copy(departure = autoDeparture, impact = autoImpact)

    /** Place le repère sélectionné sur l'attaque d'un événement candidat. */
    fun snapTo(id: MarkerId, onsetSample: Long) = moveTo(id, onsetSample)

    /** Candidat le plus proche d'une position (pour la sélection quand plusieurs pics sont proches). */
    fun nearest(candidates: List<Long>, sample: Long): Long? = candidates.minByOrNull { abs(it - sample) }

    val isModified: Boolean get() = departure != autoDeparture || impact != autoImpact

    val deltaSamples: Long? get() = if (departure != null && impact != null) impact - departure else null

    fun timeOf(sample: Long): Double = sample.toDouble() / sampleRate
}

/**
 * Fenêtre visible du graphique : premier échantillon visible et nombre d'échantillons par pixel.
 * Logique pure, testable sans interface.
 */
data class Viewport(
    val sampleCount: Int,
    val widthPx: Float,
    val startSample: Double = 0.0,
    val samplesPerPx: Double = if (widthPx > 0) sampleCount / widthPx.toDouble() else 1.0,
) {
    val maxSamplesPerPx: Double get() = if (widthPx > 0) sampleCount / widthPx.toDouble() else 1.0
    val visibleSamples: Double get() = samplesPerPx * widthPx
    val endSample: Double get() = startSample + visibleSamples

    fun sampleAt(x: Float): Double = startSample + x * samplesPerPx
    fun xOf(sample: Double): Float = ((sample - startSample) / samplesPerPx).toFloat()

    fun clamped(): Viewport {
        val spp = samplesPerPx.coerceIn(MIN_SAMPLES_PER_PX, maxSamplesPerPx.coerceAtLeast(MIN_SAMPLES_PER_PX))
        val maxStart = (sampleCount - spp * widthPx).coerceAtLeast(0.0)
        return copy(samplesPerPx = spp, startSample = startSample.coerceIn(0.0, maxStart))
    }

    /** Zoom autour d'un point de l'écran (le point sous le doigt reste fixe). */
    fun zoom(factor: Double, focusX: Float): Viewport {
        val focusSample = sampleAt(focusX)
        val spp = (samplesPerPx / factor)
            .coerceIn(MIN_SAMPLES_PER_PX, maxSamplesPerPx.coerceAtLeast(MIN_SAMPLES_PER_PX))
        return copy(samplesPerPx = spp, startSample = focusSample - focusX * spp).clamped()
    }

    fun pan(dxPx: Float): Viewport = copy(startSample = startSample - dxPx * samplesPerPx).clamped()

    /** Centre la vue sur un intervalle d'échantillons avec une marge. */
    fun fit(from: Long, to: Long, marginFraction: Double = 0.15): Viewport {
        if (widthPx <= 0) return this
        val lo = minOf(from, to).toDouble()
        val hi = maxOf(from, to).toDouble()
        val span = (hi - lo).coerceAtLeast(MIN_SAMPLES_PER_PX * widthPx)
        val total = span * (1 + 2 * marginFraction)
        return copy(samplesPerPx = total / widthPx, startSample = lo - span * marginFraction).clamped()
    }

    fun centerOn(sample: Long): Viewport = copy(startSample = sample - visibleSamples / 2).clamped()

    fun full(): Viewport = copy(startSample = 0.0, samplesPerPx = maxSamplesPerPx).clamped()

    companion object {
        /** Zoom maximal : 1 échantillon = 8 pixels. */
        const val MIN_SAMPLES_PER_PX = 1.0 / 8.0
    }
}
