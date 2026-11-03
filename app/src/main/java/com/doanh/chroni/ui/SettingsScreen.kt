package com.doanh.chroni.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.doanh.chroni.BuildConfig
import com.doanh.chroni.data.Settings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settingsState.collectAsState()
    var confirmAudio by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    var vMinText by rememberSaveable { mutableStateOf(Texts.num(s.vMin, 0)) }
    var vMaxText by rememberSaveable { mutableStateOf(Texts.num(s.vMax, 0)) }
    var rangeError by remember { mutableStateOf(false) }
    var rangeSaved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshHistory() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Paramètres et informations") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Mesure") {
                SwitchRow("Arrêt automatique", "S'arrête une fois la fenêtre de recherche de l'impact écoulée après le départ.", s.autoStop, vm::setAutoStop)
                Text("Durée maximale d'enregistrement : ${s.maxDurationS} s")
                Slider(
                    value = s.maxDurationS.toFloat(),
                    onValueChange = { vm.setMaxDuration(it.roundToInt()) },
                    valueRange = Settings.MIN_DURATION.toFloat()..Settings.MAX_DURATION.toFloat(),
                )
                Text("Plage de vitesses plausibles (fenêtre de recherche de l'impact)", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(vMinText, { vMinText = it; rangeSaved = false }, label = { Text("Min") }, suffix = { Text("m/s") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), isError = rangeError)
                    OutlinedTextField(vMaxText, { vMaxText = it; rangeSaved = false }, label = { Text("Max") }, suffix = { Text("m/s") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), isError = rangeError)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { val ok = vm.setVelocityRange(vMinText, vMaxText); rangeError = !ok; rangeSaved = ok }) { Text("Appliquer") }
                    if (rangeError) Text("Plage invalide (5 à 2000 m/s, min < max)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (rangeSaved) Text("Enregistré", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Section("Données") {
                SwitchRow(
                    "Conserver l'audio des mesures",
                    "Nécessaire pour rouvrir et corriger le graphique plus tard. Stocké uniquement sur ce téléphone (≈ 1 Mo pour 10 s).",
                    s.keepAudio, vm::setKeepAudio,
                )
                Text("Espace utilisé par l'audio : ${Texts.bytes(s.audioBytes)}")
                OutlinedButton(onClick = { confirmAudio = true }, modifier = Modifier.fillMaxWidth()) { Text("Supprimer tous les enregistrements audio") }
                OutlinedButton(onClick = { confirmAll = true }, modifier = Modifier.fillMaxWidth()) { Text("Effacer tout l'historique") }
            }

            Section("Modèle physique") {
                Para("Le téléphone entend le départ, puis le bruit de l'impact. Le délai mesuré Δt contient le vol du projectile et le retour du son :")
                Para("Δt = D/v + D/c   donc   v = D / (Δt − D/c)", bold = true)
                Para("D : distance bouche → cible ; c : vitesse du son = 331,3 × √(1 + T/273,15) m/s (343 m/s à 20 °C). Énergie : E = ½ × m × v², masse convertie en kg.")
                Para("Le temps est mesuré en échantillons audio (48 000 par seconde en général), pas avec l'horloge du téléphone : la latence du micro s'annule car les deux bruits passent par le même flux.")
            }

            Section("Hypothèses et limites de précision") {
                Para("• Le téléphone doit être près de la bouche du canon. S'il est derrière le tireur, dans l'axe du tir, le recul s'annule exactement ; s'il est sur le côté, chaque mètre d'écart latéral fausse le délai d'environ 2,8 ms (≈ 3 % à 100 m/s sur 10 m).")
                Para("• Le son de l'impact est supposé parcourir la distance D. Vent, obstacles et température inexacte faussent légèrement c.")
                Para("• Erreur relative sur v ≈ erreur relative sur D : 10 cm d'erreur sur 10 m donnent 1 % d'erreur.")
                Para("• Une erreur de 0,1 ms sur Δt donne environ v²/D × 0,0001 m/s d'erreur (1 m/s à 100 m/s sur 10 m). Plus la distance est grande, plus la mesure est précise.")
                Para("• Au-delà de la vitesse du son, l'onde de choc du projectile peut perturber la détection : résultats à vérifier.")
                Para("• Échos (murs, sol) et bruits parasites peuvent être pris pour l'impact : contrôlez le graphique quand la qualité n'est pas « bonne ».")
                Para("• Résultat expérimental, non certifié. À comparer avec un chronographe de référence.")
            }

            Section("À propos") {
                Para("Chroni ${BuildConfig.VERSION_NAME} (version ${BuildConfig.VERSION_CODE})")
                Para("Fonctionne entièrement hors ligne. Aucune publicité, aucun compte, aucune télémétrie. Seule autorisation : le microphone.")
            }
        }
    }

    if (confirmAudio) ConfirmDialog("Supprimer tous les enregistrements audio ?", "Les mesures restent dans l'historique mais leurs graphiques ne pourront plus être rouverts ni corrigés.",
        onConfirm = { confirmAudio = false; vm.deleteAllAudio() }, onDismiss = { confirmAudio = false })
    if (confirmAll) ConfirmDialog("Effacer tout l'historique ?", "Toutes les mesures et tous les enregistrements seront définitivement supprimés.",
        onConfirm = { confirmAll = false; vm.deleteAllHistory() }, onDismiss = { confirmAll = false })
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun Para(text: String, bold: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
