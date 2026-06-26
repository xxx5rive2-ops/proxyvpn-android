package app.netguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.StrictMode
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import app.netguard.pro.BuildConfig

/**
 * NetGuard Pro — Application class.
 *
 * Responsibilities:
 * - Dependency injection initialization (Hilt)
 * - Logging setup (Timber)
 * - Notification channels registration
 * - StrictMode configuration for debug builds
 *
 * This class intentionally does minimal work — heavy initialization
 * is deferred to feature modules and startup initializers.
 */
@HiltAndroidApp
class NetGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeLogging()
        registerNotificationChannels()
        if (BuildConfig.ENABLE_STRICT_MODE) {
            enableStrictMode()
        }
    }

    private fun initializeLogging() {
        if (BuildConfig.ENABLE_LOGGING) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In release builds: plant a crash-reporting tree
            // (no-op until crash reporting is configured)
            Timber.plant(ReleaseCrashTree())
        }
        Timber.i("NetGuard Pro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) starting")
    }

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService<NotificationManager>() ?: return

            val vpnChannel = NotificationChannel(
                CHANNEL_VPN_SERVICE,
                getString(R.string.notification_channel_vpn),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_vpn_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Important alerts from NetGuard Pro"
                setShowBadge(true)
            }

            manager.createNotificationChannels(listOf(vpnChannel, alertChannel))
            Timber.d("Notification channels registered")
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
        Timber.d("StrictMode enabled")
    }

    companion object {
        const val CHANNEL_VPN_SERVICE = "netguard_vpn_service"
        const val CHANNEL_ALERTS = "netguard_alerts"
    }
}

/**
 * Timber tree for release builds.
 * Strips debug/verbose logs, keeps errors for potential crash reporting.
 */
private class ReleaseCrashTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // In production: send to crash reporter (e.g., Firebase Crashlytics)
        // Currently: silently discard all logs for privacy
        // TODO: Integrate crash reporter with user consent
    }
}
