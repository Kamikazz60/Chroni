package com.doanh.chroni.core.model

import com.doanh.chroni.core.dsp.AcousticEvent
import com.doanh.chroni.core.dsp.DetectionWarning
import com.doanh.chroni.core.dsp.EventRole
import com.doanh.chroni.core.dsp.Quality
import com.doanh.chroni.core.physics.Physics
import com.doanh.chroni.core.physics.ShotParams
import com.doanh.chroni.core.physics.ShotResult
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

enum class TemperatureSource { NOMINAL, MANUAL, SENSOR }

enum class RecordingWarning { SAMPLES_POSSIBLY_LOST, MIC_SILENCED, INTERRUPTED, MAX_DURATION_REACHED }

enum class DetectionMode { AUTO, CORRECTED, MANUAL, NONE }

@Serializable
data class StoredEvent(
    val onsetSample: Long,
    val peakAmplitude: Double,
    val snrDb: Double,
    val score: Double,
    val role: EventRole,
    val uncertaintySamples: Double,
) {
    companion object {
        fun from(e: AcousticEvent) = StoredEvent(e.onsetSample, e.peakAmplitude, e.snrDb, e.score, e.role, e.onsetUncertaintySamples)
    }
}

/**
 * Une mesure complète. Les repères sont TOUJOURS des indices d'échantillons
 * (jamais des pixels). Les positions automatiques sont conservées à part des positions finales.
 */
@Serializable
data class Measurement(
    val id: String,
    val createdAtMs: Long,
    val massGrams: Double,
    val distanceM: Double,
    val temperatureC: Double?,
    val temperatureSource: TemperatureSource,
    val sampleRate: Int,
    val sampleCount: Int,
    val audioSourceName: String,
    val audioKept: Boolean,
    val autoDepartureSample: Long?,
    val autoImpactSample: Long?,
    val departureSample: Long?,
    val impactSample: Long?,
    val detectionQuality: Quality,
    val detectionWarnings: List<DetectionWarning>,
    val recordingWarnings: List<RecordingWarning> = emptyList(),
    val events: List<StoredEvent>,
    val noiseFloorDb: Double,
    val vMin: Double,
    val vMax: Double,
    val confirmed: Boolean = false,
    val modifiedAtMs: Long? = null,
    val appVersion: String = "",
    // Instantané des résultats (pour l'historique) ; recalculé à chaque modification.
    val deltaTMs: Double? = null,
    val velocity: Double? = null,
    val energy: Double? = null,
    val velocityUncertainty: Double? = null,
    val energyUncertainty: Double? = null,
) {
    val params: ShotParams get() = ShotParams(massGrams, distanceM, temperatureC)

    val mode: DetectionMode
        get() = when {
            departureSample == null || impactSample == null -> DetectionMode.NONE
            autoDepartureSample == null || autoImpactSample == null -> DetectionMode.MANUAL
            departureSample != autoDepartureSample || impactSample != autoImpactSample -> DetectionMode.CORRECTED
            else -> DetectionMode.AUTO
        }

    val durationS: Double get() = sampleCount.toDouble() / sampleRate

    /** Recalcule les résultats à partir des repères finaux. */
    fun evaluate(dep: Long? = departureSample, imp: Long? = impactSample): ShotResult =
        Evaluation.evaluate(this, dep, imp)

    /** Copie avec de nouveaux repères et l'instantané de résultats mis à jour. */
    fun withMarkers(dep: Long?, imp: Long?, confirmed: Boolean, nowMs: Long): Measurement {
        val base = copy(departureSample = dep, impactSample = imp, confirmed = confirmed, modifiedAtMs = nowMs)
        return base.withSnapshot()
    }

    fun withSnapshot(): Measurement = when (val r = evaluate()) {
        is ShotResult.Valid -> copy(
            deltaTMs = Physics.secondsToMilliseconds(r.deltaTS),
            velocity = r.velocity,
            energy = r.energy,
            velocityUncertainty = r.velocityUncertainty,
            energyUncertainty = r.energyUncertainty,
        )
        is ShotResult.Invalid -> copy(
            deltaTMs = if (departureSample != null && impactSample != null)
                Physics.secondsToMilliseconds((impactSample - departureSample).toDouble() / sampleRate) else null,
            velocity = null, energy = null, velocityUncertainty = null, energyUncertainty = null,
        )
    }
}

object MeasurementFactory {
    fun create(
        id: String,
        nowMs: Long,
        params: ShotParams,
        temperatureSource: TemperatureSource,
        sampleRate: Int,
        sampleCount: Int,
        audioSourceName: String,
        detection: com.doanh.chroni.core.dsp.DetectionResult,
        recordingWarnings: List<RecordingWarning>,
        audioKept: Boolean,
        vMin: Double,
        vMax: Double,
        appVersion: String,
    ): Measurement {
        val dep = detection.departure?.onsetSample
        val imp = detection.impact?.onsetSample
        return Measurement(
            id = id,
            createdAtMs = nowMs,
            massGrams = params.massGrams,
            distanceM = params.distanceM,
            temperatureC = params.temperatureC,
            temperatureSource = temperatureSource,
            sampleRate = sampleRate,
            sampleCount = sampleCount,
            audioSourceName = audioSourceName,
            audioKept = audioKept,
            autoDepartureSample = dep,
            autoImpactSample = imp,
            departureSample = dep,
            impactSample = imp,
            detectionQuality = detection.quality,
            detectionWarnings = detection.warnings,
            recordingWarnings = recordingWarnings,
            events = detection.events.map { StoredEvent.from(it) },
            noiseFloorDb = detection.noiseFloorDb,
            vMin = vMin,
            vMax = vMax,
            appVersion = appVersion,
        ).withSnapshot()
    }
}

object Evaluation {
    /** Incertitude de base du modèle de détection, par repère (s). */
    const val BASE_MARKER_UNCERTAINTY_S = 0.00005

    /** Incertitude d'un repère placé à la main (échantillons). */
    const val MANUAL_MARKER_UNCERTAINTY_SAMPLES = 2.0

    fun markerUncertaintySamples(m: Measurement, sample: Long, auto: Long?): Double {
        if (auto != null && sample == auto) {
            m.events.firstOrNull { it.onsetSample == auto }?.let { return it.uncertaintySamples }
        }
        return MANUAL_MARKER_UNCERTAINTY_SAMPLES
    }

    fun evaluate(m: Measurement, dep: Long?, imp: Long?): ShotResult {
        if (dep == null || imp == null) {
            return ShotResult.Invalid(com.doanh.chroni.core.physics.InvalidReason.NO_EVENTS)
        }
        val dt = Physics.samplesToSeconds(imp - dep, m.sampleRate)
        val uDep = markerUncertaintySamples(m, dep, m.autoDepartureSample) / m.sampleRate + BASE_MARKER_UNCERTAINTY_S
        val uImp = markerUncertaintySamples(m, imp, m.autoImpactSample) / m.sampleRate + BASE_MARKER_UNCERTAINTY_S
        val u = sqrt(uDep * uDep + uImp * uImp)
        return Physics.compute(m.params, dt, u)
    }
}
