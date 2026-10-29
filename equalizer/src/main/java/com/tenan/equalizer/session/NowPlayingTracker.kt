package com.tenan.equalizer.session

/**
 * Decides which app's preset the global mix should use, from the playback
 * states of the active media sessions.
 *
 * The app that most recently *started* playing wins, so starting a podcast
 * while Spotify is paused (or even still playing) switches to the podcast's
 * curve. When playback pauses the last player is kept rather than dropping
 * back to the device curve, so pausing and resuming never makes the sound
 * jump.
 */
class NowPlayingTracker {
    private val startedAt = mutableMapOf<String, Long>()
    private val playing = mutableSetOf<String>()

    var current: String? = null
        private set

    /** Returns true if [current] changed. */
    fun onState(packageName: String, isPlaying: Boolean, nowMs: Long): Boolean {
        if (isPlaying) {
            if (playing.add(packageName)) startedAt[packageName] = nowMs
        } else {
            playing.remove(packageName)
        }
        return recompute()
    }

    /** A session went away entirely (the app was closed). Returns true if [current] changed. */
    fun onRemoved(packageName: String): Boolean {
        playing.remove(packageName)
        startedAt.remove(packageName)
        return recompute()
    }

    private fun recompute(): Boolean {
        val next = playing.maxByOrNull { startedAt[it] ?: 0L } ?: current?.takeIf { it in startedAt }
        if (next == current) return false
        current = next
        return true
    }
}
