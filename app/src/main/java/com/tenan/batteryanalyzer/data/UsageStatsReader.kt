package com.tenan.batteryanalyzer.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.concurrent.TimeUnit

data class AppUsage(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
    val lastUsed: Long,
)

/**
 * Ranks apps by foreground time using UsageStatsManager. Requires the user to
 * grant "Usage access" in Settings (a special-access permission with no
 * runtime dialog).
 */
object UsageStatsReader {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun topApps(context: Context, days: Int, limit: Int = 15): List<AppUsage> {
        if (!hasPermission(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - TimeUnit.DAYS.toMillis(days.toLong())

        val byPackage = usm.queryAndAggregateUsageStats(begin, end)
        val pm = context.packageManager

        return byPackage.values
            .asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .filter { it.packageName != context.packageName }
            .mapNotNull { stats ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(stats.packageName, 0)).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    return@mapNotNull null // uninstalled or inaccessible
                }
                AppUsage(
                    packageName = stats.packageName,
                    label = label,
                    foregroundMillis = stats.totalTimeInForeground,
                    lastUsed = stats.lastTimeUsed,
                )
            }
            .sortedByDescending { it.foregroundMillis }
            .take(limit)
            .toList()
    }
}
