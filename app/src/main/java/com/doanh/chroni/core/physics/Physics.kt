package com.doanh.chroni.core.physics

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Calculs physiques de Chroni, indépendants de l'interface et d'Android.
 *
 * Modèle : Δt = D/v + D/c  ⇒  v = D / (Δt − D/c)
 * Toutes les valeurs internes sont en unités SI (m, s, kg, m/s, J) et en Double.
 */
object Physics {
    /** Température utilisée quand l'utilisateur n'en fournit pas. */
    const val NOMINAL_TEMPERATURE_C = 20.0

    /** Incertitude supposée sur la température nominale (°C). */
    const val NOMINAL_TEMPERATURE_UNCERTAINTY_C = 10.0

    /** Incertitude supposée sur une température saisie (°C). */
    const val MEASURED_TEMPERATURE_UNCERTAINTY_C = 2.0

    /** Au-delà, la vitesse est considérée comme non physique pour cette application. */
    const val MAX_PLAUSIBLE_VELOCITY = 2000.0

    /** En dessous, la vitesse est jugée non plausible pour un projectile. */
    const val MIN_PLAUSIBLE_VELOCITY = 5.0

    const val MIN_MASS_G = 0.01
    const val MAX_MASS_G = 1000.0
    const val MIN_DISTANCE_M = 0.5
    const val MAX_DISTANCE_M = 500.0
    const val MIN_TEMPERATURE_C = -40.0
    const val MAX_TEMPERATURE_C = 60.0

    /** Vitesse du son dans l'air sec (m/s) : c = 331,3 × √(1 + T/273,15). */
    fun speedOfSound(temperatureC: Double): Double = 331.3 * sqrt(1.0 + temperatureC / 273.15)

    /** Dérivée dc/dT (m/s par °C), pour la propagation d'incertitude. */
    fun speedOfSoundSlope(temperatureC: Double): Double =
        331.3 / (2.0 * 273.15 * sqrt(1.0 + temperatureC / 273.15))

    fun gramsToKilograms(grams: Double): Double = grams / 1000.0
    fun secondsToMilliseconds(seconds: Double): Double = seconds * 1000.0
    fun millisecondsToSeconds(ms: Double): Double = ms / 1000.0
    fun samplesToSeconds(samples: Long, sampleRate: Int): Double {
        require(sampleRate > 0) { "Fréquence d'échantillonnage invalide" }
        return samples.toDouble() / sampleRate.toDouble()
    }

    fun kineticEnergy(massKg: Double, velocity: Double): Double = 0.5 * massKg * velocity * velocity

    /** Délai attendu pour une vitesse donnée (utile pour la fenêtre de recherche et les tests). */
    fun expectedDelay(distanceM: Double, velocity: Double, speedOfSound: Double): Double =
        distanceM / velocity + distanceM / speedOfSound

    /** Valide les paramètres saisis. Renvoie la liste des erreurs (vide si tout est correct). */
    fun validate(params: ShotParams): List<InputError> {
        val errors = mutableListOf<InputError>()
        val m = params.massGrams
        if (m.isNaN() || m < MIN_MASS_G || m > MAX_MASS_G) errors += InputError.MASS
        val d = params.distanceM
        if (d.isNaN() || d < MIN_DISTANCE_M || d > MAX_DISTANCE_M) errors += InputError.DISTANCE
        val t = params.temperatureC
        if (t != null && (t.isNaN() || t < MIN_TEMPERATURE_C || t > MAX_TEMPERATURE_C)) errors += InputError.TEMPERATURE
        return errors
    }

    /**
     * Calcule vitesse et énergie à partir du délai mesuré.
     * @param deltaTSeconds délai entre départ et impact (s)
     * @param timingUncertaintySeconds incertitude-type estimée sur Δt (s)
     */
    fun compute(params: ShotParams, deltaTSeconds: Double, timingUncertaintySeconds: Double): ShotResult {
        val errors = validate(params)
        if (errors.isNotEmpty()) return ShotResult.Invalid(InvalidReason.INVALID_INPUT, inputErrors = errors)

        val tempC = params.temperatureC ?: NOMINAL_TEMPERATURE_C
        val c = speedOfSound(tempC)
        val d = params.distanceM
        val soundTime = d / c

        if (deltaTSeconds.isNaN() || deltaTSeconds <= 0.0) {
            return ShotResult.Invalid(InvalidReason.DELAY_NOT_POSITIVE, speedOfSound = c, soundTimeS = soundTime)
        }
        val projectileTime = deltaTSeconds - soundTime
        if (projectileTime <= 0.0) {
            return ShotResult.Invalid(InvalidReason.DELAY_SHORTER_THAN_SOUND, speedOfSound = c, soundTimeS = soundTime)
        }
        val v = d / projectileTime
        if (v > MAX_PLAUSIBLE_VELOCITY) {
            return ShotResult.Invalid(InvalidReason.VELOCITY_TOO_HIGH, speedOfSound = c, soundTimeS = soundTime, rawVelocity = v)
        }
        if (v < MIN_PLAUSIBLE_VELOCITY) {
            return ShotResult.Invalid(InvalidReason.VELOCITY_TOO_LOW, speedOfSound = c, soundTimeS = soundTime, rawVelocity = v)
        }

        val massKg = gramsToKilograms(params.massGrams)
        val energy = kineticEnergy(massKg, v)

        // Propagation d'incertitude : dv/dτ = −v²/D, avec τ = Δt − D/c.
        val tempUnc = if (params.temperatureC == null) NOMINAL_TEMPERATURE_UNCERTAINTY_C else MEASURED_TEMPERATURE_UNCERTAINTY_C
        val cUnc = speedOfSoundSlope(tempC) * tempUnc
        val soundTimeUnc = d * cUnc / (c * c)
        val tauUnc = sqrt(timingUncertaintySeconds * timingUncertaintySeconds + soundTimeUnc * soundTimeUnc)
        val vUnc = v * v / d * tauUnc
        val eUnc = massKg * v * vUnc

        val warnings = mutableListOf<ResultWarning>()
        if (v >= c * 0.95) warnings += ResultWarning.NEAR_OR_ABOVE_SOUND_SPEED
        if (vUnc / v > 0.10) warnings += ResultWarning.LARGE_UNCERTAINTY
        if (params.temperatureC == null) warnings += ResultWarning.NOMINAL_TEMPERATURE

        return ShotResult.Valid(
            deltaTS = deltaTSeconds,
            projectileTimeS = projectileTime,
            soundTimeS = soundTime,
            speedOfSound = c,
            velocity = v,
            energy = energy,
            velocityUncertainty = vUnc,
            energyUncertainty = eUnc,
            warnings = warnings,
        )
    }

    /** Nombre de décimales justifié par une incertitude donnée (0, 1 ou 2). */
    fun decimalsFor(uncertainty: Double): Int = when {
        uncertainty.isNaN() || uncertainty >= 1.0 -> 0
        uncertainty >= 0.1 -> 1
        else -> 2
    }

    /** Arrondi d'affichage uniquement : ne jamais réinjecter dans les calculs. */
    fun formatValue(value: Double, uncertainty: Double): String {
        val dec = decimalsFor(abs(uncertainty))
        return String.format(java.util.Locale.FRANCE, "%.${dec}f", value)
    }
}

data class ShotParams(
    val massGrams: Double,
    val distanceM: Double,
    /** null = température inconnue, la valeur nominale est utilisée. */
    val temperatureC: Double? = null,
)

enum class InputError { MASS, DISTANCE, TEMPERATURE }

enum class InvalidReason {
    INVALID_INPUT,
    DELAY_NOT_POSITIVE,
    DELAY_SHORTER_THAN_SOUND,
    VELOCITY_TOO_HIGH,
    VELOCITY_TOO_LOW,
    NO_EVENTS,
}

enum class ResultWarning { NEAR_OR_ABOVE_SOUND_SPEED, LARGE_UNCERTAINTY, NOMINAL_TEMPERATURE }

sealed class ShotResult {
    data class Valid(
        val deltaTS: Double,
        val projectileTimeS: Double,
        val soundTimeS: Double,
        val speedOfSound: Double,
        val velocity: Double,
        val energy: Double,
        val velocityUncertainty: Double,
        val energyUncertainty: Double,
        val warnings: List<ResultWarning>,
    ) : ShotResult()

    data class Invalid(
        val reason: InvalidReason,
        val inputErrors: List<InputError> = emptyList(),
        val speedOfSound: Double? = null,
        val soundTimeS: Double? = null,
        val rawVelocity: Double? = null,
    ) : ShotResult()
}
