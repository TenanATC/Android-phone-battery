package com.tenan.equalizer

import com.tenan.equalizer.dsp.AutoEqParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqParserTest {

    @Test
    fun parsesEqualizerApoParametricExport() {
        val text = """
            Preamp: -6.4 dB
            Filter 1: ON LSC Fc 105 Hz Gain 6.2 dB Q 0.70
            Filter 2: ON PK Fc 1000 Hz Gain -3.0 dB Q 1.00
            Filter 3: OFF PK Fc 3000 Hz Gain 9.0 dB Q 1.00
            Filter 4: ON HSC Fc 10000 Hz Gain 2.1 dB Q 0.70
        """.trimIndent()
        val parsed = AutoEqParser.parse(text).getOrThrow()
        assertEquals("Parametric, 3 filters", parsed.summary)
        assertEquals(-6.4, parsed.preampDb!!, 1e-9)
        assertEquals(6.2, parsed.curve.dbAt(20.0), 0.3)
        assertEquals(-3.0, parsed.curve.dbAt(1000.0), 0.1)
        // The OFF filter at 3 kHz is ignored.
        assertTrue(parsed.curve.dbAt(3000.0) < 1.0)
    }

    @Test
    fun parsesGraphicEqExport() {
        val text = "GraphicEQ: 20 -2.0; 100 1.0; 1000 0.0; 10000 4.0; 20000 4.0"
        val parsed = AutoEqParser.parse(text).getOrThrow()
        assertEquals("GraphicEQ, 5 points", parsed.summary)
        assertEquals(1.0, parsed.curve.dbAt(100.0), 1e-9)
        assertEquals(4.0, parsed.curve.dbAt(15000.0), 1e-9)
        assertEquals(null, parsed.preampDb)
    }

    @Test
    fun acceptsFiltersWithoutNumbersOrGain() {
        val text = "Filter: ON HP Fc 30 Hz\nFilter: ON PEQ Fc 2000 Hz Gain 2 dB Q 2"
        val parsed = AutoEqParser.parse(text).getOrThrow()
        assertEquals("Parametric, 2 filters", parsed.summary)
        assertTrue(parsed.curve.dbAt(10.0) < -6)
    }

    @Test
    fun rejectsTextWithoutFilters() {
        assertTrue(AutoEqParser.parse("hello world").isFailure)
        assertTrue(AutoEqParser.parse("GraphicEQ: 20 1").isFailure)
        assertTrue(AutoEqParser.parse("").isFailure)
    }
}
