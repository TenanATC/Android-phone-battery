package com.tenan.equalizer.device

enum class BeatsFamily(val label: String) {
    IN_EAR("In-ear earbuds"),
    EARHOOK("Earhook sport earbuds"),
    ON_EAR("On-ear headphones"),
    OVER_EAR("Over-ear headphones"),
    SPEAKER("Speaker"),
    UNKNOWN("Beats audio device"),
}

data class BeatsModel(
    val name: String,
    val family: BeatsFamily,
    val hasAnc: Boolean,
)

/**
 * Recognises Beats products from their Bluetooth name. Matching needs the word
 * "beats" (which also covers "Powerbeats" and "BeatsX") so a renamed device
 * such as "Sam's Beats Fit Pro" still matches but a generic "Fit Pro" from
 * another brand does not.
 */
object BeatsCatalog {
    private data class Rule(val needles: List<String>, val model: BeatsModel)

    private fun rule(vararg needles: String, name: String, family: BeatsFamily, anc: Boolean) =
        Rule(needles.toList(), BeatsModel(name, family, anc))

    // Most specific first: "Studio Buds +" before "Studio Buds" before "Studio".
    private val RULES = listOf(
        rule("powerbeats pro 2", name = "Powerbeats Pro 2", family = BeatsFamily.EARHOOK, anc = true),
        rule("powerbeats pro", name = "Powerbeats Pro", family = BeatsFamily.EARHOOK, anc = false),
        rule("powerbeats", name = "Powerbeats", family = BeatsFamily.EARHOOK, anc = false),
        rule("fit pro", name = "Beats Fit Pro", family = BeatsFamily.IN_EAR, anc = true),
        rule("studio buds +", "studio buds+", "studio buds plus", name = "Beats Studio Buds +", family = BeatsFamily.IN_EAR, anc = true),
        rule("studio buds", name = "Beats Studio Buds", family = BeatsFamily.IN_EAR, anc = true),
        rule("solo buds", name = "Beats Solo Buds", family = BeatsFamily.IN_EAR, anc = false),
        rule("flex", name = "Beats Flex", family = BeatsFamily.IN_EAR, anc = false),
        rule("beatsx", "beats x", name = "BeatsX", family = BeatsFamily.IN_EAR, anc = false),
        rule("studio pro", name = "Beats Studio Pro", family = BeatsFamily.OVER_EAR, anc = true),
        rule("studio3", "studio 3", name = "Beats Studio3 Wireless", family = BeatsFamily.OVER_EAR, anc = true),
        rule("solo pro", name = "Beats Solo Pro", family = BeatsFamily.ON_EAR, anc = true),
        rule("solo 4", "solo4", name = "Beats Solo 4", family = BeatsFamily.ON_EAR, anc = false),
        rule("solo3", "solo 3", name = "Beats Solo3 Wireless", family = BeatsFamily.ON_EAR, anc = false),
        rule("pill", name = "Beats Pill", family = BeatsFamily.SPEAKER, anc = false),
    )

    fun identify(bluetoothName: String?): BeatsModel? {
        val name = bluetoothName?.lowercase()?.replace(Regex("""\s+"""), " ")?.trim() ?: return null
        if ("beats" !in name) return null
        return RULES.firstOrNull { r -> r.needles.any { it in name } }?.model
            ?: BeatsModel("Beats", BeatsFamily.UNKNOWN, hasAnc = false)
    }

    /** Practical, model-aware advice shown on the Device tab. */
    fun tips(model: BeatsModel): List<String> = buildList {
        if (model.family != BeatsFamily.SPEAKER) {
            add(
                "Beats use the AAC codec. With the ${model.name} connected, Developer options → " +
                    "Bluetooth audio codec should read AAC. If it reads SBC, look for an " +
                    "\"HD audio\" toggle in the device's Bluetooth settings (not every phone has one)."
            )
        }
        when (model.family) {
            BeatsFamily.IN_EAR -> add(
                "Seal matters more than any EQ: a loose tip loses most of the bass. Run the " +
                    "ear-tip fit test in Apple's Beats app for Android, then fine-tune here."
            )
            BeatsFamily.EARHOOK -> add(
                "Earhook buds sit looser than sealed in-ears, so bass varies with fit. Seat the " +
                    "tip firmly before judging the low end, and prefer small bass boosts."
            )
            BeatsFamily.ON_EAR -> add(
                "On-ear pads leak bass when they shift; small bass boosts (2–4 dB) go further " +
                    "than large ones, which mostly add distortion."
            )
            BeatsFamily.OVER_EAR -> add(
                "Glasses frames break the pad seal and thin the bass; re-check your curve with " +
                    "the headphones seated as you normally wear them."
            )
            BeatsFamily.SPEAKER, BeatsFamily.UNKNOWN -> Unit
        }
        if (model.hasAnc) {
            add(
                "Noise cancelling and Transparency change the tuning slightly. Switch modes " +
                    "with a long-press on the bud/control or in the Beats app; keep a separate " +
                    "preset if you mostly listen in one mode."
            )
        }
        add(
            "For podcasts on the ${model.name}, give Podbean (or your podcast app) the " +
                "\"Podcast / Voice\" preset on the Apps tab — it applies only to that app while " +
                "Spotify keeps your music curve."
        )
    }
}
