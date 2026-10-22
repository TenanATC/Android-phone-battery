package com.tenan.equalizer.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tenan.equalizer.EqApp
import com.tenan.equalizer.MainActivity
import com.tenan.equalizer.R
import com.tenan.equalizer.data.AttachMode
import com.tenan.equalizer.data.EqConfig
import com.tenan.equalizer.device.CorrectionSpec
import com.tenan.equalizer.device.Corrections
import com.tenan.equalizer.device.OutputDevice
import com.tenan.equalizer.dsp.ProcessingPlan
import com.tenan.equalizer.dsp.ResponseCurve
import com.tenan.equalizer.session.AudioSessionInfo
import com.tenan.equalizer.session.DumpsysScanner
import com.tenan.equalizer.session.KnownApps
import com.tenan.equalizer.session.SessionBroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the audio effects for as long as the EQ is on. Reconciles three
 * inputs whenever any of them changes: the user's configuration, the set of
 * live audio sessions, and the current output device.
 */
class EqService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chains = mutableMapOf<Int, EffectChain>()
    private val sessionReceiver = SessionBroadcastReceiver()
    private lateinit var app: EqApp

    private var cachedCorrectionSpec: CorrectionSpec? = null
    private var cachedCorrection: ResponseCurve? = null
    private var lastNotificationText: String? = null

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = application as EqApp
        createChannel()
        val notification = buildNotification("Starting…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ContextCompat.registerReceiver(
            this, sessionReceiver, SessionBroadcastReceiver.FILTER, ContextCompat.RECEIVER_EXPORTED
        )

        scope.launch {
            combine(app.repository.config, app.sessions.sessions, app.outputs.current) { c, s, d ->
                Triple(c, s, d)
            }.conflate().collect { (config, sessions, device) -> reconcile(config, sessions, device) }
        }
        scope.launch { detectionLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TURN_OFF) app.repository.setEnabled(false)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        try {
            unregisterReceiver(sessionReceiver)
        } catch (e: IllegalArgumentException) {
            // Not registered; nothing to do.
        }
        releaseAll()
        super.onDestroy()
    }

    private fun reconcile(config: EqConfig, sessions: Map<Int, AudioSessionInfo>, device: OutputDevice) {
        if (!config.enabled) {
            releaseAll()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val profile = config.profileFor(device)
        val correction = correctionFor(profile.correction)
        val targets: Map<Int, String?> = when (config.mode) {
            AttachMode.GLOBAL -> mapOf(GLOBAL_SESSION to null)
            AttachMode.PER_APP -> sessions.mapValues { it.value.packageName }
        }

        (chains.keys - targets.keys).forEach { chains.remove(it)?.release() }
        val plans = mutableMapOf<List<Float>?, ProcessingPlan>()
        targets.forEach { (sessionId, packageName) ->
            val appBands = config.bandsForApp(packageName)
            val plan = plans.getOrPut(appBands) {
                ProcessingPlan.build(profile.settings, appBands, correction)
            }
            chains.getOrPut(sessionId) { EffectChain.open(sessionId) }.apply(plan)
        }
        app.engineStatus.value = chains.mapValues { it.value.status }

        val presetName = config.preset(profile.settings.presetId)?.name ?: "Custom"
        val where = when (config.mode) {
            AttachMode.GLOBAL -> "all apps"
            AttachMode.PER_APP -> when (val n = targets.size) {
                0 -> "waiting for a player"
                1 -> appLabel(targets.values.first())
                else -> "$n apps"
            }
        }
        updateNotification("${profile.name} · $presetName · $where")
    }

    private fun correctionFor(spec: CorrectionSpec): ResponseCurve? {
        if (spec != cachedCorrectionSpec) {
            cachedCorrectionSpec = spec
            cachedCorrection = Corrections.resolve(spec)
        }
        return cachedCorrection
    }

    /** Polls the system playback list while advanced detection is available. */
    private suspend fun detectionLoop() {
        while (scope.isActive) {
            val config = app.repository.config.value
            val scan = if (config.mode == AttachMode.PER_APP && DumpsysScanner.hasPermission(this)) {
                withContext(Dispatchers.IO) { DumpsysScanner.scan(this@EqService) }
            } else {
                null
            }
            app.sessions.replaceDetected(scan.orEmpty().filter { it.packageName != packageName })
            delay(SCAN_INTERVAL_MS)
        }
    }

    private fun releaseAll() {
        chains.values.forEach { it.release() }
        chains.clear()
        app.engineStatus.value = emptyMap()
    }

    private fun appLabel(packageName: String?): String {
        if (packageName == null) return "1 app"
        KnownApps.find(packageName)?.let { return it.name }
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val turnOff = PendingIntent.getService(
            this, 1, Intent(this, EqService::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eq)
            .setContentTitle("Equalizer on")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Turn off", turnOff)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: SecurityException) {
            // Notifications denied; the service keeps running regardless.
        }
    }

    companion object {
        private const val TAG = "EqService"
        private const val CHANNEL_ID = "eq_status"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TURN_OFF = "com.tenan.equalizer.TURN_OFF"
        private const val SCAN_INTERVAL_MS = 3_000L
        /** The global output mix. */
        const val GLOBAL_SESSION = 0

        /** False when Android refuses a background start (Android 12+ rules). */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, EqService::class.java))
            true
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException is an IllegalStateException.
            Log.w(TAG, "Could not start the EQ service now", e)
            false
        }
    }
}
