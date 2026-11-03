package com.doanh.chroni.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doanh.chroni.core.dsp.Quality
import com.doanh.chroni.core.model.DetectionMode
import com.doanh.chroni.core.model.Measurement
import com.doanh.chroni.core.physics.Physics
import com.doanh.chroni.ui.theme.ChroniColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val list by vm.history.collectAsState()
    var toDelete by remember { mutableStateOf<Measurement?>(null) }
    LaunchedEffect(Unit) { vm.refreshHistory() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Historique (${list.size})") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
    }) { pad ->
        if (list.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Aucune mesure pour l'instant.", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(m.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(Texts.date(m.createdAtMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (m.velocity != null && m.energy != null) {
                                Text(
                                    Physics.formatValue(m.velocity, m.velocityUncertainty ?: 1.0) + " m/s  ·  " +
                                        Physics.formatValue(m.energy, m.energyUncertainty ?: 1.0) + " J",
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                )
                            } else {
                                Text("Pas de résultat valide", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            }
                            Text(
                                Texts.num(m.massGrams, 3).trimEnd('0').trimEnd(',') + " g · " +
                                    Texts.num(m.distanceM, 2).trimEnd('0').trimEnd(',') + " m · " +
                                    (m.deltaTMs?.let { "Δt " + Texts.num(it, 2) + " ms" } ?: "Δt —"),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                val modeLabel = when (m.mode) {
                                    DetectionMode.AUTO -> "Auto"
                                    DetectionMode.CORRECTED -> "Corrigée"
                                    DetectionMode.MANUAL -> "Manuelle"
                                    DetectionMode.NONE -> "Sans repères"
                                }
                                AssistChip(onClick = { onOpen(m.id) }, label = { Text(modeLabel) })
                                val qc = when (m.detectionQuality) { Quality.GOOD -> ChroniColors.Cyan; Quality.FAIR -> ChroniColors.Amber; else -> ChroniColors.Red }
                                androidx.compose.foundation.Canvas(Modifier.size(10.dp)) { drawCircle(qc) }
                                Text(Texts.quality(m.detectionQuality), style = MaterialTheme.typography.labelMedium)
                                if (!m.audioKept) {
                                    Spacer(Modifier.size(4.dp))
                                    Icon(Icons.Default.MicOff, "Audio non conservé", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        IconButton(onClick = { toDelete = m }) { Icon(Icons.Default.Delete, "Supprimer") }
                    }
                }
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer cette mesure ?") },
            text = { Text("Mesure du ${Texts.date(m.createdAtMs)} et son enregistrement audio seront effacés.") },
            confirmButton = { TextButton(onClick = { vm.delete(m.id); toDelete = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}
