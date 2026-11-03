package com.doanh.chroni.core.markers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerEditorTest {
    private val ed = MarkerEditor(sampleCount = 96_000, sampleRate = 48_000, autoDeparture = 24_000, autoImpact = 30_000, departure = 24_000, impact = 30_000)

    @Test fun deplacementEtBornes() {
        assertEquals(31_000L, ed.moveTo(MarkerId.IMPACT, 31_000).impact)
        assertEquals(0L, ed.moveTo(MarkerId.DEPARTURE, -50).departure)
        assertEquals(95_999L, ed.moveTo(MarkerId.IMPACT, 1_000_000).impact)
    }

    @Test fun decalageFin() {
        assertEquals(30_001L, ed.nudge(MarkerId.IMPACT, 1).impact)
        assertEquals(29_952L, ed.nudgeMs(MarkerId.IMPACT, -1.0).impact) // 1 ms = 48 échantillons
        assertEquals(24_005L, ed.nudgeMs(MarkerId.DEPARTURE, 0.1).departure) // 0,1 ms ≈ 4,8 → 5
    }

    @Test fun reinitialisationEtEtatModifie() {
        val moved = ed.moveTo(MarkerId.IMPACT, 31_234).moveTo(MarkerId.DEPARTURE, 23_000)
        assertTrue(moved.isModified)
        val back = moved.reset()
        assertFalse(back.isModified)
        assertEquals(24_000L, back.departure)
        assertEquals(30_000L, back.impact)
        // L'origine automatique n'est jamais modifiée par un déplacement.
        assertEquals(30_000L, moved.autoImpact)
    }

    @Test fun delaiEnEchantillons() {
        assertEquals(6_000L, ed.deltaSamples)
        assertEquals(0.5, ed.timeOf(24_000), 1e-12)
        val noAuto = MarkerEditor(1000, 48_000, null, null, null, null)
        assertNull(noAuto.deltaSamples)
        assertEquals(noAuto, noAuto.nudge(MarkerId.IMPACT, 5)) // rien à décaler
    }

    @Test fun candidatLePlusProche() {
        assertEquals(30_100L, ed.nearest(listOf(24_000, 30_100, 33_000), 30_500))
        assertEquals(30_100L, ed.snapTo(MarkerId.IMPACT, 30_100).impact)
    }

    @Test fun viewportZoomGardeLePointSousLeDoigt() {
        val vp = Viewport(sampleCount = 96_000, widthPx = 1000f)
        assertEquals(96.0, vp.samplesPerPx, 1e-9)
        val before = vp.sampleAt(500f)
        val z = vp.zoom(4.0, 500f)
        assertEquals(24.0, z.samplesPerPx, 1e-9)
        assertEquals(before, z.sampleAt(500f), 1e-6)
    }

    @Test fun viewportLimites() {
        val vp = Viewport(sampleCount = 96_000, widthPx = 1000f)
        val maxZoom = vp.zoom(1e9, 0f)
        assertEquals(Viewport.MIN_SAMPLES_PER_PX, maxZoom.samplesPerPx, 1e-12)
        val panned = vp.zoom(10.0, 0f).pan(-1e9f)
        assertEquals(96_000.0 - panned.visibleSamples, panned.startSample, 1e-6)
        assertEquals(0.0, vp.zoom(10.0, 0f).pan(1e9f).startSample, 1e-9)
        assertEquals(vp.maxSamplesPerPx, maxZoom.full().samplesPerPx, 1e-9)
    }

    @Test fun viewportCadrage() {
        val vp = Viewport(sampleCount = 96_000, widthPx = 1000f).fit(24_000, 30_000)
        assertTrue(vp.startSample <= 24_000 && vp.endSample >= 30_000)
        val x = vp.xOf(24_000.0)
        assertEquals(24_000.0, vp.sampleAt(x), 1e-2) // la position écran est un Float
    }

    @Test fun precisionDuDeplacementSelonLeZoom() {
        // À fort zoom, un déplacement de 1 pixel correspond à moins d'un échantillon.
        val vp = Viewport(96_000, 1000f).zoom(1e9, 0f)
        val delta = (vp.sampleAt(1f) - vp.sampleAt(0f))
        assertEquals(0.125, delta, 1e-12)
    }
}
