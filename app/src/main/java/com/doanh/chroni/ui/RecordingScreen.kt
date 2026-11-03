package com.doanh.chroni.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.doanh.chroni.ui.theme.ChroniColors
import kotlin.math.log10

@Composable
fun RecordingScreen(vm: MainViewModel, onBack: () -> Unit, onResult: (String) -> Unit) {
    val state by vm.recording.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        if (vm.recording.value is RecordingState.Idle) vm.startRecording()
    }
    val pending by vm.pendingResult.collectAsState()
    LaunchedEffect(pending) { pending?.let { vm.consumePendingResult(); onResult(it) } }

    // Passage en arrière-plan : on arrête proprement (le micro serait coupé par Android).
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && (context as? Activity)?.isChangingConfigurations != true) vm.onInterrupted()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    BackHandler {
        when (state) {
            is RecordingState.Recording -> vm.stopRecording()
            is RecordingState.Error -> { vm.cancelRecordingError(); onBack() }
            is RecordingState.Analyzing -> Unit
            RecordingState.Idle -> onBack()
        }
    }

    Scaffold { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            when (val s = state) {
                is RecordingState.Recording -> RecordingContent(s, onStop = vm::stopRecording)
                RecordingState.Analyzing, RecordingState.Idle -> {
                    CircularProgressIndicator()
                    Text("Analyse du signal…", style = MaterialTheme.typography.titleMedium)
                }
                is RecordingState.Error -> {
                    Text("Mesure impossible", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Text(s.message, textAlign = TextAlign.Center)
                    Button(onClick = { vm.cancelRecordingError(); vm.startRecording() }) { Text("Réessayer") }
                    OutlinedButton(onClick = { vm.cancelRecordingError(); onBack() }) { Text("Retour") }
                }
            }
        }
    }
}

@Composable
private fun RecordingContent(s: RecordingState.Recording, onStop: () -> Unit) {
    val color = if (s.ready) ChroniColors.Red else ChroniColors.Amber
    Box(Modifier.size(22.dp).clip(CircleShape).background(color))
    Text(
        if (s.ready) "Enregistrement — Prêt, vous pouvez tirer" else "Mesure du bruit ambiant… patientez",
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
    )
    Text(
        Texts.num(s.elapsedS, 1) + " s / " + s.maxDurationS + " s",
        style = MaterialTheme.typography.displaySmall,
    )
    LinearProgressIndicator(
        progress = { (s.elapsedS / s.maxDurationS).toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
    )
    // Vu-mètre (échelle en dB pleine échelle, −60 à 0).
    val db = if (s.level > 0) 20 * log10(s.level) else -90.0
    val frac = ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Text("Niveau micro", style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(frac).height(14.dp).background(if (s.level > 0.97) ChroniColors.Red else MaterialTheme.colorScheme.primary))
        }
    }
    EventLine("Départ", s.departureS, ChroniColors.Departure)
    EventLine("Impact", s.impactS, ChroniColors.Impact)
    if (s.silenced) Text("Le micro est coupé par le système (autre application ?)", color = MaterialTheme.colorScheme.error)
    if (s.departureS != null) Text("Arrêt automatique après la fenêtre de recherche de l'impact.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onStop,
        colors = ButtonDefaults.buttonColors(containerColor = ChroniColors.Red, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(60.dp),
    ) {
        Icon(Icons.Default.Stop, null); Spacer(Modifier.size(8.dp)); Text("Arrêter et analyser", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EventLine(label: String, timeS: Double?, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (timeS != null) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (timeS != null) color else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.size(10.dp))
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(if (timeS != null) "détecté à ${Texts.num(timeS, 3)} s" else "en attente", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
