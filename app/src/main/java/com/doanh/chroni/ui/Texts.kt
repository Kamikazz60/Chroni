package com.doanh.chroni.ui

import com.doanh.chroni.core.dsp.DetectionWarning
import com.doanh.chroni.core.dsp.EventRole
import com.doanh.chroni.core.dsp.Quality
import com.doanh.chroni.core.model.DetectionMode
import com.doanh.chroni.core.model.RecordingWarning
import com.doanh.chroni.core.model.TemperatureSource
import com.doanh.chroni.core.physics.InputError
import com.doanh.chroni.core.physics.InvalidReason
import com.doanh.chroni.core.physics.ResultWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Textes affichés (français). */
object Texts {
    private val fr = Locale.FRANCE

    fun num(v: Double, decimals: Int) = String.format(fr, "%.${decimals}f", v)

    fun date(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", fr).format(Date(ms))

    fun bytes(b: Long): String = when {
        b >= 1_000_000 -> num(b / 1_000_000.0, 1) + " Mo"
        b >= 1_000 -> num(b / 1_000.0, 0) + " ko"
        else -> "$b o"
    }

    fun inputError(e: InputError) = when (e) {
        InputError.MASS -> "Masse invalide (0,01 à 1000 g)"
        InputError.DISTANCE -> "Distance invalide (0,5 à 500 m)"
        InputError.TEMPERATURE -> "Température invalide (−40 à 60 °C) — laisser vide si inconnue"
    }

    fun quality(q: Quality) = when (q) {
        Quality.GOOD -> "Bonne"
        Quality.FAIR -> "Moyenne"
        Quality.POOR -> "Faible"
        Quality.FAILED -> "Échec de détection"
    }

    fun mode(m: DetectionMode) = when (m) {
        DetectionMode.AUTO -> "Automatique"
        DetectionMode.CORRECTED -> "Corrigée manuellement"
        DetectionMode.MANUAL -> "Placée manuellement"
        DetectionMode.NONE -> "Aucun repère"
    }

    fun temperatureSource(s: TemperatureSource) = when (s) {
        TemperatureSource.NOMINAL -> "valeur nominale"
        TemperatureSource.MANUAL -> "saisie manuelle"
        TemperatureSource.SENSOR -> "capteur du téléphone"
    }

    fun detectionWarning(w: DetectionWarning) = when (w) {
        DetectionWarning.NO_EVENT -> "Aucun bruit impulsionnel net n'a été détecté."
        DetectionWarning.NO_IMPACT_IN_WINDOW -> "Aucun impact plausible détecté après le départ. Placez le repère d'impact dans le graphique si vous le voyez."
        DetectionWarning.AMBIGUOUS_IMPACT -> "Plusieurs impacts possibles de force comparable : vérifiez le repère d'impact dans le graphique."
        DetectionWarning.LOW_SNR_IMPACT -> "Impact faible par rapport au bruit ambiant : position moins sûre."
        DetectionWarning.MULTIPLE_LOUD_EVENTS -> "Plusieurs bruits forts enregistrés (autre tir, choc ?) : vérifiez le repère de départ."
        DetectionWarning.CLIPPING -> "Son saturé (normal près d'un tir) : l'attaque reste exploitable mais l'amplitude est écrêtée."
    }

    fun recordingWarning(w: RecordingWarning) = when (w) {
        RecordingWarning.SAMPLES_POSSIBLY_LOST -> "Le flux audio semble incomplet (échantillons perdus ?) : résultat à vérifier."
        RecordingWarning.MIC_SILENCED -> "Le système a coupé le micro pendant la capture (autre application ?)."
        RecordingWarning.INTERRUPTED -> "Enregistrement interrompu avant la fin."
        RecordingWarning.MAX_DURATION_REACHED -> "Durée maximale d'enregistrement atteinte."
    }

    fun resultWarning(w: ResultWarning) = when (w) {
        ResultWarning.NEAR_OR_ABOVE_SOUND_SPEED -> "Vitesse proche ou supérieure à celle du son : le modèle est moins fiable (onde de choc du projectile)."
        ResultWarning.LARGE_UNCERTAINTY -> "Incertitude supérieure à 10 % : augmentez la distance de tir pour gagner en précision."
        ResultWarning.NOMINAL_TEMPERATURE -> "Température inconnue : vitesse du son nominale (20 °C) utilisée."
    }

    fun invalid(r: InvalidReason) = when (r) {
        InvalidReason.INVALID_INPUT -> "Paramètres de tir invalides."
        InvalidReason.DELAY_NOT_POSITIVE -> "Délai nul ou négatif : le repère d'impact doit être après le départ."
        InvalidReason.DELAY_SHORTER_THAN_SOUND -> "Délai plus court que le simple trajet du son : repères ou distance incohérents."
        InvalidReason.VELOCITY_TOO_HIGH -> "Vitesse calculée non physique (> 2000 m/s) : repères probablement mal placés."
        InvalidReason.VELOCITY_TOO_LOW -> "Vitesse calculée trop faible pour être plausible : repère d'impact probablement erroné."
        InvalidReason.NO_EVENTS -> "Départ et impact non identifiés : aucun résultat ne peut être calculé."
    }

    fun role(r: EventRole) = when (r) {
        EventRole.DEPARTURE -> "départ"
        EventRole.IMPACT -> "impact"
        EventRole.CANDIDATE -> "candidat"
        EventRole.OUT_OF_WINDOW -> "hors fenêtre"
        EventRole.SPIKE -> "pic isolé"
        EventRole.IGNORED -> "ignoré"
    }
}
