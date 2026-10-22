package com.tenan.equalizer.data

import com.tenan.equalizer.device.CorrectionSpec
import com.tenan.equalizer.device.OutputDevice
import com.tenan.equalizer.dsp.EqSettings
import com.tenan.equalizer.dsp.Preset
import com.tenan.equalizer.dsp.Presets

enum class AttachMode {
    /** Attach to each player's own audio session; enables per-app presets. */
    PER_APP,
    /** Attach to the global output mix (session 0); reaches any app, but one curve for all. */
    GLOBAL,
}

data class DeviceProfile(
    val key: String,
    val name: String,
    val settings: EqSettings = EqSettings(),
    val correction: CorrectionSpec = CorrectionSpec.None,
)

data class EqConfig(
    val enabled: Boolean = false,
    val mode: AttachMode = AttachMode.PER_APP,
    /** Keep a separate profile per output device and switch on connect. */
    val autoSwitch: Boolean = true,
    val profiles: Map<String, DeviceProfile> = emptyMap(),
    /** Package name → preset id, for apps that get their own curve. */
    val appPresets: Map<String, String> = emptyMap(),
    val customPresets: List<Preset> = emptyList(),
) {
    val allPresets: List<Preset> get() = Presets.BUILT_IN + customPresets

    fun preset(id: String?): Preset? = id?.let { wanted -> allPresets.firstOrNull { it.id == wanted } }

    fun profileKey(device: OutputDevice): String = if (autoSwitch) device.key else DEFAULT_PROFILE_KEY

    fun profileFor(device: OutputDevice): DeviceProfile {
        val key = profileKey(device)
        return profiles[key] ?: DeviceProfile(
            key = key,
            name = if (autoSwitch) device.displayName else "All devices",
        )
    }

    /** Slider gains an app's own preset substitutes for the device's, if it has one. */
    fun bandsForApp(packageName: String?): List<Float>? =
        packageName?.let { appPresets[it] }?.let { preset(it)?.bandsDb }

    companion object {
        const val DEFAULT_PROFILE_KEY = "default"
    }
}
