package app.netguard.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

/**
 * NetGuard Pro Navigation Host.
 *
 * Defines the app's navigation graph using type-safe navigation.
 * Each feature module contributes its own navigation graph via extension functions.
 */
@Composable
fun NetGuardNavHost(
    uiState: MainUiState,
    onRequestVpnPermission: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        // Feature navigation graphs are registered here
        // Each feature contributes via NavGraphBuilder extension functions
        // dashboardNavGraph(navController, onRequestVpnPermission)
        // rulesNavGraph(navController)
        // serversNavGraph(navController)
        // trafficNavGraph(navController)
        // settingsNavGraph(navController)
        // diagnosticsNavGraph(navController)
        //
        // TODO: Uncomment as feature modules are implemented
    }
}
