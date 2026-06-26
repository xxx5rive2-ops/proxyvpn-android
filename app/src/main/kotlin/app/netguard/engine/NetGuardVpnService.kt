package app.netguard.engine

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * NetGuardVpnService — Android VPN Service entry point.
 *
 * This class is the "shell" that Android OS interacts with.
 * All actual packet processing logic is delegated to the engine-vpn module.
 *
 * Design decisions:
 * - Runs in separate process (:vpn_engine) for crash isolation
 * - Uses foreground service for reliable execution
 * - Delegates ALL logic to VpnEngine (in engine-vpn module)
 * - This class only handles Android lifecycle and IPC
 *
 * Process isolation means this crashes independently of the UI process.
 * The UI can detect the crash and offer reconnection.
 */
@AndroidEntryPoint
class NetGuardVpnService : VpnService() {

    // Injected by Hilt — actual engine implementation from engine-vpn module
    // @Inject lateinit var vpnEngine: VpnEngine  // TODO: Uncomment when engine module ready

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("NetGuardVpnService created in process: ${android.os.Process.myPid()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("NetGuardVpnService onStartCommand: action=${intent?.action}")
        return when (intent?.action) {
            ACTION_CONNECT -> {
                startVpn()
                START_STICKY
            }
            ACTION_DISCONNECT -> {
                stopVpn()
                START_NOT_STICKY
            }
            else -> START_STICKY
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
                .addDnsServer("fd00::2")
                .setMtu(1500)
                .setBlocking(false)
                .allowBypass()

            vpnInterface = builder.establish()
            Timber.i("VPN interface established: ${vpnInterface?.fileDescriptor}")

            // TODO: Start engine-vpn packet processing pipeline
            // vpnEngine.start(vpnInterface!!)

        } catch (e: Exception) {
            Timber.e(e, "Failed to start VPN")
            stopSelf()
        }
    }

    private fun stopVpn() {
        Timber.i("Stopping VPN engine")
        try {
            // TODO: vpnEngine.stop()
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Timber.e(e, "Error stopping VPN")
        } finally {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Timber.i("NetGuardVpnService destroyed")
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        Timber.w("VPN permission revoked by system")
        stopVpn()
        super.onRevoke()
    }

    companion object {
        const val ACTION_CONNECT = "app.netguard.pro.action.CONNECT"
        const val ACTION_DISCONNECT = "app.netguard.pro.action.DISCONNECT"
    }
}
