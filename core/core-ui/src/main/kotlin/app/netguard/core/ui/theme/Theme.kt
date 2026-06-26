package app.netguard.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * NetGuard Pro design system theme.
 *
 * Brand identity:
 * - Primary: Deep Shield Blue (#1565C0) — trust, security
 * - Secondary: Cyber Teal (#00838F) — technology, precision
 * - Background: Dark Navy (#0D1117) — premium, focused
 *
 * Supports:
 * - Dynamic colors (Android 12+)
 * - Dark/Light mode
 * - Material You
 */

// Brand colors
val ShieldBlue = Color(0xFF1565C0)
val ShieldBlueDark = Color(0xFF003C8F)
val ShieldBlueLight = Color(0xFF5E92F3)
val ShieldBlueContainer = Color(0xFFD6E4FF)

val CyberTeal = Color(0xFF00838F)
val CyberTealDark = Color(0xFF006064)
val CyberTealLight = Color(0xFF4FB3BF)
val CyberTealContainer = Color(0xFFB2EBF2)

val SignalGreen = Color(0xFF00C853)
val WarningAmber = Color(0xFFFFB300)
val ErrorRed = Color(0xFFE53935)

val DarkNavy = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkSurfaceVariant = Color(0xFF21262D)
val OnDark = Color(0xFFE6EDF3)
val OnDarkSecondary = Color(0xFF8B949E)

private val NetGuardDarkColorScheme = darkColorScheme(
    primary = ShieldBlue,
    onPrimary = Color.White,
    primaryContainer = ShieldBlueDark,
    onPrimaryContainer = ShieldBlueContainer,
    secondary = CyberTeal,
    onSecondary = Color.White,
    secondaryContainer = CyberTealDark,
    onSecondaryContainer = CyberTealContainer,
    tertiary = SignalGreen,
    background = DarkNavy,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),
)

private val NetGuardLightColorScheme = lightColorScheme(
    primary = ShieldBlue,
    onPrimary = Color.White,
    primaryContainer = ShieldBlueContainer,
    onPrimaryContainer = ShieldBlueDark,
    secondary = CyberTeal,
    onSecondary = Color.White,
    secondaryContainer = CyberTealContainer,
    onSecondaryContainer = CyberTealDark,
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFF8FAFE),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8ECF4),
    onSurfaceVariant = Color(0xFF44474F),
    error = ErrorRed,
    onError = Color.White,
)

@Composable
fun NetGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> NetGuardDarkColorScheme
        else -> NetGuardLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge display
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NetGuardTypography,
        content = content
    )
}
