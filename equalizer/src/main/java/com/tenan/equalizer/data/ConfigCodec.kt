package com.tenan.equalizer.data

import com.tenan.equalizer.device.CorrectionSpec
import com.tenan.equalizer.dsp.Bands
import com.tenan.equalizer.dsp.EqSettings
import com.tenan.equalizer.dsp.Preset
import com.tenan.equalizer.dsp.PresetCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON persistence for [EqConfig]. Decoding is lenient: a missing or malformed
 * field falls back to its default rather than losing the whole configuration.
 */
object ConfigCodec {
    private const val VERSION = 1

    fun encode(config: EqConfig): String = JSONObject().apply {
        put("version", VERSION)
        put("enabled", config.enabled)
        put("mode", config.mode.name)
        put("autoSwitch", config.autoSwitch)
        put("profiles", JSONArray().apply { config.profiles.values.forEach { put(encodeProfile(it)) } })
        put("appPresets", JSONObject().apply { config.appPresets.forEach { (k, v) -> put(k, v) } })
        put("customPresets", JSONArray().apply { config.customPresets.forEach { put(encodePreset(it)) } })
    }.toString()

    fun decode(json: String?): EqConfig {
        if (json.isNullOrBlank()) return EqConfig()
        return try {
            val o = JSONObject(json)
            val profiles = o.optJSONArray("profiles").objects().mapNotNull(::decodeProfile)
            val appPresets = o.optJSONObject("appPresets")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } ?: emptyMap()
            EqConfig(
                enabled = o.optBoolean("enabled", false),
                mode = enumOr(o.optString("mode"), AttachMode.PER_APP),
                autoSwitch = o.optBoolean("autoSwitch", true),
                profiles = profiles.associateBy { it.key },
                appPresets = appPresets,
                customPresets = o.optJSONArray("customPresets").objects().mapNotNull(::decodePreset),
            )
        } catch (e: Exception) {
            EqConfig()
        }
    }

    private fun encodeProfile(p: DeviceProfile) = JSONObject().apply {
        put("key", p.key)
        put("name", p.name)
        put("settings", encodeSettings(p.settings))
        put("correction", encodeCorrection(p.correction))
    }

    private fun decodeProfile(o: JSONObject): DeviceProfile? {
        val key = o.optString("key").ifBlank { return null }
        return DeviceProfile(
            key = key,
            name = o.optString("name", key),
            settings = o.optJSONObject("settings")?.let(::decodeSettings) ?: EqSettings(),
            correction = o.optJSONObject("correction")?.let(::decodeCorrection) ?: CorrectionSpec.None,
        )
    }

    private fun encodeSettings(s: EqSettings) = JSONObject().apply {
        put("bands", JSONArray().apply { s.bandsDb.forEach { put(it.toDouble()) } })
        s.presetId?.let { put("presetId", it) }
        put("preamp", s.preampDb.toDouble())
        put("bass", s.bassDb.toDouble())
        put("treble", s.trebleDb.toDouble())
        put("virtualizer", s.virtualizer.toDouble())
        put("loudness", s.loudnessDb.toDouble())
        put("balance", s.balance.toDouble())
        put("limiter", s.limiter)
        put("autoHeadroom", s.autoHeadroom)
    }

    private fun decodeSettings(o: JSONObject): EqSettings {
        val d = EqSettings()
        return EqSettings(
            bandsDb = o.optJSONArray("bands").floats() ?: d.bandsDb,
            presetId = if (o.has("presetId")) o.optString("presetId") else null,
            preampDb = o.optDouble("preamp", 0.0).toFloat(),
            bassDb = o.optDouble("bass", 0.0).toFloat(),
            trebleDb = o.optDouble("treble", 0.0).toFloat(),
            virtualizer = o.optDouble("virtualizer", 0.0).toFloat(),
            loudnessDb = o.optDouble("loudness", 0.0).toFloat(),
            balance = o.optDouble("balance", 0.0).toFloat(),
            limiter = o.optBoolean("limiter", d.limiter),
            autoHeadroom = o.optBoolean("autoHeadroom", d.autoHeadroom),
        )
    }

    private fun encodeCorrection(c: CorrectionSpec) = JSONObject().apply {
        when (c) {
            CorrectionSpec.None -> put("type", "none")
            is CorrectionSpec.BuiltIn -> {
                put("type", "builtin")
                put("id", c.id)
            }
            is CorrectionSpec.Imported -> {
                put("type", "imported")
                put("name", c.name)
                put("text", c.text)
            }
        }
    }

    private fun decodeCorrection(o: JSONObject): CorrectionSpec = when (o.optString("type")) {
        "builtin" -> CorrectionSpec.BuiltIn(o.optString("id"))
        "imported" -> CorrectionSpec.Imported(o.optString("name", "Imported"), o.optString("text"))
        else -> CorrectionSpec.None
    }

    private fun encodePreset(p: Preset) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("bands", JSONArray().apply { p.bandsDb.forEach { put(it.toDouble()) } })
    }

    private fun decodePreset(o: JSONObject): Preset? {
        val bands = o.optJSONArray("bands").floats() ?: return null
        val id = o.optString("id").ifBlank { return null }
        return Preset(id, o.optString("name", "Custom"), bands, PresetCategory.CUSTOM)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.floats(): List<Float>? {
        if (this == null || length() != Bands.GRAPHIC_HZ.size) return null
        return (0 until length()).map { optDouble(it, 0.0).toFloat() }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
