package com.doanh.chroni.ui

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doanh.chroni.BuildConfig
import com.doanh.chroni.audio.AudioCapture
import com.doanh.chroni.core.dsp.EventDetector
import com.doanh.chroni.core.dsp.RecordingMonitor
import com.doanh.chroni.core.dsp.SearchWindow
import com.doanh.chroni.core.model.Measurement
import com.doanh.chroni.core.model.MeasurementFactory
import com.doanh.chroni.core.model.TemperatureSource
import com.doanh.chroni.core.physics.InputError
import com.doanh.chroni.core.physics.Inputs
import com.doanh.chroni.core.physics.Physics
import com.doanh.chroni.core.physics.ShotParams
import com.doanh.chroni.data.MeasurementRepository
import com.doanh.chroni.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class InputsState(
    val mass: String = "",
    val distance: String = "",
    val temperature: String = "",
    val temperatureFromSensor: Boolean = false,
    val errors: List<InputError> = emptyList(),
)

sealed class RecordingState {
    data object Idle : RecordingState()
    data class Recording(
        val elapsedS: Double,
        val level: Double,
        val ready: Boolean,
        val departureS: Double?,
        val impactS: Double?,
        val maxDurationS: Int,
        val silenced: Boolean,
    ) : RecordingState()
    data object Analyzing : RecordingState()
    data class Error(val message: String) : RecordingState()
}

data class SettingsState(
    val keepAudio: Boolean,
    val maxDurationS: Int,
    val autoStop: Boolean,
    val vMin: Double,
    val vMax: Double,
    val audioBytes: Long,
    val hasTemperatureSensor: Boolean,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val repo = MeasurementRepository(app.filesDir)
    private val capture = AudioCapture(app)
    private val detector = EventDetector()
    private val sensorManager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val temperatureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

    /** Audio des mesures non conservées sur disque : gardé en mémoire pour la session en cours. */
    private val memoryAudio: MutableMap<String, ShortArray> = java.util.Collections.synchronizedMap(mutableMapOf())

    private val _inputs = MutableStateFlow(
        InputsState(settings.lastMass, settings.lastDistance, settings.lastTemperature)
    )
    val inputs: StateFlow<InputsState> = _inputs.asStateFlow()

    private val _recording = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recording: StateFlow<RecordingState> = _recording.asStateFlow()

    private val _history = MutableStateFlow<List<Measurement>>(emptyList())
    val history: StateFlow<List<Measurement>> = _history.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settingsState: StateFlow<SettingsState> = _settings.asStateFlow()

    /** Mesure terminée en attente d'affichage (survit aux rotations d'écran). */
    private val _pendingResult = MutableStateFlow<String?>(null)
    val pendingResult: StateFlow<String?> = _pendingResult.asStateFlow()
    fun consumePendingResult() { _pendingResult.value = null }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val showAudioNotice: Boolean get() = !settings.audioNoticeSeen
    fun dismissAudioNotice() { settings.audioNoticeSeen = true }

    private var session: AudioCapture.Session? = null
    private var monitorJob: Job? = null
    private var sessionParams: ShotParams? = null
    private var sessionTempSource = TemperatureSource.NOMINAL
    private var manualStop = false
    private var interrupted = false

    init { refreshHistory() }

    // ---------- Saisie ----------

    fun onMassChange(v: String) = _inputs.update { it.copy(mass = v, errors = it.errors - InputError.MASS) }
    fun onDistanceChange(v: String) = _inputs.update { it.copy(distance = v, errors = it.errors - InputError.DISTANCE) }
    fun onTemperatureChange(v: String) =
        _inputs.update { it.copy(temperature = v, temperatureFromSensor = false, errors = it.errors - InputError.TEMPERATURE) }

    /** Lecture ponctuelle du capteur de température ambiante, s'il existe. */
    fun readTemperatureSensor() {
        val sensor = temperatureSensor ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sensorManager.unregisterListener(this)
                val t = event.values.firstOrNull() ?: return
                _inputs.update {
                    it.copy(temperature = String.format(java.util.Locale.FRANCE, "%.1f", t), temperatureFromSensor = true,
                        errors = it.errors - InputError.TEMPERATURE)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /** Valide la saisie. Renvoie true si l'enregistrement peut commencer. */
    fun validateInputs(): Boolean {
        val s = _inputs.value
        val (params, errors) = Inputs.toParams(s.mass, s.distance, s.temperature)
        _inputs.update { it.copy(errors = errors) }
        if (params == null) return false
        settings.lastMass = s.mass; settings.lastDistance = s.distance; settings.lastTemperature = s.temperature
        sessionParams = params
        sessionTempSource = when {
            params.temperatureC == null -> TemperatureSource.NOMINAL
            s.temperatureFromSensor -> TemperatureSource.SENSOR
            else -> TemperatureSource.MANUAL
        }
        return true
    }

    // ---------- Enregistrement ----------

    fun startRecording() {
        if (_recording.value is RecordingState.Recording || _recording.value is RecordingState.Analyzing) return
        val params = sessionParams ?: run { _recording.value = RecordingState.Error("Paramètres manquants"); return }
        val maxS = settings.maxDurationS
        val s = try {
            capture.start(maxS)
        } catch (e: Exception) {
            _recording.value = RecordingState.Error("Impossible d'accéder au microphone : ${e.message}")
            return
        }
        session = s
        manualStop = false
        interrupted = false
        val c = Physics.speedOfSound(params.temperatureC ?: Physics.NOMINAL_TEMPERATURE_C)
        val window = SearchWindow.forShot(params.distanceM, c, settings.vMin, settings.vMax)
        val autoStop = settings.autoStop
        _recording.value = RecordingState.Recording(0.0, 0.0, false, null, null, maxS, false)
        monitorJob = viewModelScope.launch(Dispatchers.Default) {
            var lastAnalysis = 0
            var det: com.doanh.chroni.core.dsp.DetectionResult? = null
            while (isActive) {
                val n = s.samplesCaptured
                if (n - lastAnalysis >= s.sampleRate / 5) {
                    det = detector.detect(s.buffer, n, s.sampleRate, window, (RecordingMonitor.IGNORE_START_S * s.sampleRate).toInt())
                    lastAnalysis = n
                }
                val st = RecordingMonitor.status(det, n, s.sampleRate, window, autoStop)
                _recording.value = RecordingState.Recording(
                    elapsedS = n.toDouble() / s.sampleRate,
                    level = s.recentLevel(s.sampleRate / 20),
                    ready = st.ready,
                    departureS = st.departureS,
                    impactS = st.impactS,
                    maxDurationS = maxS,
                    silenced = s.isSilenced,
                )
                if (st.shouldAutoStop) capture.stop(s)
                if (s.isFinished) break
                delay(50)
            }
            finishSession(s, params, window)
        }
    }

    fun stopRecording() {
        val s = session ?: return
        manualStop = true
        capture.stop(s)
    }

    /** Appelé quand l'application passe en arrière-plan pendant une capture. */
    fun onInterrupted() {
        val s = session ?: return
        interrupted = true
        capture.stop(s)
    }

    fun cancelRecordingError() { _recording.value = RecordingState.Idle }

    private suspend fun finishSession(s: AudioCapture.Session, params: ShotParams, window: SearchWindow) {
        _recording.value = RecordingState.Analyzing
        val n = s.samplesCaptured
        val err = s.errorMessage
        if (n < s.sampleRate / 2) {
            session = null
            _recording.value = RecordingState.Error(err ?: "Enregistrement trop court pour être analysé.")
            return
        }
        val ignoreAfter = if (manualStop || interrupted) n - (RecordingMonitor.IGNORE_END_MANUAL_S * s.sampleRate).toInt() else n
        val detection = detector.detect(s.buffer, n, s.sampleRate, window, (RecordingMonitor.IGNORE_START_S * s.sampleRate).toInt(), ignoreAfter)
        val keep = settings.keepAudio
        val id = UUID.randomUUID().toString()
        val m = MeasurementFactory.create(
            id = id,
            nowMs = System.currentTimeMillis(),
            params = params,
            temperatureSource = sessionTempSource,
            sampleRate = s.sampleRate,
            sampleCount = n,
            audioSourceName = s.sourceName,
            detection = detection,
            recordingWarnings = s.warnings(stoppedByMaxDuration = n >= s.capacity, interrupted = interrupted),
            audioKept = keep,
            vMin = settings.vMin,
            vMax = settings.vMax,
            appVersion = BuildConfig.VERSION_NAME,
        )
        val audio = s.buffer.copyOf(n)
        try {
            withContext(Dispatchers.IO) {
                repo.save(m)
                if (keep) repo.saveAudio(id, audio)
            }
            if (!keep) memoryAudio[id] = audio
        } catch (e: Exception) {
            memoryAudio[id] = audio
            _messages.tryEmit("Sauvegarde impossible : ${e.message}")
        }
        session = null
        refreshHistory()
        _recording.value = RecordingState.Idle
        _pendingResult.value = id
    }

    // ---------- Historique et mesures ----------

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _history.value = repo.list()
            _settings.value = readSettings()
        }
    }

    suspend fun loadMeasurement(id: String): Measurement? = withContext(Dispatchers.IO) { repo.load(id) }

    suspend fun loadAudio(id: String): ShortArray? = memoryAudio[id] ?: withContext(Dispatchers.IO) { repo.loadAudio(id) }

    /** Enregistre les repères corrigés (positions automatiques conservées à part). */
    suspend fun saveMarkers(id: String, departure: Long?, impact: Long?): Measurement? = withContext(Dispatchers.IO) {
        val m = repo.load(id) ?: return@withContext null
        val updated = m.withMarkers(departure, impact, confirmed = true, nowMs = System.currentTimeMillis())
        repo.save(updated)
        _history.value = repo.list()
        updated
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
            memoryAudio.remove(id)
            _history.value = repo.list()
            _settings.value = readSettings()
        }
    }

    // ---------- Paramètres ----------

    private fun readSettings() = SettingsState(
        keepAudio = settings.keepAudio,
        maxDurationS = settings.maxDurationS,
        autoStop = settings.autoStop,
        vMin = settings.vMin,
        vMax = settings.vMax,
        audioBytes = repo.audioBytes(),
        hasTemperatureSensor = temperatureSensor != null,
    )

    val hasTemperatureSensor: Boolean get() = temperatureSensor != null

    fun setKeepAudio(v: Boolean) { settings.keepAudio = v; _settings.value = readSettings() }
    fun setMaxDuration(v: Int) { settings.maxDurationS = v; _settings.value = readSettings() }
    fun setAutoStop(v: Boolean) { settings.autoStop = v; _settings.value = readSettings() }

    /** Fixe la plage de vitesses plausibles ; renvoie false si elle est incohérente. */
    fun setVelocityRange(vMinText: String, vMaxText: String): Boolean {
        val lo = Inputs.parseDecimal(vMinText)
        val hi = Inputs.parseDecimal(vMaxText)
        if (lo == null || hi == null || lo < Physics.MIN_PLAUSIBLE_VELOCITY || hi > Physics.MAX_PLAUSIBLE_VELOCITY || lo >= hi) return false
        settings.vMin = lo; settings.vMax = hi
        _settings.value = readSettings()
        return true
    }

    fun deleteAllAudio() {
        viewModelScope.launch(Dispatchers.IO) {
            val n = repo.deleteAllAudio()
            memoryAudio.clear()
            _history.value = repo.list()
            _settings.value = readSettings()
            _messages.tryEmit("$n enregistrement(s) audio supprimé(s)")
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteAll()
            memoryAudio.clear()
            _history.value = emptyList()
            _settings.value = readSettings()
            _messages.tryEmit("Historique effacé")
        }
    }

    override fun onCleared() {
        session?.let { capture.stop(it) }
        super.onCleared()
    }
}
