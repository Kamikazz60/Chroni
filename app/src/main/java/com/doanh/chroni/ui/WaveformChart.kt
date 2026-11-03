package com.doanh.chroni.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.doanh.chroni.core.markers.MarkerId
import com.doanh.chroni.core.markers.Viewport
import com.doanh.chroni.core.model.StoredEvent
import com.doanh.chroni.ui.theme.ChroniColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Pyramide min/max pré-calculée pour dessiner rapidement des millions d'échantillons. */
class WaveformData(val samples: ShortArray) {
    val block = 64
    val mins: ShortArray
    val maxs: ShortArray
    val globalPeak: Int

    init {
        val nb = (samples.size + block - 1) / block
        mins = ShortArray(nb); maxs = ShortArray(nb)
        var gp = 1
        for (b in 0 until nb) {
            var lo = Short.MAX_VALUE.toInt(); var hi = Short.MIN_VALUE.toInt()
            val end = min(samples.size, (b + 1) * block)
            for (i in b * block until end) { val v = samples[i].toInt(); if (v < lo) lo = v; if (v > hi) hi = v }
            mins[b] = lo.toShort(); maxs[b] = hi.toShort()
            gp = max(gp, max(abs(lo), abs(hi)))
        }
        globalPeak = gp
    }

    /** Min/max sur [from, to). */
    fun range(from: Int, to: Int): Pair<Int, Int> {
        val a = from.coerceIn(0, samples.size); val b = to.coerceIn(0, samples.size)
        if (b <= a) return 0 to 0
        var lo = Int.MAX_VALUE; var hi = Int.MIN_VALUE
        var i = a
        while (i < b) {
            if (i % block == 0 && i + block <= b) {
                val bi = i / block
                lo = min(lo, mins[bi].toInt()); hi = max(hi, maxs[bi].toInt()); i += block
            } else {
                val v = samples[i].toInt(); lo = min(lo, v); hi = max(hi, v); i++
            }
        }
        return lo to hi
    }
}

enum class AmplitudeScale { LINEAR, LOG }

/**
 * Graphique temporel interactif.
 * - glisser un repère (poignée en haut ou ligne verticale) : le déplace, avec une précision qui suit le zoom ;
 * - glisser ailleurs : défilement ; pincer : zoom ; double-toucher : zoom ×2 ;
 * - toucher un triangle de candidat : y place le repère sélectionné.
 */
@Composable
fun WaveformChart(
    data: WaveformData,
    sampleRate: Int,
    viewport: Viewport,
    onViewportChange: (Viewport) -> Unit,
    departure: Long?,
    impact: Long?,
    autoDeparture: Long?,
    autoImpact: Long?,
    selected: MarkerId,
    events: List<StoredEvent>,
    scale: AmplitudeScale,
    onMarkerDrag: (MarkerId, Long) -> Unit,
    onMarkerSelect: (MarkerId) -> Unit,
    onCandidateTap: (StoredEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handleHeightPx = with(density) { 30f * this.density }
    val touchSlopPx = with(density) { 28f * this.density }
    val candidateZonePx = with(density) { 26f * this.density }
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val vpState = rememberUpdatedState(viewport)
    val depState = rememberUpdatedState(departure)
    val impState = rememberUpdatedState(impact)
    val eventsState = rememberUpdatedState(events)
    val dragAccum = remember { DoubleArray(1) }
    val onVp by rememberUpdatedState(onViewportChange)
    val onDrag by rememberUpdatedState(onMarkerDrag)
    val onSelect by rememberUpdatedState(onMarkerSelect)
    val onCandidate by rememberUpdatedState(onCandidateTap)

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { sz ->
                if (sz.width > 0 && abs(sz.width - viewport.widthPx) > 0.5f) {
                    val vp = vpState.value
                    // Conserve la zone visible lors d'un changement de taille (rotation).
                    val visible = vp.visibleSamples
                    onVp(Viewport(data.samples.size, sz.width.toFloat(), vp.startSample,
                        if (vp.widthPx > 0) visible / sz.width else data.samples.size / sz.width.toDouble()).clamped())
                }
            }
            .pointerInput(data) {
                detectTapGestures(
                    onDoubleTap = { p -> onVp(vpState.value.zoom(2.0, p.x)) },
                    onTap = { p ->
                        val vp = vpState.value
                        if (p.y > size.height - candidateZonePx) {
                            val hit = eventsState.value.minByOrNull { abs(vp.xOf(it.onsetSample.toDouble()) - p.x) }
                            if (hit != null && abs(vp.xOf(hit.onsetSample.toDouble()) - p.x) < touchSlopPx) onCandidate(hit)
                        } else {
                            nearestMarker(p, vp, depState.value, impState.value, touchSlopPx, handleHeightPx)?.let { onSelect(it) }
                        }
                    },
                )
            }
            .pointerInput(data) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val grabbed = nearestMarker(down.position, vpState.value, depState.value, impState.value, touchSlopPx, handleHeightPx)
                    if (grabbed != null) {
                        onSelect(grabbed)
                        dragAccum[0] = ((if (grabbed == MarkerId.DEPARTURE) depState.value else impState.value) ?: 0L).toDouble()
                    }
                    var lastSpan = 0f
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            // Pincer pour zoomer (horizontalement).
                            val xs = pressed.map { it.position.x }
                            val span = (xs.max() - xs.min()).coerceAtLeast(1f)
                            val center = xs.average().toFloat()
                            if (lastSpan > 0f) onVp(vpState.value.zoom((span / lastSpan).toDouble(), center))
                            lastSpan = span
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1) {
                            lastSpan = 0f
                            val ch = pressed[0]
                            val dx = ch.positionChange().x
                            if (dx != 0f) {
                                if (grabbed != null) {
                                    dragAccum[0] += dx * vpState.value.samplesPerPx
                                    onDrag(grabbed, dragAccum[0].let { kotlin.math.round(it).toLong() })
                                } else {
                                    onVp(vpState.value.pan(dx))
                                }
                                ch.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        drawWaveform(data, viewport, scale, colors.primary.copy(alpha = 0.85f), colors.outline)
        drawTimeAxis(viewport, sampleRate, textMeasurer, colors.onSurfaceVariant, colors.outline)
        // Candidats (triangles en bas).
        for (e in events) {
            val x = viewport.xOf(e.onsetSample.toDouble())
            if (x < -10 || x > size.width + 10) continue
            val c = when (e.role) {
                com.doanh.chroni.core.dsp.EventRole.DEPARTURE -> ChroniColors.Departure
                com.doanh.chroni.core.dsp.EventRole.IMPACT -> ChroniColors.Impact
                com.doanh.chroni.core.dsp.EventRole.SPIKE, com.doanh.chroni.core.dsp.EventRole.IGNORED -> colors.outline
                else -> colors.onSurface.copy(alpha = 0.75f)
            }
            val y = size.height - 4f
            val path = Path().apply { moveTo(x, y - 16f); lineTo(x - 10f, y); lineTo(x + 10f, y); close() }
            drawPath(path, c)
        }
        // Positions automatiques d'origine (pointillés discrets) si modifiées.
        autoDeparture?.takeIf { it != departure }?.let { drawGhost(viewport.xOf(it.toDouble()), ChroniColors.Departure) }
        autoImpact?.takeIf { it != impact }?.let { drawGhost(viewport.xOf(it.toDouble()), ChroniColors.Impact) }
        departure?.let { drawMarker(viewport.xOf(it.toDouble()), "D", ChroniColors.Departure, selected == MarkerId.DEPARTURE, handleHeightPx, textMeasurer) }
        impact?.let { drawMarker(viewport.xOf(it.toDouble()), "I", ChroniColors.Impact, selected == MarkerId.IMPACT, handleHeightPx, textMeasurer) }
    }
}

private fun nearestMarker(p: Offset, vp: Viewport, dep: Long?, imp: Long?, slop: Float, handleH: Float): MarkerId? {
    val dx = dep?.let { abs(vp.xOf(it.toDouble()) - p.x) } ?: Float.MAX_VALUE
    val ix = imp?.let { abs(vp.xOf(it.toDouble()) - p.x) } ?: Float.MAX_VALUE
    // Zone de prise plus large sur la poignée (en haut).
    val limit = if (p.y < handleH * 1.6f) slop * 1.4f else slop
    val best = min(dx, ix)
    if (best > limit) return null
    return if (dx <= ix) MarkerId.DEPARTURE else MarkerId.IMPACT
}

private fun DrawScope.drawWaveform(d: WaveformData, vp: Viewport, scale: AmplitudeScale, color: Color, axis: Color) {
    val mid = size.height / 2f
    val half = size.height / 2f - 34f
    drawLine(axis.copy(alpha = 0.5f), Offset(0f, mid), Offset(size.width, mid), 1f)
    val w = size.width.toInt()
    val spp = vp.samplesPerPx
    // Normalisation : en linéaire, sur le pic visible (les impacts faibles restent lisibles en zoomant).
    val norm: Double = if (scale == AmplitudeScale.LINEAR) {
        val (lo, hi) = d.range(floor(vp.startSample).toInt(), ceil(vp.endSample).toInt() + 1)
        max(max(abs(lo), abs(hi)), 50).toDouble()
    } else d.globalPeak.toDouble()
    fun mapY(v: Int): Float {
        val a = v / norm
        val r = if (scale == AmplitudeScale.LINEAR) a else {
            val db = 20 * log10(abs(a).coerceAtLeast(1e-6))
            val m = ((db + 60) / 60).coerceIn(0.0, 1.0)
            if (a < 0) -m else m
        }
        return (mid - r * half).toFloat()
    }
    if (spp >= 1.0) {
        for (x in 0 until w) {
            val from = floor(vp.startSample + x * spp).toInt()
            val to = max(from + 1, floor(vp.startSample + (x + 1) * spp).toInt())
            if (from >= d.samples.size) break
            val (lo, hi) = d.range(from, to)
            drawLine(color, Offset(x.toFloat(), mapY(hi)), Offset(x.toFloat(), mapY(lo)), 1.2f)
        }
    } else {
        val first = max(0, floor(vp.startSample).toInt())
        val last = min(d.samples.size - 1, ceil(vp.endSample).toInt())
        val path = Path()
        for (i in first..last) {
            val x = vp.xOf(i.toDouble()); val y = mapY(d.samples[i].toInt())
            if (i == first) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2f))
        if (spp < 0.34) for (i in first..last) drawCircle(color, 3.5f, Offset(vp.xOf(i.toDouble()), mapY(d.samples[i].toInt())))
    }
}

private fun DrawScope.drawTimeAxis(vp: Viewport, fs: Int, tm: TextMeasurer, text: Color, grid: Color) {
    val visibleS = vp.visibleSamples / fs
    val target = visibleS / 5
    val step = niceStep(target)
    val startT = vp.startSample / fs
    var t = ceil(startT / step) * step
    val style = TextStyle(color = text, fontSize = 10.sp)
    val decimals = max(0, -floor(log10(step)).toInt())
    while (t <= startT + visibleS) {
        val x = vp.xOf(t * fs)
        drawLine(grid.copy(alpha = 0.35f), Offset(x, 34f), Offset(x, size.height - 26f), 1f)
        val label = if (step < 0.001) String.format(java.util.Locale.FRANCE, "%.${decimals - 3}f ms", t * 1000)
        else String.format(java.util.Locale.FRANCE, "%.${max(0, decimals)}f s", t)
        drawText(tm, label, Offset(x + 3f, size.height - 42f), style)
        t += step
    }
}

private fun niceStep(target: Double): Double {
    if (target <= 0) return 1.0
    val mag = 10.0.pow(floor(log10(target)))
    val f = target / mag
    return mag * when { f < 1.5 -> 1.0; f < 3.5 -> 2.0; f < 7.5 -> 5.0; else -> 10.0 }
}

private fun DrawScope.drawGhost(x: Float, c: Color) {
    if (x < 0 || x > size.width) return
    var y = 34f
    while (y < size.height - 26f) { drawLine(c.copy(alpha = 0.5f), Offset(x, y), Offset(x, y + 8f), 2f); y += 16f }
}

private fun DrawScope.drawMarker(x: Float, label: String, c: Color, selected: Boolean, handleH: Float, tm: TextMeasurer) {
    if (x < -40 || x > size.width + 40) return
    drawLine(c, Offset(x, 0f), Offset(x, size.height - 22f), if (selected) 4f else 2.5f)
    val w = handleH * 1.15f
    drawRoundRect(c, Offset(x - w / 2, 0f), Size(w, handleH), CornerRadius(handleH / 3))
    if (selected) drawRoundRect(Color.White, Offset(x - w / 2, 0f), Size(w, handleH), CornerRadius(handleH / 3), style = Stroke(3f))
    val layout = tm.measure(label, TextStyle(color = ChroniColors.Night, fontSize = 15.sp))
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, (handleH - layout.size.height) / 2f))
}
