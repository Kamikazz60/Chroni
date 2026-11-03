package com.doanh.chroni.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.doanh.chroni.core.dsp.EventRole
import com.doanh.chroni.core.markers.MarkerEditor
import com.doanh.chroni.core.markers.MarkerId
import com.doanh.chroni.core.markers.Viewport
import com.doanh.chroni.core.model.Measurement
import com.doanh.chroni.core.physics.Physics
import com.doanh.chroni.core.physics.ShotResult
import com.doanh.chroni.ui.theme.ChroniColors
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    var m by remember { mutableStateOf<Measurement?>(null) }
    var data by remember { mutableStateOf<WaveformData?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        val mm = vm.loadMeasurement(id)
        val audio = vm.loadAudio(id)
        if (mm == null || audio == null) { loadFailed = true; return@LaunchedEffect }
        data = WaveformData(audio)
        m = mm
    }

    val mm = m
    val d = data
    if (mm == null || d == null) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Analyse") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } })
        }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (loadFailed) Text("Enregistrement audio indisponible (non conservé ou supprimé).", textAlign = TextAlign.Center)
                else CircularProgressIndicator()
            }
        }
        return
    }
    AnalysisContent(vm, mm, d, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisContent(vm: MainViewModel, m: Measurement, data: WaveformData, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    // État sauvegardé (rotation) : uniquement des indices d'échantillons, jamais des pixels.
    var dep by rememberSaveable { mutableStateOf(m.departureSample ?: -1L) }
    var imp by rememberSaveable { mutableStateOf(m.impactSample ?: -1L) }
    var selectedName by rememberSaveable { mutableStateOf(if (m.departureSample == null) MarkerId.DEPARTURE.name else MarkerId.IMPACT.name) }
    var vpStart by rememberSaveable { mutableStateOf(-1.0) }
    var vpSpp by rememberSaveable { mutableStateOf(-1.0) }
    var logScale by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var chartWidth by remember { mutableStateOf(0f) }

    val editor = MarkerEditor(
        sampleCount = m.sampleCount, sampleRate = m.sampleRate,
        autoDeparture = m.autoDepartureSample, autoImpact = m.autoImpactSample,
        departure = dep.takeIf { it >= 0 }, impact = imp.takeIf { it >= 0 },
        selected = MarkerId.valueOf(selectedName),
    )
    fun apply(e: MarkerEditor) {
        dep = e.departure ?: -1L; imp = e.impact ?: -1L; selectedName = e.selected.name
    }

    val viewport = if (chartWidth > 0 && vpSpp > 0) Viewport(m.sampleCount, chartWidth, vpStart, vpSpp).clamped()
    else Viewport(m.sampleCount, chartWidth.coerceAtLeast(1f)).let { base ->
        val a = editor.departure; val b = editor.impact
        if (a != null && b != null && chartWidth > 0) base.fit(a, b, 0.3) else base
    }
    fun setVp(v: Viewport) {
        // Premier affichage : cadrage automatique sur départ et impact.
        val a = editor.departure; val b = editor.impact
        val target = if (vpSpp < 0 && a != null && b != null) Viewport(m.sampleCount, v.widthPx).fit(a, b, 0.3) else v
        chartWidth = target.widthPx; vpStart = target.startSample; vpSpp = target.samplesPerPx
    }

    val changed = editor.departure != m.departureSample || editor.impact != m.impactSample
    val result = m.evaluate(editor.departure, editor.impact)

    BackHandler { if (changed) confirmLeave = true else onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analyse acoustique") },
                navigationIcon = { IconButton(onClick = { if (changed) confirmLeave = true else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { apply(editor.reset()) }, enabled = editor.isModified) {
                        Icon(Icons.Default.Restore, "Revenir aux positions automatiques")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LiveResult(result, editor)
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
                    .testTag("waveform"),
            ) {
                WaveformChart(
                    data = data, sampleRate = m.sampleRate, viewport = viewport, onViewportChange = ::setVp,
                    departure = editor.departure, impact = editor.impact,
                    autoDeparture = m.autoDepartureSample, autoImpact = m.autoImpactSample,
                    selected = editor.selected, events = m.events,
                    scale = if (logScale) AmplitudeScale.LOG else AmplitudeScale.LINEAR,
                    onMarkerDrag = { id, s -> apply(editor.moveTo(id, s)) },
                    onMarkerSelect = { apply(editor.select(it)) },
                    onCandidateTap = { e -> apply(editor.snapTo(editor.selected, e.onsetSample)) },
                )
            }
            Controls(
                editor = editor, m = m, viewport = viewport, logScale = logScale,
                onEditor = ::apply, onViewport = ::setVp, onToggleScale = { logScale = !logScale },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { apply(editor.reset()) }, enabled = editor.isModified, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, null); Spacer(Modifier.size(6.dp)); Text("Auto")
                }
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            vm.saveMarkers(m.id, editor.departure, editor.impact)
                            // La navigation doit toujours se faire sur le fil principal.
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                saving = false
                                onBack()
                            }
                        }
                    },
                    enabled = !saving && (changed || !m.confirmed),
                    modifier = Modifier.weight(2f),
                ) {
                    Icon(Icons.Default.Check, null); Spacer(Modifier.size(6.dp)); Text("Confirmer et enregistrer")
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Modifications non enregistrées") },
            text = { Text("Quitter sans enregistrer les nouvelles positions des repères ?") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Quitter") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Rester") } },
        )
    }
}

@Composable
private fun LiveResult(r: ShotResult, e: MarkerEditor) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val dt = e.deltaSamples?.let { Physics.secondsToMilliseconds(it.toDouble() / e.sampleRate) }
        Column(Modifier.weight(1f)) {
            Text("Δt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dt?.let { Texts.num(it, 2) + " ms" } ?: "—", fontWeight = FontWeight.Bold)
        }
        when (r) {
            is ShotResult.Valid -> {
                Column(Modifier.weight(1f)) {
                    Text("Vitesse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Physics.formatValue(r.velocity, r.velocityUncertainty) + " m/s", fontWeight = FontWeight.Bold, color = ChroniColors.Departure)
                }
                Column(Modifier.weight(1f)) {
                    Text("Énergie", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Physics.formatValue(r.energy, r.energyUncertainty) + " J", fontWeight = FontWeight.Bold, color = ChroniColors.Impact)
                }
            }
            is ShotResult.Invalid -> Text(Texts.invalid(r.reason), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(
    editor: MarkerEditor,
    m: Measurement,
    viewport: Viewport,
    logScale: Boolean,
    onEditor: (MarkerEditor) -> Unit,
    onViewport: (Viewport) -> Unit,
    onToggleScale: () -> Unit,
) {
    val sel = editor.selected
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Choix du repère + temps affiché.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf(MarkerId.DEPARTURE, MarkerId.IMPACT).forEachIndexed { i, id ->
                val pos = editor.position(id)
                val label = (if (id == MarkerId.DEPARTURE) "Départ" else "Impact") +
                    (pos?.let { " " + Texts.num(editor.timeOf(it), 4) + " s" } ?: " (non placé)")
                SegmentedButton(
                    selected = sel == id,
                    onClick = { onEditor(editor.select(id)) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    icon = { Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (id == MarkerId.DEPARTURE) ChroniColors.Departure else ChroniColors.Impact)) },
                ) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
        }
        // Réglage fin du repère sélectionné.
        val selPos = editor.position(sel)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            NudgeButton("−1 ms", selPos != null) { onEditor(editor.nudgeMs(sel, -1.0)) }
            NudgeButton("−1 éch.", selPos != null) { onEditor(editor.nudge(sel, -1)) }
            NudgeButton("+1 éch.", selPos != null) { onEditor(editor.nudge(sel, 1)) }
            NudgeButton("+1 ms", selPos != null) { onEditor(editor.nudgeMs(sel, 1.0)) }
        }
        if (selPos == null) {
            OutlinedButton(onClick = { onEditor(editor.moveTo(sel, (viewport.startSample + viewport.visibleSamples / 2).toLong())) }, modifier = Modifier.fillMaxWidth()) {
                Text("Placer le repère au centre de la vue")
            }
        }
        // Navigation dans le signal.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onViewport(viewport.zoom(0.5, viewport.widthPx / 2)) }) { Icon(Icons.Default.ZoomOut, "Dézoomer") }
            IconButton(onClick = { onViewport(viewport.zoom(2.0, viewport.widthPx / 2)) }) { Icon(Icons.Default.ZoomIn, "Zoomer") }
            IconButton(onClick = { selPos?.let { onViewport(viewport.zoom(1.0, 0f).copy(samplesPerPx = 0.5).clamped().centerOn(it)) } }, enabled = selPos != null) {
                Icon(Icons.Default.CenterFocusStrong, "Zoom fort sur le repère sélectionné")
            }
            IconButton(onClick = {
                val a = editor.departure; val b = editor.impact
                if (a != null && b != null) onViewport(viewport.fit(a, b, 0.3))
            }, enabled = editor.departure != null && editor.impact != null) { Icon(Icons.Default.FitScreen, "Cadrer départ et impact") }
            IconButton(onClick = { onViewport(viewport.full()) }) { Icon(Icons.Default.ZoomOutMap, "Signal entier") }
            FilterChip(selected = logScale, onClick = onToggleScale, label = { Text("dB") })
        }
        // Sélection précise parmi les pics proches.
        val near = m.events
            .filter { it.role != EventRole.IGNORED }
            .sortedBy { abs(it.onsetSample - (selPos ?: (viewport.startSample + viewport.visibleSamples / 2).toLong())) }
            .take(8)
            .sortedBy { it.onsetSample }
        if (near.isNotEmpty()) {
            Text("Pics détectés — touchez pour y placer le repère « ${if (sel == MarkerId.DEPARTURE) "Départ" else "Impact"} »",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                near.forEach { e ->
                    val isAt = selPos == e.onsetSample
                    val c = when (e.role) { EventRole.DEPARTURE -> ChroniColors.Departure; EventRole.IMPACT -> ChroniColors.Impact; else -> MaterialTheme.colorScheme.outline }
                    OutlinedButton(
                        onClick = { onEditor(editor.snapTo(sel, e.onsetSample)); onViewport(viewport.centerOn(e.onsetSample)) },
                        border = BorderStroke(if (isAt) 2.dp else 1.dp, c),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Texts.num(e.onsetSample.toDouble() / m.sampleRate * 1000, 1) + " ms", style = MaterialTheme.typography.labelMedium)
                            Text(Texts.role(e.role) + " · " + Texts.num(e.snrDb, 0) + " dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Text("Glissez la poignée D ou I pour déplacer un repère, glissez ailleurs pour défiler, pincez pour zoomer.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NudgeButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
}
