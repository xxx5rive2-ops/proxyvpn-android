package app.netguard.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import app.netguard.core.ui.theme.NetGuardTheme

/**
 * MainActivity — Single Activity architecture entry point.
 *
 * Responsibilities:
 * - Splash screen installation
 * - VPN permission request lifecycle
 * - Navigation host setup
 * - System UI configuration
 *
 * All actual UI is delegated to Compose navigation graph.
 * No business logic lives here — only Android lifecycle coordination.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * VPN permission launcher — system shows its own dialog.
     * Result is true if user granted permission, false if denied.
     */
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK
        Timber.i("VPN permission result: granted=$granted")
        viewModel.onVpnPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Keep splash visible until ViewModel is ready
        splashScreen.setKeepOnScreenCondition {
            viewModel.isLoading.value
        }

        // Observe VPN permission requests from ViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vpnPermissionRequest.collect {
                    requestVpnPermission()
                }
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            NetGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NetGuardNavHost(
                        uiState = uiState,
                        onRequestVpnPermission = { viewModel.requestVpnPermission() }
                    )
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Permission not yet granted — launch system dialog
            Timber.d("Requesting VPN permission via system dialog")
            vpnPermissionLauncher.launch(intent)
        } else {
            // Permission already granted
            Timber.d("VPN permission already granted")
            viewModel.onVpnPermissionResult(granted = true)
        }
    }
}
