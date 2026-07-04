package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MonochromeColorScheme = lightColorScheme(
    primary = Color(0xFF000000),         // Pure Black buttons and primary interactive elements
    onPrimary = Color(0xFFFFFFFF),       // White text/icons inside pure black buttons
    secondary = Color(0xFF86868B),       // Apple dynamic secondary gray
    onSecondary = Color(0xFFFFFFFF),     // White on secondary
    background = Color(0xFFFFFFFF),      // Pure white main background
    onBackground = Color(0xFF1D1D1F),    // Apple rich charcoal black body text
    surface = Color(0xFFFFFFFF),         // Pure white surface containers
    onSurface = Color(0xFF1D1D1F),        // Charcoal black text on white surfaces
    surfaceVariant = Color(0xFFF5F5F7),  // Apple-style light gray card background
    onSurfaceVariant = Color(0xFF1D1D1F),// Dark gray/black text on light cards
    outline = Color(0xFFE5E5E7),         // Subtly high-contrast border gray
    outlineVariant = Color(0xFFD2D2D7)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Force false by default to ensure pure white aesthetic
    dynamicColor: Boolean = false, // Disable dynamic system colors to preserve monochrome branding
    content: @Composable () -> Unit,
) {
    // Always use the beautiful MonochromeColorScheme
    MaterialTheme(
        colorScheme = MonochromeColorScheme,
        typography = Typography,
        content = content
    )
}
