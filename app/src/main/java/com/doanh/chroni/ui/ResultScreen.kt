package com.doanh.chroni.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doanh.chroni.core.dsp.Quality
import com.doanh.chroni.core.model.Measurement
import com.doanh.chroni.core.physics.Physics
import com.doanh.chroni.core.physics.ShotResult
import com.doanh.chroni.ui.theme.ChroniColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    vm: MainViewModel,
    id: String,
    onBack: () -> Unit,
    onAnalysis: () -> Unit,
    onNewMeasurement: () -> Unit,
) {
    val history by vm.history.collectAsState()
    var loaded by remember { mutableStateOf<Measurement?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var hasAudio by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        loaded = vm.loadMeasurement(id)
        notFound = loaded == null
        hasAudio = vm.loadAudio(id) != null
    }
    val m = history.firstOrNull { it.id == id } ?: loaded

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Résultat") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = { if (m != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Supprimer la mesure") } },
            )
        },
    ) { pad ->
        if (m == null) {
            Column(Modifier.padding(pad).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (notFound) Text("Mesure introuvable.") else CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ResultBody(m)
            Button(onClick = onAnalysis, enabled = hasAudio, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Default.GraphicEq, null); Spacer(Modifier.size(8.dp))
                Text(if (hasAudio) "Graphique acoustique / corriger" else "Graphique indisponible (audio non conservé)")
            }
            OutlinedButton(onClick = onNewMeasurement, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Mic, null); Spacer(Modifier.size(8.dp)); Text("Nouvelle mesure")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette mesure ?") },
            text = { Text("La mesure et son enregistrement audio seront définitivement effacés.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id); onBack() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun ResultBody(m: Measurement) {
    val r = m.evaluate()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (r) {
                is ShotResult.Valid -> {
                    BigValue("Vitesse estimée", Physics.formatValue(r.velocity, r.velocityUncertainty), "m/s",
                        "± " + Physics.formatValue(r.velocityUncertainty, r.velocityUncertainty), ChroniColors.Departure)
                    Spacer(Modifier.height(6.dp))
                    BigValue("Énergie estimée", Physics.formatValue(r.energy, r.energyUncertainty), "J",
                        "± " + Physics.formatValue(r.energyUncertainty, r.energyUncertainty), ChroniColors.Impact)
                    Text("Incertitude liée au chronométrage et à la température uniquement (la distance saisie est supposée exacte).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is ShotResult.Invalid -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
                        Text("Pas de résultat valide", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    }
                    Text(Texts.invalid(r.reason))
                    Text("Ouvrez le graphique pour placer ou corriger les repères.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    QualityBadge(m)

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Détails", fontWeight = FontWeight.SemiBold)
            val dtMs = if (m.departureSample != null && m.impactSample != null)
                Physics.secondsToMilliseconds((m.impactSample - m.departureSample).toDouble() / m.sampleRate) else null
            InfoRow("Délai brut mesuré", dtMs?.let { Texts.num(it, 2) + " ms" } ?: "—")
            if (r is ShotResult.Valid) {
                InfoRow("Temps de vol du projectile", Texts.num(r.projectileTimeS * 1000, 2) + " ms")
                InfoRow("Retour du son de l'impact", Texts.num(r.soundTimeS * 1000, 2) + " ms")
            } else if (r is ShotResult.Invalid && r.soundTimeS != null) {
                InfoRow("Retour du son de l'impact", Texts.num(r.soundTimeS * 1000, 2) + " ms")
            }
            HorizontalDivider()
            InfoRow("Masse", Texts.num(m.massGrams, if (m.massGrams < 10) 3 else 1).trimEnd('0').trimEnd(',') + " g")
            InfoRow("Distance", Texts.num(m.distanceM, 2).trimEnd('0').trimEnd(',') + " m")
            val t = m.temperatureC ?: Physics.NOMINAL_TEMPERATURE_C
            InfoRow("Température", Texts.num(t, 1) + " °C (" + Texts.temperatureSource(m.temperatureSource) + ")")
            InfoRow("Vitesse du son utilisée", Texts.num(Physics.speedOfSound(t), 1) + " m/s")
            HorizontalDivider()
            InfoRow("Mode", Texts.mode(m.mode) + if (m.confirmed) " — confirmée" else "")
            InfoRow("Audio", "${m.sampleRate} Hz, ${Texts.num(m.durationS, 1)} s, source ${m.audioSourceName}")
            InfoRow("Bruit de fond", Texts.num(m.noiseFloorDb, 0) + " dBFS")
            InfoRow("Date", Texts.date(m.createdAtMs))
        }
    }

    val warnings = buildList {
        m.detectionWarnings.forEach { add(Texts.detectionWarning(it)) }
        m.recordingWarnings.forEach { add(Texts.recordingWarning(it)) }
        if (r is ShotResult.Valid) r.warnings.forEach { add(Texts.resultWarning(it)) }
    }
    if (warnings.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Avertissements", fontWeight = FontWeight.SemiBold)
                warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    Text(
        "Hypothèse : le téléphone est près de la bouche du canon et le son de l'impact parcourt la distance saisie. Estimation expérimentale, non certifiée.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun QualityBadge(m: Measurement) {
    val (color, label) = when (m.detectionQuality) {
        Quality.GOOD -> ChroniColors.Cyan to "Qualité de détection : bonne"
        Quality.FAIR -> ChroniColors.Amber to "Qualité de détection : moyenne"
        Quality.POOR -> ChroniColors.Red to "Qualité de détection : faible — vérifiez le graphique"
        Quality.FAILED -> ChroniColors.Red to "Détection automatique en échec"
    }
    val suffix = if (m.mode == com.doanh.chroni.core.model.DetectionMode.CORRECTED || m.mode == com.doanh.chroni.core.model.DetectionMode.MANUAL) " (repères ajustés à la main)" else ""
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(12.dp)) { drawCircle(color) }
        Spacer(Modifier.size(8.dp))
        Text(label + suffix, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BigValue(label: String, value: String, unit: String, unc: String, accent: Color) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.size(6.dp))
        Text(unit, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        Spacer(Modifier.size(10.dp))
        Text(unc, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f).padding(end = 8.dp))
        Text(value, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(0.55f))
    }
}
