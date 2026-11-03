package com.doanh.chroni.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.doanh.chroni.core.model.RecordingWarning
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Capture continue du microphone avec AudioRecord, en PCM 16 bits mono.
 *
 * Le temps est donné par la position dans le flux d'échantillons et la fréquence
 * réelle renvoyée par AudioRecord. L'horloge système n'est utilisée que pour un
 * contrôle de cohérence (détection d'éventuelles pertes d'échantillons).
 */
class AudioCapture(private val context: Context) {

    class Session internal constructor(
        val sampleRate: Int,
        val sourceName: String,
        val buffer: ShortArray,
    ) {
        internal val count = AtomicInteger(0)
        internal val stopRequested = AtomicBoolean(false)
        internal val silenced = AtomicBoolean(false)
        @Volatile internal var error: String? = null
        @Volatile internal var finished = false
        @Volatile internal var startNanos = 0L
        @Volatile internal var endNanos = 0L

        /** Nombre d'échantillons déjà capturés (lecture sûre depuis un autre thread). */
        val samplesCaptured: Int get() = count.get()
        val isFinished: Boolean get() = finished
        val errorMessage: String? get() = error
        val isSilenced: Boolean get() = silenced.get()
        val capacity: Int get() = buffer.size

        /** Niveau RMS (0..1) des derniers [windowSamples] échantillons. */
        fun recentLevel(windowSamples: Int): Double {
            val n = count.get()
            val from = (n - windowSamples).coerceAtLeast(0)
            if (n <= from) return 0.0
            var peak = 0
            for (i in from until n) peak = maxOf(peak, abs(buffer[i].toInt()))
            return peak / 32768.0
        }

        fun warnings(stoppedByMaxDuration: Boolean, interrupted: Boolean): List<RecordingWarning> {
            val w = mutableListOf<RecordingWarning>()
            val elapsedS = (endNanos - startNanos) / 1e9
            val expected = elapsedS * sampleRate
            // Tolérance large : le démarrage d'AudioRecord prend quelques dizaines de ms.
            if (expected > sampleRate && count.get() < expected * 0.95 - sampleRate * 0.15) w += RecordingWarning.SAMPLES_POSSIBLY_LOST
            if (silenced.get()) w += RecordingWarning.MIC_SILENCED
            if (interrupted || error != null) w += RecordingWarning.INTERRUPTED
            if (stoppedByMaxDuration) w += RecordingWarning.MAX_DURATION_REACHED
            return w
        }
    }

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "chroni-audio").apply { priority = Thread.MAX_PRIORITY } }

    /**
     * Démarre une capture. Lance une exception lisible si le micro est indisponible.
     * La permission RECORD_AUDIO doit avoir été accordée avant l'appel.
     */
    @SuppressLint("MissingPermission")
    fun start(maxDurationS: Int): Session {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val sources = buildList {
            if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED")
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
            add(MediaRecorder.AudioSource.MIC to "MIC")
        }
        var lastError = "Microphone indisponible"
        for ((source, name) in sources) {
            for (rate in intArrayOf(48_000, 44_100)) {
                val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuf <= 0) continue
                val record = try {
                    AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 4, rate / 5 * 2))
                } catch (e: Exception) {
                    lastError = e.message ?: lastError; continue
                }
                if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); continue }
                val actualRate = record.sampleRate
                val session = Session(actualRate, name, ShortArray(actualRate * maxDurationS))
                try {
                    record.startRecording()
                } catch (e: Exception) {
                    record.release(); lastError = e.message ?: lastError; continue
                }
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    record.release(); lastError = "Le microphone est utilisé par une autre application"; continue
                }
                val callback = registerSilenceCallback(am, record, session)
                executor.execute { readLoop(record, session, minBuf, am, callback) }
                return session
            }
        }
        throw IllegalStateException(lastError)
    }

    fun stop(session: Session) { session.stopRequested.set(true) }

    private fun readLoop(record: AudioRecord, s: Session, minBuf: Int, am: AudioManager, cb: Any?) {
        val chunk = (minBuf / 2).coerceIn(256, 4096)
        s.startNanos = SystemClock.elapsedRealtimeNanos()
        try {
            while (!s.stopRequested.get()) {
                val offset = s.count.get()
                val room = s.buffer.size - offset
                if (room <= 0) break
                val read = record.read(s.buffer, offset, minOf(chunk, room))
                if (read < 0) {
                    s.error = when (read) {
                        AudioRecord.ERROR_DEAD_OBJECT -> "Le microphone a été interrompu par le système"
                        AudioRecord.ERROR_INVALID_OPERATION -> "Lecture audio impossible"
                        else -> "Erreur audio ($read)"
                    }
                    break
                }
                s.count.addAndGet(read)
            }
        } catch (e: Exception) {
            s.error = e.message ?: "Erreur audio"
        } finally {
            s.endNanos = SystemClock.elapsedRealtimeNanos()
            runCatching { record.stop() }
            if (Build.VERSION.SDK_INT >= 29 && cb is AudioManager.AudioRecordingCallback) runCatching { am.unregisterAudioRecordingCallback(cb) }
            record.release()
            s.finished = true
        }
    }

    private fun registerSilenceCallback(am: AudioManager, record: AudioRecord, s: Session): Any? {
        if (Build.VERSION.SDK_INT < 29) return null
        val id = record.audioSessionId
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                configs.firstOrNull { it.clientAudioSessionId == id }?.let { if (it.isClientSilenced) s.silenced.set(true) }
            }
        }
        am.registerAudioRecordingCallback(cb, null)
        return cb
    }
}
