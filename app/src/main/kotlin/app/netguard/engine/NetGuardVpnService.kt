package app.netguard.engine

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * NetGuardVpnService — Android VPN Service shell.
 *
 * Runs in separate process (:vpn_engine) for crash isolation.
 * Delegates all packet processing to engine-vpn module.
 */
@AndroidEntryPoint
class NetGuardVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("NetGuardVpnService created (pid=${android.os.Process.myPid()})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_CONNECT    -> { startVpn(); START_STICKY }
            ACTION_DISCONNECT -> { stopVpn();  START_NOT_STICKY }
            else              -> START_STICKY
        }
    }

    private fun startVpn() {
        Timber.i("Starting VPN engine")
        try {
            val builder = Builder()
                .setSession("NetGuard Pro")
                .addAddress("10.0.0.1", 24)
                .addAddress("fd00::1", 120)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("10.0.0.2")
                .setMtu(1500)
                .setBlocking(false)
                .allowBypass()

            vpnInterface = builder.establish()
            Timber.i("TUN interface established fd=${vpnInterface?.fileDescriptor}")
            // TODO Phase 1: vpnEngine.start(vpnInterface!!)
        } catch (e: Exception) {
            Timber.e(e, "Failed to establish VPN interface")
            stopSelf()
        }
    }

    private fun stopVpn() {
        Timber.i("Stopping VPN")
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Timber.e(e, "Error stopping VPN")
        } finally {
            stopSelf()
        }
    }

    override fun onDestroy() { stopVpn(); super.onDestroy() }
    override fun onRevoke()  { Timber.w("VPN revoked"); stopVpn(); super.onRevoke() }

    companion object {
        const val ACTION_CONNECT    = "app.netguard.pro.CONNECT"
        const val ACTION_DISCONNECT = "app.netguard.pro.DISCONNECT"
    }
}
