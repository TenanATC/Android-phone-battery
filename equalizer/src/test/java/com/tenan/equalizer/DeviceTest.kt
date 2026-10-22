package com.tenan.equalizer

import com.tenan.equalizer.device.BeatsCatalog
import com.tenan.equalizer.device.BeatsFamily
import com.tenan.equalizer.device.CorrectionSpec
import com.tenan.equalizer.device.Corrections
import com.tenan.equalizer.device.OutputDevice
import com.tenan.equalizer.device.OutputKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTest {

    @Test
    fun identifiesBeatsModelsFromBluetoothNames() {
        fun model(name: String) = BeatsCatalog.identify(name)?.name
        assertEquals("Beats Fit Pro", model("Beats Fit Pro"))
        assertEquals("Beats Fit Pro", model("Matt's Beats Fit Pro"))
        assertEquals("Beats Studio Buds +", model("Beats Studio Buds +"))
        assertEquals("Beats Studio Buds +", model("Beats  Studio Buds+"))
        assertEquals("Beats Studio Buds", model("Beats Studio Buds"))
        assertEquals("Beats Studio Pro", model("Beats Studio Pro"))
        assertEquals("Powerbeats Pro 2", model("Powerbeats Pro 2"))
        assertEquals("Powerbeats Pro", model("Powerbeats Pro"))
        assertEquals("Beats Solo Buds", model("Beats Solo Buds"))
        assertEquals("Beats Solo 4", model("Beats Solo 4"))
        assertEquals("Beats Flex", model("Beats Flex"))
        assertEquals("BeatsX", model("BeatsX"))
        assertEquals("Beats Pill", model("Beats Pill"))
        assertEquals("Beats", model("Beats Mystery Edition"))
    }

    @Test
    fun ignoresNonBeatsDevices() {
        assertNull(BeatsCatalog.identify("Galaxy Buds2 Pro"))
        assertNull(BeatsCatalog.identify("Fit Pro"))
        assertNull(BeatsCatalog.identify(null))
    }

    @Test
    fun everyHeadphoneFamilyHasACorrectionAndTips() {
        listOf("Beats Fit Pro", "Powerbeats Pro", "Beats Solo 4", "Beats Studio Pro").forEach { name ->
            val model = BeatsCatalog.identify(name)!!
            assertNotNull(name, Corrections.suggestedFor(model))
            assertTrue(BeatsCatalog.tips(model).isNotEmpty())
        }
        assertNull(Corrections.suggestedFor(BeatsCatalog.identify("Beats Pill")!!))
        assertEquals(BeatsFamily.EARHOOK, BeatsCatalog.identify("Powerbeats Pro 2")!!.family)
    }

    @Test
    fun builtInBeatsCorrectionsStayMild() {
        Corrections.BUILT_IN.filter { it.id.startsWith("beats") }.forEach { c ->
            var hz = 20.0
            while (hz < 20000) {
                assertTrue("${c.name} at $hz", kotlin.math.abs(c.curve.dbAt(hz)) <= 4.0)
                hz *= 1.05
            }
        }
    }

    @Test
    fun resolvesCorrectionSpecs() {
        assertNull(Corrections.resolve(CorrectionSpec.None))
        assertNotNull(Corrections.resolve(CorrectionSpec.BuiltIn(Corrections.BEATS_IN_EAR)))
        assertNull(Corrections.resolve(CorrectionSpec.BuiltIn("missing")))
        assertNotNull(Corrections.resolve(CorrectionSpec.Imported("x", "GraphicEQ: 20 1; 20000 2")))
        assertNull(Corrections.resolve(CorrectionSpec.Imported("x", "nonsense")))
    }

    @Test
    fun picksBluetoothOverWiredOverSpeaker() {
        val speaker = OutputDevice.SPEAKER
        val wired = OutputDevice(OutputKind.WIRED)
        val beats = OutputDevice(OutputKind.BLUETOOTH, "Beats Fit Pro")
        assertEquals(beats, OutputDevice.pickMediaOutput(listOf(speaker, wired, beats)))
        assertEquals(wired, OutputDevice.pickMediaOutput(listOf(speaker, wired)))
        assertEquals(speaker, OutputDevice.pickMediaOutput(emptyList()))
    }

    @Test
    fun profileKeysSeparateBluetoothDevicesByName() {
        val a = OutputDevice(OutputKind.BLUETOOTH, "Beats Fit Pro")
        val b = OutputDevice(OutputKind.BLUETOOTH, "Car Kit")
        assertTrue(a.key != b.key)
        assertEquals("speaker", OutputDevice.SPEAKER.key)
        assertNotNull(a.beats)
        assertNull(OutputDevice(OutputKind.WIRED, "Beats").beats)
    }
}
