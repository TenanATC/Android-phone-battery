package com.tenan.equalizer

import com.tenan.equalizer.session.AudioSessionInfo
import com.tenan.equalizer.session.DumpsysParser
import com.tenan.equalizer.session.SessionRegistry
import com.tenan.equalizer.session.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private val dump = """
        Audio event log: playback activity as reported through PlayerBase
        10-22 08:01:12:001 new player piid:63 uid/pid:10158/4728 type:android.media.AudioTrack attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null session:999
          players:
          AudioPlaybackConfiguration piid:63 deviceId:2 type:android.media.AudioTrack u/pid:10158/4728 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null sessionId:1257 mutedState:none
          AudioPlaybackConfiguration piid:71 deviceId:2 type:android.media.MediaPlayer u/pid:10201/5100 state:paused attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_SPEECH flags=0x0 tags= bundle=null sessionId:1401
          AudioPlaybackConfiguration piid:72 deviceId:2 type:android.media.AudioTrack u/pid:10201/5100 state:released attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_SPEECH flags=0x0 tags= bundle=null sessionId:1399
          AudioPlaybackConfiguration piid:80 deviceId:2 type:android.media.SoundPool u/pid:1000/900 state:started attr:AudioAttributes: usage=USAGE_ASSISTANCE_SONIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x0 tags= bundle=null sessionId:17
          AudioPlaybackConfiguration piid:81 deviceId:2 type:android.media.AudioTrack u/pid:10300/6000 state:started attr:AudioAttributes: usage=USAGE_NOTIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x0 tags= bundle=null sessionId:1500
          AudioPlaybackConfiguration piid:82 deviceId:2 type:android.media.AudioTrack u/pid:1010158/7000 state:idle attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null sessionId:1600
    """.trimIndent()

    @Test
    fun parsesLivePlayersAndSkipsTheEventLog() {
        val players = DumpsysParser.parse(dump)
        assertEquals(listOf(1257, 1401, 1399, 17, 1500, 1600), players.map { it.sessionId })
        assertEquals(10158, players[0].uid)
        assertEquals("started", players[0].state)
        assertEquals("USAGE_MEDIA", players[0].usage)
    }

    @Test
    fun keepsOnlyAppMediaSessions() {
        val sessions = DumpsysParser.mediaSessions(DumpsysParser.parse(dump), ownUid = 10999)
        // Released, system-uid and notification players are dropped; a
        // secondary-user app uid (1010158) is kept.
        assertEquals(setOf(1257, 1401, 1600), sessions.map { it.sessionId }.toSet())
        val withoutSelf = DumpsysParser.mediaSessions(DumpsysParser.parse(dump), ownUid = 10158)
        assertTrue(withoutSelf.none { it.uid == 10158 })
    }

    @Test
    fun announcedSessionsSurviveScansAndCloseOnlyOnRequest() {
        val registry = SessionRegistry()
        registry.announce(100, "com.spotify.music")
        registry.replaceDetected(listOf(AudioSessionInfo(200, "com.podbean.app.podcast", SessionSource.DETECTED)))
        assertEquals(setOf(100, 200), registry.sessions.value.keys)

        registry.replaceDetected(emptyList())
        assertEquals(setOf(100), registry.sessions.value.keys)

        // Closing a detected-only session is a no-op; closing an announced one removes it.
        registry.replaceDetected(listOf(AudioSessionInfo(200, null, SessionSource.DETECTED)))
        registry.close(200)
        assertEquals(setOf(100, 200), registry.sessions.value.keys)
        registry.close(100)
        assertEquals(setOf(200), registry.sessions.value.keys)
    }

    @Test
    fun aSessionSeenBothWaysStaysAnnounced() {
        val registry = SessionRegistry()
        registry.announce(100, "com.spotify.music")
        registry.replaceDetected(listOf(AudioSessionInfo(100, "com.spotify.music", SessionSource.DETECTED)))
        assertEquals(SessionSource.ANNOUNCED, registry.sessions.value.getValue(100).source)
        registry.announce(0, "bad")
        registry.announce(-4, "bad")
        assertEquals(setOf(100), registry.sessions.value.keys)
    }
}
