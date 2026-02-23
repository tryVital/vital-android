@file:Suppress("unused")

package io.tryvital.vitalsamsunghealth

/**
 * List of supported apps that can be used to sync sleep data with Vital.
 */
sealed class SupportedSleepApps(val packageName: String) {
    object GoogleFit : SupportedSleepApps("com.google.android.apps.fitness")
    object SamsungHealth : SupportedSleepApps("com.sec.android.app.shealth")
    object Oura : SupportedSleepApps("com.ouraring.oura")
    object Withings : SupportedSleepApps("com.withings.wiscale2")
    object Whoop : SupportedSleepApps("com.whoop.android")
    object Garmin : SupportedSleepApps("com.garmin.android.apps.connectmobile")
    object Fitbit : SupportedSleepApps("com.fitbit.FitbitMobile")
    object Polar : SupportedSleepApps("fi.polar.polarflow")
    object Coros : SupportedSleepApps("com.yf.smart.coros.dist")
    object Suunto : SupportedSleepApps("com.stt.android")
    object Suunto2 : SupportedSleepApps("com.stt.android.suunto")
    object Xiaomi : SupportedSleepApps("com.xiaomi.wearable")
    object Muse : SupportedSleepApps("com.interaxon.muse")

    companion object {
        fun values(): Array<SupportedSleepApps> {
            return arrayOf(
                GoogleFit,
                SamsungHealth,
                Oura,
                Withings,
                Whoop,
                Garmin,
                Fitbit,
                Polar,
                Coros,
                Suunto,
                Suunto2,
                Xiaomi,
                Muse
            )
        }

        fun valueOf(value: String): SupportedSleepApps {
            return when (value) {
                "GoogleFit" -> GoogleFit
                "SamsungHealth" -> SamsungHealth
                "Oura" -> Oura
                "Withings" -> Withings
                "Whoop" -> Whoop
                "Garmin" -> Garmin
                "Fitbit" -> Fitbit
                "Polar" -> Polar
                "Coros" -> Coros
                "Suunto" -> Suunto
                "Suunto2" -> Suunto2
                "Xiaomi" -> Xiaomi
                "Muse" -> Muse
                else -> throw IllegalArgumentException("No object io.tryvital.vitalsamsunghealth.SupportedSleepApps.$value")
            }
        }
    }
}