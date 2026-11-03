package com.doanh.chroni.core.dsp

import com.doanh.chroni.core.physics.Physics
import com.doanh.chroni.core.physics.ShotParams
import com.doanh.chroni.core.physics.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EventDetectorTest {
    private val fs = 48_000
    private val detector = EventDetector()
    private val c = Physics.speedOfSound(20.0)

    private fun window(d: Double) = SearchWindow.forShot(d, c, vMin = 30.0, vMax = 1500.0)

    @Test fun signalPropreDonneLaBonneVitesse() {
        val d = 10.0
        val v = 120.0
        val dt = Physics.expectedDelay(d, v, c)
        val sig = SyntheticSignal(fs, 2.0).noise(0.0005).blast(0.5, 0.8).ring(0.5 + dt, 0.15).toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = window(d))
        assertNotNull(r.departure); assertNotNull(r.impact)
        val depErr = abs(r.departure!!.onsetSample - (0.5 * fs).toLong())
        val impErr = abs(r.impact!!.onsetSample - ((0.5 + dt) * fs).toLong())
        assertTrue("erreur départ $depErr échantillons", depErr <= 10)
        assertTrue("erreur impact $impErr échantillons", impErr <= 10)
        val measured = (r.impact!!.onsetSample - r.departure!!.onsetSample).toDouble() / fs
        val res = Physics.compute(ShotParams(0.55, d, 20.0), measured, 0.0001) as ShotResult.Valid
        assertEquals(v, res.velocity, v * 0.01)
        assertEquals(Quality.GOOD, r.quality)
    }

    @Test fun fonctionneA44100Hz() {
        val f = 44_100
        val d = 15.0
        val dt = Physics.expectedDelay(d, 250.0, c)
        val sig = SyntheticSignal(f, 1.5).noise(0.0005).blast(0.4, 0.9).ring(0.4 + dt, 0.2).toShorts()
        val r = detector.detect(sig, sampleRate = f, window = window(d))
        val measured = (r.impact!!.onsetSample - r.departure!!.onsetSample).toDouble() / f
        assertEquals(dt, measured, 0.0003)
    }

    @Test fun echosDuDepartNeSontPasPrisPourLImpact() {
        val d = 10.0
        val dt = Physics.expectedDelay(d, 100.0, c) // ≈ 129 ms
        val sig = SyntheticSignal(fs, 2.0).noise(0.0005)
            .blast(0.5, 0.8)
            .echo(0.5, 0.045, 0.35) // écho de mur dans la fenêtre, avant l'impact
            .ring(0.5 + dt, 0.12)
            .toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = window(d))
        val imp = r.impact!!
        assertTrue(abs(imp.onsetSample - ((0.5 + dt) * fs).toLong()) <= 10)
    }

    @Test fun picIsoleRejete() {
        val d = 10.0
        val sig = SyntheticSignal(fs, 1.5).noise(0.0005)
            .blast(0.4, 0.8)
            .spike(0.5, 0.9) // pic d'un seul échantillon dans la fenêtre
            .toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = window(d))
        assertNull(r.impact)
        assertEquals(Quality.FAILED, r.quality)
        assertTrue(DetectionWarning.NO_IMPACT_IN_WINDOW in r.warnings)
        assertTrue(r.events.any { it.role == EventRole.SPIKE })
    }

    @Test fun silenceDonneUnEchecExplicite() {
        val sig = SyntheticSignal(fs, 1.0).noise(0.0005).toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = window(10.0))
        assertEquals(Quality.FAILED, r.quality)
        assertNull(r.departure)
        assertTrue(DetectionWarning.NO_EVENT in r.warnings)
    }

    @Test fun impactHorsFenetreNonRetenu() {
        val d = 10.0
        // impact 2,5 s après : v < 30 m/s → hors fenêtre
        val sig = SyntheticSignal(fs, 3.5).noise(0.0005).blast(0.4, 0.8).ring(2.9, 0.2).toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = window(d))
        assertNull(r.impact)
        assertTrue(r.events.any { it.role == EventRole.OUT_OF_WINDOW })
    }

    @Test fun deuxImpactsEquivalentsSignalesAmbigus() {
        val d = 10.0
        val sig = SyntheticSignal(fs, 2.0).noise(0.0005)
            .blast(0.4, 0.8).ring(0.55, 0.2).ring(0.65, 0.2).toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = window(d))
        assertNotNull(r.impact)
        assertTrue(DetectionWarning.AMBIGUOUS_IMPACT in r.warnings)
        assertEquals(Quality.POOR, r.quality)
    }

    @Test fun impactFaibleSignale() {
        val d = 10.0
        val dt = Physics.expectedDelay(d, 150.0, c)
        val sig = SyntheticSignal(fs, 2.0).noise(0.001).blast(0.4, 0.8).ring(0.4 + dt, 0.025).toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = window(d))
        assertNotNull(r.impact)
        assertTrue(r.quality == Quality.POOR || r.quality == Quality.FAIR)
    }

    @Test fun saturationSignalee() {
        val d = 10.0
        val dt = Physics.expectedDelay(d, 150.0, c)
        val sig = SyntheticSignal(fs, 2.0).noise(0.0005).blast(0.4, 3.0).ring(0.4 + dt, 0.2).toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = window(d))
        assertTrue(DetectionWarning.CLIPPING in r.warnings)
        val measured = (r.impact!!.onsetSample - r.departure!!.onsetSample).toDouble() / fs
        assertEquals(dt, measured, 0.0003)
    }

    @Test fun bruitDuBoutonDeDemarrageIgnore() {
        val d = 10.0
        val dt = Physics.expectedDelay(d, 150.0, c)
        val sig = SyntheticSignal(fs, 2.0).noise(0.0005)
            .blast(0.05, 0.9, decayMs = 3.0) // tapotement sur l'écran au démarrage
            .blast(0.6, 0.8).ring(0.6 + dt, 0.2).toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = window(d), ignoreBeforeSample = (0.3 * fs).toInt())
        assertTrue(abs(r.departure!!.onsetSample - (0.6 * fs).toLong()) <= 10)
    }

    @Test fun sansFenetreLePremierCandidatApresLeDepartEstRetenu() {
        val sig = SyntheticSignal(fs, 2.0).noise(0.0005).blast(0.4, 0.8).ring(0.9, 0.2).toShorts()
        val r = detector.detect(sig, sampleRate = fs, window = null)
        assertTrue(abs(r.impact!!.onsetSample - (0.9 * fs).toLong()) <= 10)
    }

    @Test fun bufferPartielAnalyse() {
        // Analyse en cours d'enregistrement : seule la partie déjà capturée compte.
        val sig = SyntheticSignal(fs, 2.0).noise(0.0005).blast(0.4, 0.8).ring(1.5, 0.2).toShorts()
        val r = detector.detect(sig, sampleCount = fs, sampleRate = fs, window = null)
        assertNotNull(r.departure)
        assertNull(r.impact)
    }
}
