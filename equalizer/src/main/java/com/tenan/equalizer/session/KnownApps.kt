package com.tenan.equalizer.session

import com.tenan.equalizer.dsp.Presets

enum class AppCategory(val label: String) {
    MUSIC("Music"),
    PODCAST("Podcasts"),
    AUDIOBOOK("Audiobooks"),
    VIDEO("Video"),
}

data class KnownApp(
    val packageName: String,
    val name: String,
    val category: AppCategory,
) {
    /** The preset offered as a one-tap default for this kind of app. */
    val suggestedPresetId: String?
        get() = when (category) {
            AppCategory.PODCAST -> Presets.VOICE_ID
            AppCategory.AUDIOBOOK -> Presets.SPOKEN_ID
            AppCategory.MUSIC, AppCategory.VIDEO -> null
        }
}

/** Major audio and podcast apps, listed on the Apps tab when installed. */
object KnownApps {
    val ALL = listOf(
        KnownApp("com.spotify.music", "Spotify", AppCategory.MUSIC),
        KnownApp("com.podbean.app.podcast", "Podbean", AppCategory.PODCAST),
        KnownApp("au.com.shiftyjelly.pocketcasts", "Pocket Casts", AppCategory.PODCAST),
        KnownApp("com.bambuna.podcastaddict", "Podcast Addict", AppCategory.PODCAST),
        KnownApp("fm.castbox.audiobook.radio.podcast", "Castbox", AppCategory.PODCAST),
        KnownApp("de.danoeh.antennapod", "AntennaPod", AppCategory.PODCAST),
        KnownApp("com.google.android.apps.youtube.music", "YouTube Music", AppCategory.MUSIC),
        KnownApp("com.apple.android.music", "Apple Music", AppCategory.MUSIC),
        KnownApp("com.amazon.mp3", "Amazon Music", AppCategory.MUSIC),
        KnownApp("com.aspiro.tidal", "TIDAL", AppCategory.MUSIC),
        KnownApp("deezer.android.app", "Deezer", AppCategory.MUSIC),
        KnownApp("com.soundcloud.android", "SoundCloud", AppCategory.MUSIC),
        KnownApp("com.pandora.android", "Pandora", AppCategory.MUSIC),
        KnownApp("com.clearchannel.iheartradio.controller", "iHeartRadio", AppCategory.MUSIC),
        KnownApp("com.sec.android.app.music", "Samsung Music", AppCategory.MUSIC),
        KnownApp("com.audible.application", "Audible", AppCategory.AUDIOBOOK),
        KnownApp("com.overdrive.mobile.android.libby", "Libby", AppCategory.AUDIOBOOK),
        KnownApp("com.google.android.youtube", "YouTube", AppCategory.VIDEO),
    )

    private val BY_PACKAGE = ALL.associateBy { it.packageName }

    fun find(packageName: String?): KnownApp? = packageName?.let { BY_PACKAGE[it] }
}
