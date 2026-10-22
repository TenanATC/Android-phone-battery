package com.tenan.equalizer.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.util.concurrent.TimeUnit

/**
 * "Advanced app detection": finds every playing app's audio session from the
 * system playback list, including apps that never announce their session.
 * Works only once the user has granted DUMP over adb; the same technique the
 * leading Android EQ apps use for the same reason.
 */
object DumpsysScanner {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    /** Blocking; call off the main thread. Null if the dump could not be read. */
    fun scan(context: Context): List<AudioSessionInfo>? {
        val dump = runDumpsys() ?: return null
        val pm = context.packageManager
        return DumpsysParser.mediaSessions(DumpsysParser.parse(dump), Process.myUid()).map { p ->
            AudioSessionInfo(
                sessionId = p.sessionId,
                packageName = pm.getPackagesForUid(p.uid)?.firstOrNull(),
                source = SessionSource.DETECTED,
            )
        }
    }

    private fun runDumpsys(): String? = try {
        val process = ProcessBuilder("dumpsys", "audio").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroy()
        // Without the permission dumpsys prints a "Permission Denial" banner instead.
        text.takeUnless { "Permission Denial" in it }
    } catch (e: Exception) {
        null
    }
}
