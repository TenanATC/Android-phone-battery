package com.tenan.batteryanalyzer.analysis

import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import com.tenan.batteryanalyzer.data.AppUsage
import com.tenan.batteryanalyzer.data.BatterySnapshot
import java.util.concurrent.TimeUnit

enum class Severity { HIGH, MEDIUM, LOW, INFO }

data class Suggestion(
    val id: String,
    val severity: Severity,
    val title: String,
    val detail: String,
    /** Settings action to deep-link the user to, when one exists. */
    val settingsAction: String? = null,
)

/**
 * Rule-based recommendation engine. Every rule reads real state from this
 * phone — system settings, the sampled history, usage stats, and the OEM
 * profile — so the output is specific to the device, not generic advice.
 */
object SuggestionEngine {

    fun generate(
        context: Context,
        profile: DeviceProfile,
        snapshot: BatterySnapshot?,
        stats: DrainStats,
        topApps: List<AppUsage>,
        hasUsagePermission: Boolean,
    ): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        val resolver = context.contentResolver
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        addScreenRules(out, resolver, context)
        addDrainRules(out, stats, pm)
        addChargingRules(out, profile, snapshot, stats)
        addAppRules(out, profile, topApps, hasUsagePermission)
        addMiscRules(out, resolver, context, profile)

        return out.sortedBy { it.severity.ordinal }
    }

    // ---- Screen ------------------------------------------------------------

    private fun addScreenRules(
        out: MutableList<Suggestion>, resolver: ContentResolver, context: Context
    ) {
        val timeoutMs = Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
        if (timeoutMs > 60_000) {
            out += Suggestion(
                id = "screen_timeout",
                severity = if (timeoutMs >= 300_000) Severity.HIGH else Severity.MEDIUM,
                title = "Screen timeout is ${timeoutMs / 60_000} minutes",
                detail = "Your screen stays on ${timeoutMs / 60_000} minutes after you stop " +
                    "touching it. The display is usually the #1 battery consumer — dropping " +
                    "this to 30 seconds or 1 minute is one of the highest-impact changes you can make.",
                settingsAction = Settings.ACTION_DISPLAY_SETTINGS,
            )
        }

        val brightnessMode = Settings.System.getInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        if (brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
            val brightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            val high = brightness in 0..255 && brightness > 190
            out += Suggestion(
                id = "adaptive_brightness",
                severity = if (high) Severity.HIGH else Severity.MEDIUM,
                title = if (high) "Brightness is fixed at a high level" else "Adaptive brightness is off",
                detail = "You're using manual brightness" +
                    (if (high) " set near maximum. " else ". ") +
                    "Adaptive brightness lets the phone dim the display whenever ambient " +
                    "light allows, which meaningfully cuts display power over a full day.",
                settingsAction = Settings.ACTION_DISPLAY_SETTINGS,
            )
        }

        val nightMode = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        if (nightMode != Configuration.UI_MODE_NIGHT_YES) {
            out += Suggestion(
                id = "dark_theme",
                severity = Severity.LOW,
                title = "Dark theme is off",
                detail = "Most modern phones (including yours, most likely) have OLED screens, " +
                    "where black pixels are simply switched off. Enabling dark theme " +
                    "system-wide can cut display power noticeably at medium/high brightness.",
                settingsAction = Settings.ACTION_DISPLAY_SETTINGS,
            )
        }
    }

    // ---- Drain patterns from history ----------------------------------------

    private fun addDrainRules(out: MutableList<Suggestion>, stats: DrainStats, pm: PowerManager) {
        val idle = stats.screenOffDrainPerHour
        if (idle != null && idle > 2.0) {
            out += Suggestion(
                id = "idle_drain",
                severity = if (idle > 4.0) Severity.HIGH else Severity.MEDIUM,
                title = "High idle drain: ${"%.1f".format(idle)}%/hr with the screen off",
                detail = "A healthy phone loses roughly 0.5–1.5% per hour while idle. Yours is " +
                    "losing ${"%.1f".format(idle)}%/hr, which points to apps waking the phone in " +
                    "the background (sync, location polling, push loops). Check the app " +
                    "consumption tab and restrict the worst offenders, and consider turning " +
                    "off always-on display if you use it.",
            )
        }

        val active = stats.screenOnDrainPerHour
        if (active != null && active > 15.0) {
            out += Suggestion(
                id = "active_drain",
                severity = if (active > 25.0) Severity.HIGH else Severity.MEDIUM,
                title = "Heavy screen-on drain: ${"%.1f".format(active)}%/hr",
                detail = "While you're actively using the phone it drains " +
                    "${"%.1f".format(active)}%/hr. Typical mixed use is 8–15%/hr. High refresh " +
                    "rate (120Hz), max brightness, gaming, camera, and hotspot are the usual " +
                    "causes. If your display settings offer a 'Standard'/60Hz or adaptive " +
                    "refresh option, it can stretch screen time significantly.",
            )
        }

        if (!pm.isPowerSaveMode && idle != null && idle > 2.0) {
            out += Suggestion(
                id = "battery_saver",
                severity = Severity.MEDIUM,
                title = "Battery saver is off despite high background drain",
                detail = "Battery saver defers background work, sync, and location use. Given " +
                    "your idle drain, scheduling it (e.g. auto-on at 30–50%) would recover real " +
                    "hours. It changes little about foreground use.",
                settingsAction = Settings.ACTION_BATTERY_SAVER_SETTINGS,
            )
        }
    }

    // ---- Charging habits & battery health -----------------------------------

    private fun addChargingRules(
        out: MutableList<Suggestion>,
        profile: DeviceProfile,
        snapshot: BatterySnapshot?,
        stats: DrainStats,
    ) {
        if (stats.hoursAtFullWhilePlugged > 3.0) {
            val feature = profile.chargeLimitFeature
            out += Suggestion(
                id = "full_plugged",
                severity = Severity.MEDIUM,
                title = "Phone sits at 100% while plugged in (${"%.0f".format(stats.hoursAtFullWhilePlugged)}h observed)",
                detail = "Holding a lithium battery at 100% (typically overnight charging) is " +
                    "the main driver of long-term capacity loss." +
                    (if (feature != null) {
                        " Your ${profile.manufacturer} has a built-in fix: enable $feature."
                    } else {
                        " If your phone offers a charge-limit or optimized-charging option, enable it."
                    }),
            )
        }

        if (stats.deepDischargeCount >= 3) {
            out += Suggestion(
                id = "deep_discharge",
                severity = Severity.LOW,
                title = "Frequent deep discharges (${stats.deepDischargeCount}× below 15% recently)",
                detail = "Running the battery near empty stresses it almost as much as holding " +
                    "it at 100%. Keeping it roughly between 20% and 80% maximizes the number of " +
                    "cycles it will survive — top up earlier when you can.",
            )
        }

        val chargeTemp = stats.avgChargingTemperatureC
        if (chargeTemp != null && chargeTemp > 38f) {
            out += Suggestion(
                id = "hot_charging",
                severity = Severity.HIGH,
                title = "Battery runs hot while charging (avg ${"%.1f".format(chargeTemp)}°C)",
                detail = "Heat is the battery's worst enemy, and it compounds while charging. " +
                    "Take the case off when fast-charging, avoid charging in direct sun or on " +
                    "soft surfaces, and prefer slower (non-fast) chargers overnight. Wireless " +
                    "charging also runs hotter than wired.",
            )
        }

        if (snapshot != null &&
            (snapshot.health == BatteryManager.BATTERY_HEALTH_DEAD ||
                snapshot.health == BatteryManager.BATTERY_HEALTH_OVERHEAT)
        ) {
            out += Suggestion(
                id = "health_flag",
                severity = Severity.HIGH,
                title = "The system reports a battery health problem",
                detail = "Android is flagging this battery as degraded or overheating. If the " +
                    "phone also shuts down unexpectedly or the battery drains very fast, a " +
                    "battery replacement is the real fix — software tuning won't recover it.",
            )
        }

        if (snapshot != null && snapshot.cycleCount > 500) {
            out += Suggestion(
                id = "cycles",
                severity = Severity.INFO,
                title = "Battery has ${snapshot.cycleCount} charge cycles",
                detail = "Lithium batteries typically retain ~80% of design capacity after " +
                    "500 full cycles. Yours has logged ${snapshot.cycleCount}, so shorter runtime " +
                    "than when new is expected — the charging-habit tips above slow further loss.",
            )
        }
    }

    // ---- Per-app consumption -------------------------------------------------

    private fun addAppRules(
        out: MutableList<Suggestion>,
        profile: DeviceProfile,
        topApps: List<AppUsage>,
        hasUsagePermission: Boolean,
    ) {
        if (!hasUsagePermission) {
            out += Suggestion(
                id = "grant_usage",
                severity = Severity.INFO,
                title = "Grant Usage access to unlock per-app analysis",
                detail = "With Usage access, this app can rank the apps consuming your " +
                    "screen-on time and point out which ones to restrict. Nothing leaves the " +
                    "phone — the analysis is entirely local.",
                settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS,
            )
            return
        }

        val heavy = topApps.filter { it.foregroundMillis > TimeUnit.HOURS.toMillis(2) }
        if (heavy.isNotEmpty()) {
            val names = heavy.take(3).joinToString(", ") { it.label }
            out += Suggestion(
                id = "heavy_apps",
                severity = Severity.MEDIUM,
                title = "Heaviest apps this week: $names",
                detail = "These apps dominate your screen time and therefore your battery. For " +
                    "any you don't need running in the background, restrict them via " +
                    "${profile.backgroundLimitPath}. Social and video apps also poll in the " +
                    "background even when closed.",
            )
        }
    }

    // ---- Misc system toggles ---------------------------------------------------

    private fun addMiscRules(
        out: MutableList<Suggestion>,
        resolver: ContentResolver,
        context: Context,
        profile: DeviceProfile,
    ) {
        if (ContentResolver.getMasterSyncAutomatically()) {
            out += Suggestion(
                id = "auto_sync",
                severity = Severity.INFO,
                title = "Account auto-sync is on",
                detail = "Auto-sync itself is cheap, but each additional synced account " +
                    "(mail, contacts, photos) adds periodic wakeups. Prune accounts you no " +
                    "longer use in ${profile.batteryDashboardName}'s account settings.",
                settingsAction = Settings.ACTION_SYNC_SETTINGS,
            )
        }

        val wifiScanning = Settings.Global.getInt(resolver, "wifi_scan_always_enabled", 0) == 1
        val bleScanning = Settings.Global.getInt(resolver, "ble_scan_always_enabled", 0) == 1
        if (wifiScanning || bleScanning) {
            val what = listOfNotNull(
                if (wifiScanning) "Wi-Fi" else null,
                if (bleScanning) "Bluetooth" else null,
            ).joinToString(" and ")
            out += Suggestion(
                id = "bg_scanning",
                severity = Severity.LOW,
                title = "$what scanning stays on even when toggled off",
                detail = "Location services keep scanning for $what networks in the background " +
                    "to improve positioning. Turning 'scanning' off under Location > Location " +
                    "services saves a small but constant drain.",
                settingsAction = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            )
        }

        // OEM-specific standing advice.
        when (profile.oem) {
            Oem.SAMSUNG -> out += Suggestion(
                id = "oem_tip",
                severity = Severity.INFO,
                title = "One UI tip: put unused apps to deep sleep",
                detail = "In Settings > Battery > Background usage limits, 'Put unused apps to " +
                    "sleep' plus a Deep sleeping list stops rarely-used apps from ever running " +
                    "in the background. Also check Device care > Battery for the built-in usage graph.",
            )
            Oem.GOOGLE -> out += Suggestion(
                id = "oem_tip",
                severity = Severity.INFO,
                title = "Pixel tip: keep Adaptive Battery on",
                detail = "Adaptive Battery learns which apps you actually use and limits the " +
                    "rest. Pair it with Extreme Battery Saver for travel days — it can double " +
                    "remaining runtime by pausing all but chosen apps.",
            )
            Oem.XIAOMI -> out += Suggestion(
                id = "oem_tip",
                severity = Severity.INFO,
                title = "MIUI/HyperOS tip: check autostart list",
                detail = "Xiaomi's Security app > Battery offers per-app 'Battery saver' modes, " +
                    "and the Autostart manager controls which apps may self-launch — trimming " +
                    "it cuts idle drain more than most toggles.",
            )
            Oem.ONEPLUS, Oem.OPPO -> out += Suggestion(
                id = "oem_tip",
                severity = Severity.INFO,
                title = "OxygenOS/ColorOS tip: use per-app battery optimization",
                detail = "Settings > Battery > More settings > Optimize battery use lets you " +
                    "set 'Optimize' or 'Intelligent control' per app; 'Sleep standby " +
                    "optimization' helps overnight drain.",
            )
            else -> {}
        }
    }
}
