package app.netguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.StrictMode
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import app.netguard.pro.R
import app.netguard.pro.BuildConfig

/**
 * NetGuard Pro — Application class.
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
            Timber.plant(ReleaseCrashTree())
        }
        Timber.i("NetGuard Pro ${BuildConfig.VERSION_NAME} starting")
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
            )

            manager.createNotificationChannels(listOf(vpnChannel, alertChannel))
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }

    companion object {
        const val CHANNEL_VPN_SERVICE = "netguard_vpn_service"
        const val CHANNEL_ALERTS = "netguard_alerts"
    }
}

private class ReleaseCrashTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) { }
}
