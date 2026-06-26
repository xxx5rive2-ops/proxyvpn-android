package app.netguard.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * Quick Settings Tile for NetGuard Pro.
 *
 * Allows users to toggle VPN connection directly from the Quick Settings panel.
 * Updates tile state based on current VPN connection status.
 */
@RequiresApi(Build.VERSION_CODES.N)
class NetGuardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        Timber.d("Tile: onStartListening")
        updateTileState(isConnected = false) // TODO: Query actual state
    }

    override fun onStopListening() {
        super.onStopListening()
        Timber.d("Tile: onStopListening")
    }

    override fun onClick() {
        super.onClick()
        Timber.d("Tile: onClick")
        // TODO: Toggle VPN via VpnController
    }

    private fun updateTileState(isConnected: Boolean) {
        qsTile?.apply {
            state = if (isConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (isConnected) "NetGuard: On" else "NetGuard: Off"
            contentDescription = if (isConnected) {
                "NetGuard Pro VPN is active. Tap to disconnect."
            } else {
                "NetGuard Pro VPN is inactive. Tap to connect."
            }
            updateTile()
        }
    }
}
