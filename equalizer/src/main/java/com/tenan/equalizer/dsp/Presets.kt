package com.tenan.equalizer.dsp

enum class PresetCategory { GENERAL, MUSIC, VOICE, CUSTOM }

data class Preset(
    val id: String,
    val name: String,
    /** Gains for [Bands.GRAPHIC_HZ], in dB. */
    val bandsDb: List<Float>,
    val category: PresetCategory,
) {
    init {
        require(bandsDb.size == Bands.GRAPHIC_HZ.size) { "Expected ${Bands.GRAPHIC_HZ.size} bands" }
    }
}

/**
 * Built-in curves over 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz — the
 * genre set the popular Android EQ apps ship, plus voice curves aimed at
 * podcasts and audiobooks.
 */
object Presets {
    const val FLAT_ID = "flat"
    const val VOICE_ID = "voice"
    const val SPOKEN_ID = "spoken"

    private fun p(id: String, name: String, category: PresetCategory, vararg db: Number) =
        Preset(id, name, db.map { it.toFloat() }, category)

    val BUILT_IN: List<Preset> = listOf(
        p(FLAT_ID, "Flat", PresetCategory.GENERAL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        p("bass", "Bass Boost", PresetCategory.GENERAL, 6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
        p("bass_cut", "Bass Reducer", PresetCategory.GENERAL, -6, -5, -4, -2, 0, 0, 0, 0, 0, 0),
        p("treble", "Treble Boost", PresetCategory.GENERAL, 0, 0, 0, 0, 0, 0, 1, 3, 5, 6),
        p("v_shape", "V-Shape", PresetCategory.GENERAL, 5, 4, 2, 0, -2, -2, 0, 2, 4, 5),
        p("late_night", "Late Night", PresetCategory.GENERAL, 4, 3, 1, 0, -1, 0, 1, 2, 3, 3),
        // Voice: roll off rumble and boom, clear out 250 Hz mud, lift the
        // 2–4 kHz consonant region where speech intelligibility lives.
        p(VOICE_ID, "Podcast / Voice", PresetCategory.VOICE, -6, -4, -2, -1, 0, 1, 3, 3, 1, 0),
        p(SPOKEN_ID, "Audiobook", PresetCategory.VOICE, -8, -6, -3, -1, 0, 2, 3, 2, 0, -2),
        p("rock", "Rock", PresetCategory.MUSIC, 4, 3, 2, 0, -1, -1, 1, 3, 4, 4),
        p("pop", "Pop", PresetCategory.MUSIC, -1, 1, 3, 4, 3, 0, -1, -1, 0, 1),
        p("hiphop", "Hip-Hop", PresetCategory.MUSIC, 5, 5, 3, 1, -1, -1, 1, 0, 1, 2),
        p("electronic", "Electronic", PresetCategory.MUSIC, 5, 4, 1, 0, -2, 1, 0, 1, 4, 5),
        p("jazz", "Jazz", PresetCategory.MUSIC, 3, 2, 1, 2, -1, -1, 0, 1, 2, 3),
        p("classical", "Classical", PresetCategory.MUSIC, 4, 3, 2, 1, -1, -1, 0, 2, 3, 4),
        p("acoustic", "Acoustic", PresetCategory.MUSIC, 3, 3, 2, 1, 1, 1, 2, 2, 2, 1),
        p("rnb", "R&B", PresetCategory.MUSIC, 3, 6, 4, 1, -2, -1, 2, 2, 3, 3),
    )

    fun builtIn(id: String): Preset? = BUILT_IN.firstOrNull { it.id == id }
}
