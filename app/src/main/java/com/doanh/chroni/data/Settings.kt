package com.doanh.chroni.data

import android.content.Context

/** Préférences locales (SharedPreferences). */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("chroni_settings", Context.MODE_PRIVATE)

    var keepAudio: Boolean
        get() = p.getBoolean("keep_audio", true)
        set(v) = p.edit().putBoolean("keep_audio", v).apply()

    var maxDurationS: Int
        get() = p.getInt("max_duration_s", 10)
        set(v) = p.edit().putInt("max_duration_s", v.coerceIn(MIN_DURATION, MAX_DURATION)).apply()

    var autoStop: Boolean
        get() = p.getBoolean("auto_stop", true)
        set(v) = p.edit().putBoolean("auto_stop", v).apply()

    var vMin: Double
        get() = p.getFloat("v_min", 20f).toDouble()
        set(v) = p.edit().putFloat("v_min", v.toFloat()).apply()

    var vMax: Double
        get() = p.getFloat("v_max", 1500f).toDouble()
        set(v) = p.edit().putFloat("v_max", v.toFloat()).apply()

    var lastMass: String
        get() = p.getString("last_mass", "") ?: ""
        set(v) = p.edit().putString("last_mass", v).apply()

    var lastDistance: String
        get() = p.getString("last_distance", "") ?: ""
        set(v) = p.edit().putString("last_distance", v).apply()

    var lastTemperature: String
        get() = p.getString("last_temperature", "") ?: ""
        set(v) = p.edit().putString("last_temperature", v).apply()

    var audioNoticeSeen: Boolean
        get() = p.getBoolean("audio_notice_seen", false)
        set(v) = p.edit().putBoolean("audio_notice_seen", v).apply()

    companion object {
        const val MIN_DURATION = 3
        const val MAX_DURATION = 30
    }
}
