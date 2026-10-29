package com.tenan.equalizer.session

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.tenan.equalizer.eqApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Follows which app is playing, using the media sessions players publish for
 * their lock-screen and notification controls. Android exposes those only to
 * apps with Notification access, which the user grants in Settings — no
 * computer needed. In Global mode this is what lets Podbean get its own preset
 * even though it never announces its audio session.
 *
 * Notification contents are never read; only the media sessions are used.
 */
class NowPlayingMonitor(private val context: Context) {
    private val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val handler = Handler(Looper.getMainLooper())
    private val listenerComponent = ComponentName(context, MediaNotificationListener::class.java)
    private val tracker = NowPlayingTracker()
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var listening = false

    private val _nowPlaying = MutableStateFlow<String?>(null)
    /** Package of the app whose curve the global mix should follow. */
    val nowPlaying: StateFlow<String?> = _nowPlaying

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        track(controllers.orEmpty())
    }

    fun hasAccess(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Safe to call repeatedly; does nothing until access is granted. */
    fun start() {
        if (listening || !hasAccess()) return
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            listening = true
            track(sessionManager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            // Access was revoked between the check and the call.
            listening = false
        }
    }

    private fun track(controllers: List<MediaController>) {
        val live = controllers.associateBy { it.sessionToken }
        callbacks.keys.filter { it.sessionToken !in live }.forEach { gone ->
            gone.unregisterCallback(callbacks.remove(gone)!!)
            if (live.values.none { it.packageName == gone.packageName }) {
                publish(tracker.onRemoved(gone.packageName))
            }
        }
        val known = callbacks.keys.map { it.sessionToken }.toSet()
        controllers.filter { it.sessionToken !in known }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = onState(controller, state)
            }
            controller.registerCallback(callback, handler)
            callbacks[controller] = callback
            onState(controller, controller.playbackState)
        }
    }

    private fun onState(controller: MediaController, state: PlaybackState?) {
        val playing = state?.state == PlaybackState.STATE_PLAYING ||
            state?.state == PlaybackState.STATE_BUFFERING
        publish(tracker.onState(controller.packageName, playing, SystemClock.elapsedRealtime()))
    }

    private fun publish(changed: Boolean) {
        if (changed) _nowPlaying.value = tracker.current
    }
}

/**
 * Exists only so Android can grant Notification access, which unlocks the
 * media-session list for [NowPlayingMonitor]. Notifications are ignored.
 */
class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        eqApp.nowPlaying.start()
    }
}
