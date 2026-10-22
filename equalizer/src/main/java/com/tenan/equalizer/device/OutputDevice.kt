package com.tenan.equalizer.device

enum class OutputKind { BLUETOOTH, USB, WIRED, HEARING_AID, SPEAKER, OTHER }

/** Where media audio is going right now; each one gets its own EQ profile. */
data class OutputDevice(val kind: OutputKind, val name: String = "") {

    /** Stable profile key. Bluetooth and USB devices are told apart by name. */
    val key: String
        get() = when (kind) {
            OutputKind.BLUETOOTH, OutputKind.USB -> "${kind.name.lowercase()}:$name"
            else -> kind.name.lowercase()
        }

    val displayName: String
        get() = when (kind) {
            OutputKind.SPEAKER -> "Phone speaker"
            OutputKind.WIRED -> "Wired headphones"
            OutputKind.HEARING_AID -> name.ifBlank { "Hearing aid" }
            OutputKind.USB -> name.ifBlank { "USB audio" }
            OutputKind.BLUETOOTH -> name.ifBlank { "Bluetooth audio" }
            OutputKind.OTHER -> name.ifBlank { "Audio output" }
        }

    val beats: BeatsModel?
        get() = if (kind == OutputKind.BLUETOOTH) BeatsCatalog.identify(name) else null

    companion object {
        val SPEAKER = OutputDevice(OutputKind.SPEAKER)

        /**
         * Android routes media to the most recently connected personal device,
         * which in practice means headphones win over the speaker and Bluetooth
         * wins when both a wire and Bluetooth are present.
         */
        private val PRIORITY = listOf(
            OutputKind.BLUETOOTH, OutputKind.USB, OutputKind.WIRED,
            OutputKind.HEARING_AID, OutputKind.SPEAKER, OutputKind.OTHER,
        )

        fun pickMediaOutput(outputs: List<OutputDevice>): OutputDevice =
            outputs.minByOrNull { PRIORITY.indexOf(it.kind) } ?: SPEAKER
    }
}
