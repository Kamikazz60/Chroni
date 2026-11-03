package com.doanh.chroni.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsTest {
    private val eps = 1e-9

    @Test fun conversionGrammesKilogrammes() {
        assertEquals(0.00055, Physics.gramsToKilograms(0.55), eps)
        assertEquals(1.0, Physics.gramsToKilograms(1000.0), eps)
    }

    @Test fun conversionSecondesMillisecondes() {
        assertEquals(123.4, Physics.secondsToMilliseconds(0.1234), eps)
        assertEquals(0.1234, Physics.millisecondsToSeconds(123.4), eps)
        assertEquals(0.5, Physics.samplesToSeconds(24_000, 48_000), eps)
    }

    @Test fun vitesseDuSon() {
        assertEquals(331.3, Physics.speedOfSound(0.0), 1e-9)
        assertEquals(343.2, Physics.speedOfSound(20.0), 0.1)
        assertTrue(Physics.speedOfSound(30.0) > Physics.speedOfSound(0.0))
    }

    @Test fun allerRetourCoherent() {
        // Pour une vitesse donnée, le délai attendu doit redonner exactement cette vitesse.
        val params = ShotParams(massGrams = 0.55, distanceM = 10.0, temperatureC = 15.0)
        val c = Physics.speedOfSound(15.0)
        for (v in listOf(50.0, 100.0, 300.0, 900.0)) {
            val dt = Physics.expectedDelay(10.0, v, c)
            val r = Physics.compute(params, dt, 0.0001) as ShotResult.Valid
            assertEquals(v, r.velocity, 1e-6)
            assertEquals(dt, r.deltaTS, eps)
            assertEquals(10.0 / v, r.projectileTimeS, 1e-9)
            assertEquals(10.0 / c, r.soundTimeS, 1e-9)
        }
    }

    @Test fun energieCinetique() {
        // 0,55 g à 100 m/s → 0,5 × 0,00055 × 10000 = 2,75 J
        val params = ShotParams(0.55, 10.0, 20.0)
        val c = Physics.speedOfSound(20.0)
        val r = Physics.compute(params, Physics.expectedDelay(10.0, 100.0, c), 0.0) as ShotResult.Valid
        assertEquals(2.75, r.energy, 1e-6)
    }

    @Test fun temperatureNominaleSiAbsente() {
        val params = ShotParams(1.0, 10.0, null)
        val c = Physics.speedOfSound(Physics.NOMINAL_TEMPERATURE_C)
        val r = Physics.compute(params, Physics.expectedDelay(10.0, 200.0, c), 0.0) as ShotResult.Valid
        assertEquals(c, r.speedOfSound, eps)
        assertTrue(ResultWarning.NOMINAL_TEMPERATURE in r.warnings)
    }

    @Test fun delaiNulOuNegatifRefuse() {
        val p = ShotParams(1.0, 10.0, 20.0)
        assertEquals(InvalidReason.DELAY_NOT_POSITIVE, (Physics.compute(p, 0.0, 0.0) as ShotResult.Invalid).reason)
        assertEquals(InvalidReason.DELAY_NOT_POSITIVE, (Physics.compute(p, -0.01, 0.0) as ShotResult.Invalid).reason)
        assertEquals(InvalidReason.DELAY_NOT_POSITIVE, (Physics.compute(p, Double.NaN, 0.0) as ShotResult.Invalid).reason)
    }

    @Test fun delaiPlusCourtQueLeSonRefuse() {
        val p = ShotParams(1.0, 10.0, 20.0)
        val soundTime = 10.0 / Physics.speedOfSound(20.0)
        val r = Physics.compute(p, soundTime * 0.9, 0.0) as ShotResult.Invalid
        assertEquals(InvalidReason.DELAY_SHORTER_THAN_SOUND, r.reason)
        val r2 = Physics.compute(p, soundTime, 0.0) as ShotResult.Invalid
        assertEquals(InvalidReason.DELAY_SHORTER_THAN_SOUND, r2.reason)
    }

    @Test fun vitesseNonPhysiqueRefusee() {
        val p = ShotParams(1.0, 10.0, 20.0)
        val c = Physics.speedOfSound(20.0)
        val tooFast = Physics.compute(p, 10.0 / c + 10.0 / 5000.0, 0.0) as ShotResult.Invalid
        assertEquals(InvalidReason.VELOCITY_TOO_HIGH, tooFast.reason)
        val tooSlow = Physics.compute(p, 10.0 / c + 10.0 / 1.0, 0.0) as ShotResult.Invalid
        assertEquals(InvalidReason.VELOCITY_TOO_LOW, tooSlow.reason)
    }

    @Test fun entreesInvalides() {
        assertEquals(listOf(InputError.MASS), Physics.validate(ShotParams(0.0, 10.0)))
        assertEquals(listOf(InputError.MASS), Physics.validate(ShotParams(-1.0, 10.0)))
        assertEquals(listOf(InputError.DISTANCE), Physics.validate(ShotParams(1.0, 0.0)))
        assertEquals(listOf(InputError.DISTANCE), Physics.validate(ShotParams(1.0, Double.NaN)))
        assertEquals(listOf(InputError.TEMPERATURE), Physics.validate(ShotParams(1.0, 10.0, 99.0)))
        assertTrue(Physics.validate(ShotParams(0.55, 10.0, 20.0)).isEmpty())
        val r = Physics.compute(ShotParams(0.0, 0.0), 0.1, 0.0) as ShotResult.Invalid
        assertEquals(InvalidReason.INVALID_INPUT, r.reason)
        assertEquals(2, r.inputErrors.size)
    }

    @Test fun incertitudeCroitAvecLaVitesse() {
        val p = ShotParams(1.0, 10.0, 20.0)
        val c = Physics.speedOfSound(20.0)
        val slow = Physics.compute(p, Physics.expectedDelay(10.0, 100.0, c), 0.0002) as ShotResult.Valid
        val fast = Physics.compute(p, Physics.expectedDelay(10.0, 400.0, c), 0.0002) as ShotResult.Valid
        assertTrue(fast.velocityUncertainty > slow.velocityUncertainty)
        // dv = v²/D × dt (température mesurée : petite contribution supplémentaire)
        assertTrue(slow.velocityUncertainty >= 100.0 * 100.0 / 10.0 * 0.0002)
    }

    @Test fun supersoniqueSignale() {
        val p = ShotParams(1.0, 20.0, 20.0)
        val c = Physics.speedOfSound(20.0)
        val r = Physics.compute(p, Physics.expectedDelay(20.0, 400.0, c), 0.00005) as ShotResult.Valid
        assertTrue(ResultWarning.NEAR_OR_ABOVE_SOUND_SPEED in r.warnings)
    }

    @Test fun arrondiAffichageNeModifiePasLaValeur() {
        val p = ShotParams(0.55, 10.0, 20.0)
        val c = Physics.speedOfSound(20.0)
        val dt = Physics.expectedDelay(10.0, 123.456789, c)
        val r = Physics.compute(p, dt, 0.0005) as ShotResult.Valid
        assertEquals(123.456789, r.velocity, 1e-6)
        // incertitude ≈ 0,76 m/s → 1 décimale
        assertEquals(1, Physics.decimalsFor(r.velocityUncertainty))
        assertEquals("123,5", Physics.formatValue(r.velocity, r.velocityUncertainty))
        assertEquals(0, Physics.decimalsFor(3.0))
        assertEquals(2, Physics.decimalsFor(0.05))
    }
}
