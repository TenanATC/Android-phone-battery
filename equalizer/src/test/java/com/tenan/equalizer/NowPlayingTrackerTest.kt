package com.tenan.equalizer

import com.tenan.equalizer.session.NowPlayingTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTrackerTest {
    private val spotify = "com.spotify.music"
    private val podbean = "com.podbean.app.podcast"

    @Test
    fun theMostRecentlyStartedPlayerWins() {
        val t = NowPlayingTracker()
        assertTrue(t.onState(spotify, true, 1_000))
        assertEquals(spotify, t.current)
        assertTrue(t.onState(podbean, true, 2_000))
        assertEquals(podbean, t.current)
        // Spotify reporting "still playing" again does not take over.
        assertFalse(t.onState(spotify, true, 3_000))
        assertEquals(podbean, t.current)
    }

    @Test
    fun stoppingHandsBackToAnAppStillPlaying() {
        val t = NowPlayingTracker()
        t.onState(spotify, true, 1_000)
        t.onState(podbean, true, 2_000)
        t.onState(podbean, false, 3_000)
        assertEquals(spotify, t.current)
    }

    @Test
    fun pausingKeepsTheLastPlayersCurve() {
        val t = NowPlayingTracker()
        t.onState(podbean, true, 1_000)
        assertFalse(t.onState(podbean, false, 2_000))
        assertEquals(podbean, t.current)
        // Resuming restarts its clock but changes nothing audible.
        assertFalse(t.onState(podbean, true, 3_000))
    }

    @Test
    fun aClosedAppIsForgotten() {
        val t = NowPlayingTracker()
        t.onState(podbean, true, 1_000)
        t.onState(podbean, false, 2_000)
        assertTrue(t.onRemoved(podbean))
        assertNull(t.current)
    }

    @Test
    fun anAppThatNeverPlayedIsNeverChosen() {
        val t = NowPlayingTracker()
        assertFalse(t.onState(spotify, false, 1_000))
        assertNull(t.current)
    }
}
