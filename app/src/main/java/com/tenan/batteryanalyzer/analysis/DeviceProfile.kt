package com.tenan.batteryanalyzer.analysis

import android.os.Build
import java.util.Locale

enum class Oem { SAMSUNG, GOOGLE, XIAOMI, ONEPLUS, OPPO, VIVO, MOTOROLA, HUAWEI, SONY, NOTHING, ASUS, GENERIC }

/**
 * Runtime profile of the phone the app is installed on. This is what makes the
 * advice "tuned to your phone": every OEM ships its own battery settings,
 * charge-limit feature, and app-killing behavior under different names.
 */
data class DeviceProfile(
    val oem: Oem,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
) {
    /** Marketing name of this OEM's charge-limit / battery-protection feature. */
    val chargeLimitFeature: String?
        get() = when (oem) {
            Oem.SAMSUNG -> "Battery > Battery protection (limits charge to 80%)"
            Oem.GOOGLE -> "Battery > Charging optimization > Limit to 80%"
            Oem.XIAOMI -> "Battery > Battery protection > Smart charge"
            Oem.ONEPLUS, Oem.OPPO -> "Battery > Battery health > Smart rapid charging"
            Oem.MOTOROLA -> "Battery > Optimized charging / Overcharge protection"
            Oem.SONY -> "Battery > Battery Care"
            Oem.ASUS -> "Battery > Battery care > Charging limit"
            Oem.HUAWEI -> "Battery > Smart Charge"
            Oem.NOTHING -> "Battery > Battery health"
            else -> null
        }

    /** Where this OEM hides its per-app background restriction switch. */
    val backgroundLimitPath: String
        get() = when (oem) {
            Oem.SAMSUNG -> "Settings > Battery > Background usage limits (Deep sleeping apps)"
            Oem.GOOGLE -> "Settings > Apps > [app] > Battery > Restricted"
            Oem.XIAOMI -> "Settings > Apps > Manage apps > [app] > Battery saver > Restrict"
            Oem.ONEPLUS, Oem.OPPO -> "Settings > Battery > More settings > Optimize battery use"
            Oem.HUAWEI -> "Settings > Battery > App launch"
            else -> "Settings > Apps > [app] > Battery > Restricted"
        }

    /** OEM battery dashboard, for tips that reference it. */
    val batteryDashboardName: String
        get() = when (oem) {
            Oem.SAMSUNG -> "Device care > Battery"
            Oem.XIAOMI -> "Security app > Battery"
            Oem.HUAWEI -> "Optimizer > Battery"
            else -> "Settings > Battery"
        }

    val displayName: String
        get() = "$manufacturer $model"

    companion object {
        fun detect(): DeviceProfile {
            val mfr = Build.MANUFACTURER.lowercase(Locale.US)
            val oem = when {
                mfr.contains("samsung") -> Oem.SAMSUNG
                mfr.contains("google") -> Oem.GOOGLE
                mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") -> Oem.XIAOMI
                mfr.contains("oneplus") -> Oem.ONEPLUS
                mfr.contains("oppo") || mfr.contains("realme") -> Oem.OPPO
                mfr.contains("vivo") || mfr.contains("iqoo") -> Oem.VIVO
                mfr.contains("motorola") || mfr.contains("lenovo") -> Oem.MOTOROLA
                mfr.contains("huawei") || mfr.contains("honor") -> Oem.HUAWEI
                mfr.contains("sony") -> Oem.SONY
                mfr.contains("nothing") -> Oem.NOTHING
                mfr.contains("asus") -> Oem.ASUS
                else -> Oem.GENERIC
            }
            return DeviceProfile(
                oem = oem,
                manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                model = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
            )
        }
    }
}
