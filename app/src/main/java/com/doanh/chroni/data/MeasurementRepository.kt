package com.doanh.chroni.data

import com.doanh.chroni.core.model.Measurement
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stockage local et privé des mesures, sans serveur :
 *  - measurements/<id>.json : paramètres, métadonnées audio, repères, résultats ;
 *  - audio/<id>.pcm : signal brut 16 bits mono little-endian (si conservé).
 * Écritures atomiques (fichier temporaire puis renommage).
 */
class MeasurementRepository(root: File) {
    private val jsonDir = File(root, "measurements").apply { mkdirs() }
    private val audioDir = File(root, "audio").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    @Synchronized
    fun save(m: Measurement) {
        atomicWrite(File(jsonDir, "${m.id}.json"), json.encodeToString(Measurement.serializer(), m).toByteArray())
    }

    @Synchronized
    fun load(id: String): Measurement? {
        val f = File(jsonDir, "$id.json")
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(Measurement.serializer(), f.readText()) }.getOrNull()
    }

    @Synchronized
    fun list(): List<Measurement> =
        (jsonDir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f -> runCatching { json.decodeFromString(Measurement.serializer(), f.readText()) }.getOrNull() }
            .sortedByDescending { it.createdAtMs }

    @Synchronized
    fun delete(id: String) {
        File(jsonDir, "$id.json").delete()
        File(audioDir, "$id.pcm").delete()
    }

    @Synchronized
    fun saveAudio(id: String, samples: ShortArray, count: Int = samples.size) {
        val buf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buf.putShort(samples[i])
        atomicWrite(File(audioDir, "$id.pcm"), buf.array())
    }

    @Synchronized
    fun loadAudio(id: String): ShortArray? {
        val f = File(audioDir, "$id.pcm")
        if (!f.exists()) return null
        val bytes = f.readBytes()
        val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(sb.remaining()).also { sb.get(it) }
    }

    fun hasAudio(id: String): Boolean = File(audioDir, "$id.pcm").exists()

    @Synchronized
    fun deleteAudio(id: String) {
        File(audioDir, "$id.pcm").delete()
        load(id)?.let { save(it.copy(audioKept = false)) }
    }

    @Synchronized
    fun deleteAllAudio(): Int {
        var n = 0
        audioDir.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension
            if (f.delete()) n++
            load(id)?.let { save(it.copy(audioKept = false)) }
        }
        return n
    }

    fun audioBytes(): Long = audioDir.listFiles()?.sumOf { it.length() } ?: 0L

    @Synchronized
    fun deleteAll() {
        jsonDir.listFiles()?.forEach { it.delete() }
        audioDir.listFiles()?.forEach { it.delete() }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("Écriture impossible : ${target.name}")
        }
    }
}
