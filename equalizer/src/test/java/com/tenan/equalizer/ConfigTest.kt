package com.tenan.equalizer

import com.tenan.equalizer.data.AttachMode
import com.tenan.equalizer.data.ConfigCodec
import com.tenan.equalizer.data.DeviceProfile
import com.tenan.equalizer.data.EqConfig
import com.tenan.equalizer.device.CorrectionSpec
import com.tenan.equalizer.device.Corrections
import com.tenan.equalizer.device.OutputDevice
import com.tenan.equalizer.device.OutputKind
import com.tenan.equalizer.dsp.EqSettings
import com.tenan.equalizer.dsp.Preset
import com.tenan.equalizer.dsp.PresetCategory
import com.tenan.equalizer.dsp.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigTest {
    private val beats = OutputDevice(OutputKind.BLUETOOTH, "Beats Fit Pro")

    @Test
    fun configRoundTripsThroughJson() {
        val custom = Preset("custom_1", "Commute", List(10) { it.toFloat() - 4f }, PresetCategory.CUSTOM)
        val config = EqConfig(
            enabled = true,
            mode = AttachMode.GLOBAL,
            autoSwitch = true,
            profiles = mapOf(
                beats.key to DeviceProfile(
                    beats.key, beats.displayName,
                    EqSettings().withBand(2, 3.5f).copy(bassDb = 4f, balance = -0.25f, limiter = false),
                    CorrectionSpec.BuiltIn(Corrections.BEATS_IN_EAR),
                ),
                "speaker" to DeviceProfile(
                    "speaker", "Phone speaker",
                    correction = CorrectionSpec.Imported("My AutoEq", "GraphicEQ: 20 1; 20000 2"),
                ),
            ),
            appPresets = mapOf("com.podbean.app.podcast" to Presets.VOICE_ID),
            customPresets = listOf(custom),
        )
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
    }

    @Test
    fun badJsonFallsBackToDefaults() {
        assertEquals(EqConfig(), ConfigCodec.decode(null))
        assertEquals(EqConfig(), ConfigCodec.decode("{not json"))
        assertEquals(EqConfig(), ConfigCodec.decode("""{"mode":"NOPE"}"""))
    }

    @Test
    fun profilesFollowTheDeviceOnlyWhenAutoSwitchIsOn() {
        val config = EqConfig()
        assertEquals(beats.key, config.profileFor(beats).key)
        assertEquals("Beats Fit Pro", config.profileFor(beats).name)
        val single = config.copy(autoSwitch = false)
        assertEquals(EqConfig.DEFAULT_PROFILE_KEY, single.profileFor(beats).key)
    }

    @Test
    fun appPresetsResolveToBands() {
        val config = EqConfig(appPresets = mapOf("com.podbean.app.podcast" to Presets.VOICE_ID, "x" to "deleted"))
        assertEquals(Presets.builtIn(Presets.VOICE_ID)!!.bandsDb, config.bandsForApp("com.podbean.app.podcast"))
        assertNull(config.bandsForApp("x"))
        assertNull(config.bandsForApp("com.spotify.music"))
        assertNull(config.bandsForApp(null))
    }
}
