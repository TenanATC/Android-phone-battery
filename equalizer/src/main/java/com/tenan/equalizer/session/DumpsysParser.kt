package com.tenan.equalizer.session

data class DetectedPlayer(
    val uid: Int,
    val sessionId: Int,
    val state: String,
    val usage: String?,
)

/**
 * Parses the player list in `dumpsys audio`. Each active player prints as
 *
 * ```
 * AudioPlaybackConfiguration piid:63 deviceId:2 type:android.media.AudioTrack u/pid:10158/4728
 *   state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC … sessionId:1257
 * ```
 *
 * (all on one line). This is how EQ apps reach players that never announce
 * their audio session. Event-log lines use "uid/pid:" instead and are skipped
 * because they describe history, not live players.
 */
object DumpsysParser {
    private val UID = Regex("""(?<![A-Za-z])u/pid:\s*(\d+)/""")
    private val SESSION = Regex("""\bsession(?:Id)?:\s*(\d+)""")
    private val STATE = Regex("""\bstate:\s*([a-z_]+)""")
    private val USAGE = Regex("""\busage=(USAGE_[A-Z_]+)""")

    private val MEDIA_USAGES = setOf("USAGE_MEDIA", "USAGE_UNKNOWN", "USAGE_GAME")

    fun parse(dump: String): List<DetectedPlayer> = dump.lineSequence().mapNotNull { line ->
        val uid = UID.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        val session = SESSION.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        val state = STATE.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
        DetectedPlayer(uid, session, state, USAGE.find(line)?.groupValues?.get(1))
    }.toList()

    /**
     * Sessions worth equalising: media (not alarms, calls or navigation), from
     * an app rather than the system, and not yet released. Paused players are
     * kept so the EQ is already in place when playback resumes.
     */
    fun mediaSessions(players: List<DetectedPlayer>, ownUid: Int): List<DetectedPlayer> =
        players
            .filter { p ->
                p.sessionId > 0 &&
                    p.state != "released" &&
                    (p.usage == null || p.usage in MEDIA_USAGES) &&
                    p.uid % 100_000 >= FIRST_APPLICATION_UID &&
                    p.uid != ownUid
            }
            .sortedByDescending { it.state == "started" }
            .distinctBy { it.sessionId }

    private const val FIRST_APPLICATION_UID = 10_000
}
