package app.netguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MainViewModel — coordinates app-level state.
 *
 * Handles:
 * - App initialization state (splash screen)
 * - VPN permission request/response lifecycle
 * - Navigation events
 *
 * Does NOT handle business logic — delegates to use cases.
 */
@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _vpnPermissionRequest = MutableSharedFlow<Unit>()
    val vpnPermissionRequest = _vpnPermissionRequest.asSharedFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            try {
                // Simulate initialization — will be replaced with actual startup logic
                // (database migrations, preferences loading, etc.)
                _isLoading.value = false
            } catch (e: Exception) {
                Timber.e(e, "Initialization failed")
                _isLoading.value = false
                _uiState.value = _uiState.value.copy(
                    error = "Initialization failed: ${e.message}"
                )
            }
        }
    }

    fun requestVpnPermission() {
        viewModelScope.launch {
            _vpnPermissionRequest.emit(Unit)
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        Timber.i("VPN permission: granted=$granted")
        _uiState.value = _uiState.value.copy(
            vpnPermissionGranted = granted,
            error = if (!granted) "VPN permission denied" else null
        )
    }
}

data class MainUiState(
    val vpnPermissionGranted: Boolean = false,
    val isVpnConnected: Boolean = false,
    val error: String? = null
)
