package com.doanh.chroni.data

import com.doanh.chroni.core.dsp.EventDetector
import com.doanh.chroni.core.dsp.Quality
import com.doanh.chroni.core.dsp.SearchWindow
import com.doanh.chroni.core.dsp.SyntheticSignal
import com.doanh.chroni.core.model.DetectionMode
import com.doanh.chroni.core.model.MeasurementFactory
import com.doanh.chroni.core.model.TemperatureSource
import com.doanh.chroni.core.physics.Physics
import com.doanh.chroni.core.physics.ShotParams
import com.doanh.chroni.core.physics.ShotResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MeasurementRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val fs = 48_000
    private val c = Physics.speedOfSound(18.0)
    private val params = ShotParams(0.55, 10.0, 18.0)
    private val dt = Physics.expectedDelay(10.0, 110.0, c)
    private val audio = SyntheticSignal(fs, 2.0).noise(0.0005).blast(0.5, 0.8).ring(0.5 + dt, 0.15).toShorts()

    private fun measurement(id: String = "m1", now: Long = 1_000L) = MeasurementFactory.create(
        id = id, nowMs = now, params = params, temperatureSource = TemperatureSource.MANUAL,
        sampleRate = fs, sampleCount = audio.size, audioSourceName = "TEST",
        detection = EventDetector().detect(audio, sampleRate = fs, window = SearchWindow.forShot(10.0, c, 30.0, 1500.0)),
        recordingWarnings = emptyList(), audioKept = true, vMin = 30.0, vMax = 1500.0, appVersion = "test",
    )

    @Test fun mesureAutomatiqueCalculee() {
        val m = measurement()
        assertEquals(DetectionMode.AUTO, m.mode)
        assertEquals(Quality.GOOD, m.detectionQuality)
        assertEquals(110.0, m.velocity!!, 1.5)
        assertEquals(0.5 * 0.00055 * m.velocity!! * m.velocity!!, m.energy!!, 1e-9)
    }

    @Test fun recalculApresCorrection() {
        val m = measurement()
        val shift = 48L // +1 ms sur l'impact → délai plus long → vitesse plus faible
        val corrected = m.withMarkers(m.departureSample, m.impactSample!! + shift, confirmed = true, nowMs = 2_000L)
        assertEquals(DetectionMode.CORRECTED, corrected.mode)
        assertTrue(corrected.velocity!! < m.velocity!!)
        assertEquals(m.deltaTMs!! + 1.0, corrected.deltaTMs!!, 1e-9)
        val expected = (Physics.compute(params, corrected.deltaTMs!! / 1000.0, 0.0) as ShotResult.Valid).velocity
        assertEquals(expected, corrected.velocity!!, 1e-9)
        // L'origine automatique reste intacte.
        assertEquals(m.autoImpactSample, corrected.autoImpactSample)
    }

    @Test fun correctionIncoherenteNAffichePasDeResultat() {
        val m = measurement()
        val bad = m.withMarkers(m.impactSample, m.departureSample, confirmed = false, nowMs = 2_000L)
        assertNull(bad.velocity)
        assertTrue(bad.deltaTMs!! < 0)
        assertTrue(bad.evaluate() is ShotResult.Invalid)
    }

    @Test fun sauvegardeEtReouverture() {
        val repo = MeasurementRepository(tmp.root)
        val m = measurement()
        repo.save(m)
        repo.saveAudio(m.id, audio)
        val reopened = repo.load(m.id)!!
        assertEquals(m, reopened)
        assertArrayEquals(audio, repo.loadAudio(m.id))

        // Correction puis réouverture : automatique et corrigé distingués.
        val corrected = reopened.withMarkers(reopened.departureSample, reopened.impactSample!! + 10, true, 3_000L)
        repo.save(corrected)
        val again = repo.load(m.id)!!
        assertEquals(DetectionMode.CORRECTED, again.mode)
        assertEquals(m.autoImpactSample, again.autoImpactSample)
        assertEquals(m.impactSample!! + 10, again.impactSample)
        assertTrue(again.confirmed)
    }

    @Test fun listeTrieeEtSuppression() {
        val repo = MeasurementRepository(tmp.root)
        repo.save(measurement("a", 1_000)); repo.saveAudio("a", audio)
        repo.save(measurement("b", 5_000)); repo.saveAudio("b", audio)
        assertEquals(listOf("b", "a"), repo.list().map { it.id })
        assertTrue(repo.audioBytes() >= audio.size * 2L * 2)
        repo.delete("a")
        assertEquals(listOf("b"), repo.list().map { it.id })
        assertFalse(repo.hasAudio("a"))
    }

    @Test fun suppressionDesEnregistrementsAudio() {
        val repo = MeasurementRepository(tmp.root)
        repo.save(measurement("a")); repo.saveAudio("a", audio)
        assertEquals(1, repo.deleteAllAudio())
        assertNull(repo.loadAudio("a"))
        val m = repo.load("a")
        assertNotNull(m)
        assertFalse(m!!.audioKept)
        assertNotNull(m.velocity) // la mesure reste consultable
        assertEquals(0L, repo.audioBytes())
    }

    @Test fun fichierCorrompuIgnore() {
        val repo = MeasurementRepository(tmp.root)
        repo.save(measurement("ok"))
        java.io.File(tmp.root, "measurements/bad.json").writeText("{pas du json")
        assertEquals(listOf("ok"), repo.list().map { it.id })
        assertNull(repo.load("bad"))
    }
}
