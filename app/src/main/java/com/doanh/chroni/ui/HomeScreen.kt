package com.doanh.chroni.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.doanh.chroni.R
import com.doanh.chroni.core.physics.InputError
import com.doanh.chroni.core.physics.Physics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.inputs.collectAsState()
    val settings by vm.settingsState.collectAsState()
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var showAudioNotice by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart() else permissionDenied = true
    }

    fun requestStart() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) onStart() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_chroni_mark), contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.size(10.dp))
                        Text("Chroni", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.Default.History, contentDescription = "Historique") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Paramètres et informations") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Chronographe acoustique", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            NumberField("Masse du projectile", "g", state.mass, vm::onMassChange,
                state.errors.firstOrNull { it == InputError.MASS })
            NumberField("Distance bouche → cible", "m", state.distance, vm::onDistanceChange,
                state.errors.firstOrNull { it == InputError.DISTANCE })
            NumberField("Température de l'air (facultatif)", "°C", state.temperature, vm::onTemperatureChange,
                state.errors.firstOrNull { it == InputError.TEMPERATURE }, last = true)

            val tSource = when {
                state.temperature.isBlank() -> "Source : valeur nominale ${Texts.num(Physics.NOMINAL_TEMPERATURE_C, 0)} °C " +
                    "(vitesse du son ${Texts.num(Physics.speedOfSound(Physics.NOMINAL_TEMPERATURE_C), 1)} m/s)"
                state.temperatureFromSensor -> "Source : capteur du téléphone"
                else -> "Source : saisie manuelle"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tSource, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (settings.hasTemperatureSensor) {
                    TextButton(onClick = vm::readTemperatureSensor) {
                        Icon(Icons.Default.Thermostat, null); Spacer(Modifier.size(4.dp)); Text("Lire le capteur")
                    }
                }
            }

            Button(
                onClick = {
                    if (vm.validateInputs()) {
                        if (vm.showAudioNotice && settings.keepAudio) showAudioNotice = true else requestStart()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Default.Mic, null); Spacer(Modifier.size(10.dp))
                Text("Lancer une mesure", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.History, null); Spacer(Modifier.size(8.dp)); Text("Historique des mesures")
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text("Pour une mesure fiable", fontWeight = FontWeight.SemiBold)
                    }
                    Text("• Posez le téléphone près du tireur, idéalement juste derrière la bouche, dans l'axe du tir : son recul s'annule alors dans le calcul. Évitez de le poser sur le côté (1 m d'écart ≈ 3 % d'erreur).", style = MaterialTheme.typography.bodySmall)
                    Text("• Utilisez une cible sonore (plaque métallique) : l'impact doit s'entendre nettement.", style = MaterialTheme.typography.bodySmall)
                    Text("• Attendez « Prêt » avant de tirer, puis restez silencieux jusqu'à l'arrêt.", style = MaterialTheme.typography.bodySmall)
                    Text("• Résultat expérimental, non certifié : plus la distance est grande, meilleure est la précision.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (settings.keepAudio) "Les enregistrements audio sont conservés sur ce téléphone pour pouvoir corriger les mesures (${Texts.bytes(settings.audioBytes)} utilisés). Modifiable dans les paramètres."
                        else "Les enregistrements audio ne sont pas conservés : le graphique n'est disponible que juste après la mesure.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showAudioNotice) {
        AlertDialog(
            onDismissRequest = { showAudioNotice = false },
            title = { Text("Conservation de l'audio") },
            text = { Text("Chroni conserve l'enregistrement de chaque mesure sur ce téléphone (environ 1 Mo pour 10 s), uniquement pour pouvoir rouvrir et corriger le graphique plus tard. Rien n'est envoyé sur Internet. Vous pouvez désactiver cette conservation ou tout supprimer dans les paramètres.") },
            confirmButton = { TextButton(onClick = { showAudioNotice = false; vm.dismissAudioNotice(); requestStart() }) { Text("Compris") } },
            dismissButton = { TextButton(onClick = { showAudioNotice = false; vm.dismissAudioNotice(); onSettings() }) { Text("Paramètres") } },
        )
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            title = { Text("Microphone nécessaire") },
            text = { Text("Chroni a besoin du microphone pour mesurer le délai entre le départ et l'impact. L'audio reste sur le téléphone. Si vous avez refusé définitivement, autorisez le micro dans les réglages de l'application.") },
            confirmButton = {
                TextButton(onClick = {
                    permissionDenied = false
                    context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text("Ouvrir les réglages") }
            },
            dismissButton = { TextButton(onClick = { permissionDenied = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun NumberField(label: String, unit: String, value: String, onChange: (String) -> Unit, error: InputError?, last: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }) },
        label = { Text(label) },
        suffix = { Text(unit) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(Texts.inputError(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}
