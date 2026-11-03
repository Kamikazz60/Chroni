package com.doanh.chroni.core.dsp

/**
 * Logique de suivi pendant l'enregistrement (pure, testable) :
 * état « prêt », événements déjà repérés et décision d'arrêt automatique.
 */
object RecordingMonitor {
    /** Durée d'ambiance captée avant de dire « prêt » (sert à estimer le bruit de fond). */
    const val READY_AFTER_S = 0.6

    /** Début ignoré (bruit du doigt sur le bouton Démarrer). */
    const val IGNORE_START_S = 0.35

    /** Marge après la fin de la fenêtre de recherche avant l'arrêt automatique. */
    const val STOP_MARGIN_S = 0.4

    /** Fin ignorée lors d'un arrêt manuel (bruit du doigt sur le bouton Arrêter). */
    const val IGNORE_END_MANUAL_S = 0.15

    data class Status(
        val ready: Boolean,
        val departureS: Double?,
        val impactS: Double?,
        val shouldAutoStop: Boolean,
    )

    fun status(
        detection: DetectionResult?,
        capturedSamples: Int,
        sampleRate: Int,
        window: SearchWindow,
        autoStop: Boolean,
    ): Status {
        val ready = capturedSamples >= READY_AFTER_S * sampleRate
        val dep = detection?.departure
        val imp = detection?.impact
        val stopAt = dep?.let { it.onsetSample + ((window.maxDelayS + STOP_MARGIN_S) * sampleRate).toLong() }
        return Status(
            ready = ready,
            departureS = dep?.onsetSample?.let { it.toDouble() / sampleRate },
            impactS = imp?.onsetSample?.let { it.toDouble() / sampleRate },
            shouldAutoStop = autoStop && stopAt != null && capturedSamples >= stopAt,
        )
    }
}
