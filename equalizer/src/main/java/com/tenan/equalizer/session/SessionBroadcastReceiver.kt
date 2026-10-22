package com.tenan.equalizer.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import com.tenan.equalizer.eqApp

/**
 * Receives the session announcements players such as Spotify send when
 * playback starts and stops. Declared in the manifest and also registered by
 * the running service, because Android 8+ may withhold implicit broadcasts
 * from manifest receivers; the registry ignores the resulting duplicates.
 */
class SessionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        val registry = context.eqApp.sessions
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> registry.announce(sessionId, packageName)
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> registry.close(sessionId)
        }
    }

    companion object {
        val FILTER = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
    }
}
