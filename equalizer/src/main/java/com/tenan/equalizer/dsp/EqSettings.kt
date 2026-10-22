package com.tenan.equalizer.dsp

/** Everything the user tunes for one output device. */
data class EqSettings(
    /** Gains for [Bands.GRAPHIC_HZ], in dB. */
    val bandsDb: List<Float> = FLAT,
    /** The preset the bands came from; null once the user moves a slider. */
    val presetId: String? = Presets.FLAT_ID,
    val preampDb: Float = 0f,
    /** Low-shelf boost at ~105 Hz, 0..12 dB. */
    val bassDb: Float = 0f,
    /** High-shelf boost at ~8 kHz, -6..12 dB. */
    val trebleDb: Float = 0f,
    /** Virtualizer (stereo widening) strength, 0..1. */
    val virtualizer: Float = 0f,
    /** LoudnessEnhancer gain, 0..10 dB. */
    val loudnessDb: Float = 0f,
    /** Channel balance, -1 (left only) .. 1 (right only). */
    val balance: Float = 0f,
    val limiter: Boolean = true,
    /** Lower the preamp just enough that no boost can push the signal past 0 dBFS. */
    val autoHeadroom: Boolean = true,
) {
    init {
        require(bandsDb.size == Bands.GRAPHIC_HZ.size) { "Expected ${Bands.GRAPHIC_HZ.size} bands" }
    }

    fun withBand(index: Int, db: Float): EqSettings = copy(
        bandsDb = bandsDb.toMutableList().also {
            it[index] = db.coerceIn(Bands.MIN_GAIN_DB, Bands.MAX_GAIN_DB)
        },
        presetId = null,
    )

    fun withPreset(preset: Preset): EqSettings = copy(bandsDb = preset.bandsDb, presetId = preset.id)

    companion object {
        val FLAT: List<Float> = List(Bands.GRAPHIC_HZ.size) { 0f }
    }
}
