package app.netguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * BootReceiver — handles device boot and app update events.
 *
 * Starts the VPN service automatically if "Auto Connect" is enabled in settings.
 * Uses WorkManager to schedule the startup work, avoiding ANR risks.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Timber.i("Boot/update received: ${intent.action}")
                // TODO: Check auto-connect preference and start VPN via WorkManager
                // AutoConnectWorker.enqueue(context)
            }
        }
    }
}
