package com.tenan.equalizer.data

import android.content.Context
import com.tenan.equalizer.device.CorrectionSpec
import com.tenan.equalizer.device.OutputDevice
import com.tenan.equalizer.dsp.EqSettings
import com.tenan.equalizer.dsp.Preset
import com.tenan.equalizer.dsp.PresetCategory
import com.tenan.equalizer.session.AudioSessionInfo
import com.tenan.equalizer.session.SessionSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Single source of truth for the user's EQ configuration, persisted on every change. */
class EqRepository(context: Context) {
    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(ConfigCodec.decode(prefs.getString(KEY_CONFIG, null)))
    val config: StateFlow<EqConfig> = _config

    fun update(transform: (EqConfig) -> EqConfig) {
        val next = _config.updateAndGet(transform)
        prefs.edit().putString(KEY_CONFIG, ConfigCodec.encode(next)).apply()
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun updateProfile(device: OutputDevice, transform: (DeviceProfile) -> DeviceProfile) = update { c ->
        val profile = transform(c.profileFor(device))
        c.copy(profiles = c.profiles + (profile.key to profile))
    }

    fun updateSettings(device: OutputDevice, transform: (EqSettings) -> EqSettings) =
        updateProfile(device) { it.copy(settings = transform(it.settings)) }

    fun setCorrection(device: OutputDevice, correction: CorrectionSpec) =
        updateProfile(device) { it.copy(correction = correction) }

    fun setAppPreset(packageName: String, presetId: String?) = update { c ->
        c.copy(
            appPresets = if (presetId == null) c.appPresets - packageName
            else c.appPresets + (packageName to presetId)
        )
    }

    fun saveCustomPreset(name: String, bands: List<Float>): Preset {
        val preset = Preset("custom_${System.currentTimeMillis()}", name.trim(), bands, PresetCategory.CUSTOM)
        update { it.copy(customPresets = it.customPresets + preset) }
        return preset
    }

    fun deleteCustomPreset(id: String) = update { c ->
        c.copy(
            customPresets = c.customPresets.filterNot { it.id == id },
            appPresets = c.appPresets.filterValues { it != id },
        )
    }

    fun deleteProfile(key: String) = update { it.copy(profiles = it.profiles - key) }

    // Announced sessions are remembered across process restarts: a player
    // only announces once, when playback starts, so losing the list would
    // leave a song that is already playing un-equalised until the next track.

    fun saveAnnouncedSessions(sessions: List<AudioSessionInfo>) {
        val json = JSONArray()
        sessions.forEach { s ->
            json.put(JSONObject().apply {
                put("id", s.sessionId)
                s.packageName?.let { put("pkg", it) }
                put("at", s.openedAtMs)
            })
        }
        prefs.edit().putString(KEY_SESSIONS, json.toString()).apply()
    }

    fun loadAnnouncedSessions(nowMs: Long = System.currentTimeMillis()): List<AudioSessionInfo> = try {
        val json = JSONArray(prefs.getString(KEY_SESSIONS, "[]"))
        (0 until json.length()).mapNotNull { i ->
            val o = json.optJSONObject(i) ?: return@mapNotNull null
            val at = o.optLong("at")
            if (nowMs - at > SESSION_MEMORY_MS) return@mapNotNull null
            AudioSessionInfo(
                o.optInt("id"),
                if (o.has("pkg")) o.optString("pkg") else null,
                SessionSource.ANNOUNCED,
                at,
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        const val KEY_CONFIG = "config"
        const val KEY_SESSIONS = "announced_sessions"
        val SESSION_MEMORY_MS = TimeUnit.HOURS.toMillis(12)
    }
}
