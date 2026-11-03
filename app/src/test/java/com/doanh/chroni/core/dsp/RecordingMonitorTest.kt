package com.doanh.chroni.core.dsp

import com.doanh.chroni.core.physics.Physics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMonitorTest {
    private val fs = 48_000
    private val c = Physics.speedOfSound(20.0)
    private val window = SearchWindow.forShot(10.0, c, 20.0, 1500.0) // max ≈ 0,529 s

    @Test fun arretAutomatiqueApresLaFenetreComplete() {
        val sig = SyntheticSignal(fs, 3.0).noise(0.0005).blast(0.8, 0.8).ring(0.9, 0.2).toShorts()
        val det = EventDetector()
        val stopSample = ((0.8 + window.maxDelayS + RecordingMonitor.STOP_MARGIN_S) * fs).toInt()

        val early = det.detect(sig, stopSample - 2000, fs, window, (RecordingMonitor.IGNORE_START_S * fs).toInt())
        val s1 = RecordingMonitor.status(early, stopSample - 2000, fs, window, autoStop = true)
        assertTrue(s1.ready)
        assertEquals(0.8, s1.departureS!!, 0.001)
        assertEquals(0.9, s1.impactS!!, 0.001)
        assertFalse(s1.shouldAutoStop)

        val late = det.detect(sig, stopSample + 100, fs, window, (RecordingMonitor.IGNORE_START_S * fs).toInt())
        assertTrue(RecordingMonitor.status(late, stopSample + 100, fs, window, autoStop = true).shouldAutoStop)
        assertFalse(RecordingMonitor.status(late, stopSample + 100, fs, window, autoStop = false).shouldAutoStop)
    }

    @Test fun pasPretAuDebutEtPasDArretSansDepart() {
        val s = RecordingMonitor.status(null, fs / 10, fs, window, true)
        assertFalse(s.ready)
        assertNull(s.departureS)
        assertFalse(s.shouldAutoStop)
    }
}
