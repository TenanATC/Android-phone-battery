package com.tenan.equalizer.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class SessionSource {
    /** The player broadcast ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION (Spotify and most players do). */
    ANNOUNCED,
    /** Found by reading the system's playback list (needs the DUMP permission). */
    DETECTED,
}

data class AudioSessionInfo(
    val sessionId: Int,
    val packageName: String?,
    val source: SessionSource,
    val openedAtMs: Long = 0L,
)

/**
 * The audio sessions currently eligible for the EQ, merged from both sources.
 * Announced sessions live until the player closes them; detected sessions are
 * replaced wholesale on every scan. A session seen by both stays ANNOUNCED.
 */
class SessionRegistry {
    private val _sessions = MutableStateFlow<Map<Int, AudioSessionInfo>>(emptyMap())
    val sessions: StateFlow<Map<Int, AudioSessionInfo>> = _sessions

    fun announce(sessionId: Int, packageName: String?, nowMs: Long = System.currentTimeMillis()) {
        if (sessionId <= 0) return
        _sessions.update { current ->
            val existing = current[sessionId]
            if (existing?.source == SessionSource.ANNOUNCED && existing.packageName == packageName) {
                current
            } else {
                current + (sessionId to AudioSessionInfo(
                    sessionId,
                    packageName ?: existing?.packageName,
                    SessionSource.ANNOUNCED,
                    nowMs,
                ))
            }
        }
    }

    fun close(sessionId: Int) {
        _sessions.update { current ->
            if (current[sessionId]?.source == SessionSource.ANNOUNCED) current - sessionId else current
        }
    }

    fun replaceDetected(detected: List<AudioSessionInfo>) {
        _sessions.update { current ->
            val announced = current.filterValues { it.source == SessionSource.ANNOUNCED }
            val fresh = detected
                .filter { it.sessionId > 0 && it.sessionId !in announced }
                .associate { it.sessionId to it.copy(source = SessionSource.DETECTED) }
            val next = announced + fresh
            if (next == current) current else next
        }
    }

    fun restoreAnnounced(saved: List<AudioSessionInfo>) {
        _sessions.update { current ->
            current + saved.filter { it.sessionId > 0 && it.sessionId !in current }
                .associate { it.sessionId to it.copy(source = SessionSource.ANNOUNCED) }
        }
    }

    fun announced(): List<AudioSessionInfo> =
        _sessions.value.values.filter { it.source == SessionSource.ANNOUNCED }
}
