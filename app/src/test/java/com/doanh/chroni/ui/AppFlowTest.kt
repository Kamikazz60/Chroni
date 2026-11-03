package com.doanh.chroni.ui

import android.Manifest
import android.graphics.Bitmap
import android.media.AudioRecord
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.doanh.chroni.MainActivity
import com.doanh.chroni.core.dsp.SyntheticSignal
import com.doanh.chroni.core.physics.Physics
import com.doanh.chroni.data.MeasurementRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioRecord
import java.io.File
import java.io.FileOutputStream

/**
 * Parcours complet de l'application dans Robolectric (Android simulé sur la JVM) :
 * saisie → enregistrement (micro simulé avec un tir synthétique) → résultat →
 * graphique et correction → historique. Capture aussi des images des écrans.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class AppFlowTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val distance = 10.0
    private val velocity = 120.0
    private val c = Physics.speedOfSound(20.0)

    companion object {
        /** Tir synthétique : départ à 0,8 s, impact à 0,8 s + Δt (v = 120 m/s, D = 10 m, 20 °C). */
        val shot: ShortArray by lazy {
            val c = Physics.speedOfSound(20.0)
            val dt = Physics.expectedDelay(10.0, 120.0, c)
            SyntheticSignal(48_000, 10.0).noise(0.0005).blast(0.8, 0.8).ring(0.8 + dt, 0.15).toShorts()
        }

        /** Position de lecture du micro simulé (Robolectric redemande la source à chaque lecture). */
        @Volatile var pos = 0

        private val source = object : ShadowAudioRecord.AudioRecordSource {
                    override fun readInShortArray(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int, isBlocking: Boolean): Int {
                        if (pos >= shot.size) { java.util.Arrays.fill(audioData, offsetInShorts, offsetInShorts + sizeInShorts, 0); return sizeInShorts }
                        val n = minOf(sizeInShorts, shot.size - pos)
                        System.arraycopy(shot, pos, audioData, offsetInShorts, n)
                        pos += n
                        Thread.sleep(1) // laisse respirer le thread d'analyse
                        return n
                    }
                }
    }

    object Reset {
        /**
         * AndroidViewModelFactory garde en statique la première Application créée :
         * entre deux tests Robolectric, il faut l'oublier (sans effet sur un vrai téléphone).
         */
        fun resetViewModelFactory() {
            val cls = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory::class.java
            cls.declaredFields.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == cls }.forEach {
                it.isAccessible = true; it.set(null, null)
            }
        }
    }

    @Before fun setUp() {
        Reset.resetViewModelFactory()
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        pos = 0
        ShadowAudioRecord.setSourceProvider { _: AudioRecord -> source }
    }

    private fun shot(name: String) {
        val dir = System.getProperty("chroni.screenshots") ?: return
        File(dir).mkdirs()
        rule.waitForIdle()
        // Capture logicielle de la vue racine (captureToImage n'est pas supporté par Robolectric).
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun fillInputs(mass: String, dist: String, temp: String) {
        rule.onNodeWithText("Masse du projectile").performTextReplacement(mass)
        rule.onNodeWithText("Distance bouche → cible").performTextReplacement(dist)
        rule.onNodeWithText("Température de l'air (facultatif)").performTextReplacement(temp)
    }

    @Test fun saisieInvalideAfficheLesErreurs() {
        rule.onNodeWithText("Chronographe acoustique").assertExists()
        fillInputs("", "0", "")
        rule.onNodeWithText("Lancer une mesure").performClick()
        rule.onNodeWithText("Masse invalide (0,01 à 1000 g)").assertExists()
        rule.onNodeWithText("Distance invalide (0,5 à 500 m)").assertExists()
        shot("01b_accueil_erreurs")
    }

    @Test fun parcoursCompletMesureCorrectionHistorique() {
        shot("01_accueil")
        fillInputs("0,55", "10", "20")
        rule.onNodeWithText("Lancer une mesure").performClick()
        // Première utilisation : information sur la conservation de l'audio.
        rule.onNodeWithText("Compris").performClick()

        // Écran d'enregistrement puis résultat (arrêt automatique après la fenêtre de recherche).
        try {
            rule.waitUntil(30_000) { rule.onAllNodesWithText("Vitesse estimée").fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            shot("zz_debug_blocage")
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            val r = MeasurementRepository(app.filesDir)
            r.list().forEach { mm ->
                val a = r.loadAudio(mm.id)!!
                var pk = 0; var at = 0
                a.forEachIndexed { i, v -> if (kotlin.math.abs(v.toInt()) > pk) { pk = kotlin.math.abs(v.toInt()); at = i } }
                val firstLoud = a.indexOfFirst { kotlin.math.abs(it.toInt()) > 5000 }
                println("CHRONI_DEBUG n=${a.size} peak=$pk at=$at firstLoud=$firstLoud srcPeak=${shot.maxOf { kotlin.math.abs(it.toInt()) }} srcLoud=${shot.indexOfFirst { kotlin.math.abs(it.toInt()) > 5000 }}")
            }
            throw e
        }
        shot("03_resultat")

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = MeasurementRepository(rule.activity.application.filesDir)
        repeat(50) { if (repo.list().isEmpty()) Thread.sleep(100) }
        val m = repo.list().single()
        val expectedDt = Physics.expectedDelay(distance, velocity, c)
        assertEquals(expectedDt * 1000, m.deltaTMs!!, 0.3)
        assertEquals(velocity, m.velocity!!, 1.5)
        assertTrue("audio conservé", repo.hasAudio(m.id))

        // Graphique interactif.
        rule.onNodeWithText("Graphique acoustique / corriger").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Analyse acoustique").fetchSemanticsNodes().isNotEmpty() }
        shot("04_analyse")

        // Glisser la poignée « I » au doigt vers la gauche : le délai diminue et le résultat est recalculé.
        // Cadrage initial : départ à 18,75 % et impact à 81,25 % de la largeur (marges de 30 %).
        rule.onNodeWithTag("waveform").performTouchInput {
            val x = width * 0.8125f
            down(androidx.compose.ui.geometry.Offset(x, 30f))
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(-8f, 0f)) }
            up()
        }
        rule.waitForIdle()
        val afterDrag = MeasurementRepository(rule.activity.application.filesDir).list().single()
        assertEquals(m.deltaTMs, afterDrag.deltaTMs) // rien n'est enregistré avant confirmation
        rule.onNode(hasText(Texts.num(m.deltaTMs!!, 2) + " ms")).assertDoesNotExist()
        shot("04b_analyse_glisser")
        rule.onNodeWithContentDescription("Revenir aux positions automatiques").performClick()
        rule.onNode(hasText(Texts.num(m.deltaTMs!!, 2) + " ms")).assertExists()

        // Défilement / zoom sans plantage.
        rule.onNodeWithContentDescription("Zoomer").performClick()
        rule.onNodeWithContentDescription("Signal entier").performClick()
        shot("05_analyse_signal_entier")
        rule.onNodeWithContentDescription("Cadrer départ et impact").performClick()

        // Correction : impact +1 ms → Δt +1 ms, vitesse recalculée immédiatement.
        val before = m.deltaTMs!!
        rule.onNodeWithText("+1 ms").performClick()
        rule.onNode(hasText(Texts.num(before + 1.0, 2) + " ms")).assertExists()
        shot("06_analyse_corrigee")
        rule.onNodeWithText("Confirmer et enregistrer").performClick()

        rule.waitUntil(10_000) { rule.onAllNodesWithText("Corrigée manuellement — confirmée", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val saved = repo.load(m.id)!!
        assertEquals(m.autoImpactSample, saved.autoImpactSample)
        assertEquals(m.autoImpactSample!! + 48, saved.impactSample)
        assertEquals(before + 1.0, saved.deltaTMs!!, 1e-6)
        assertTrue(saved.velocity!! < m.velocity!!)

        // Retour aux positions automatiques dans le graphique.
        rule.onNodeWithText("Graphique acoustique / corriger").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Analyse acoustique").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Revenir aux positions automatiques").performClick()
        rule.onNode(hasText(Texts.num(before, 2) + " ms")).assertExists()
        rule.onNodeWithContentDescription("Retour").performClick()
        rule.onNodeWithText("Quitter").performClick() // abandon des modifications non enregistrées

        // Historique : la mesure y figure et se rouvre.
        rule.onNodeWithContentDescription("Retour").performClick()
        rule.onNodeWithContentDescription("Historique").performClick()
        rule.onNodeWithText("Historique (1)").assertExists()
        rule.onNodeWithText("Corrigée").assertExists()
        shot("07_historique")
        rule.onNodeWithText("Corrigée").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Vitesse estimée").fetchSemanticsNodes().isNotEmpty() }

        // Suppression.
        rule.onNodeWithContentDescription("Supprimer la mesure").performClick()
        rule.onNodeWithText("Supprimer").performClick()
        rule.waitUntil(5_000) { repo.list().isEmpty() }
        assertTrue(!repo.hasAudio(m.id))
    }

    @Test @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    fun modeSombre() {
        fillInputs("0,55", "10", "")
        shot("02_accueil_sombre")
    }

    @Test fun ecranParametres() {
        rule.onNodeWithContentDescription("Paramètres et informations").performClick()
        rule.onNodeWithText("Conserver l'audio des mesures").assertExists()
        shot("08_parametres")
        rule.onRoot().performTouchInput { swipeLeft() }
    }
}
