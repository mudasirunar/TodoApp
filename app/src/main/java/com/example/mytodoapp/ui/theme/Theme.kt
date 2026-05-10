package com.example.mytodoapp.ui.theme

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

// 1. VIBRANT COLOR DEFINITIONS
val DeepNavy = Color(0xFF0F172A)      // Premium Dark Background
val GlassySlate = Color(0xFF1E293B)   // Card Surface
val ElectricBlue = Color(0xFF3B82F6)  // Secondary Actions
val VividIndigo = Color(0xFF6366F1)   // Main Primary Brand Color
val GlowingRed = Color(0xFFFF453A)    // Vibrant Error/Delete
val GlassyWhite = Color(0xFFF8FAFC)   // Clean Light Background

// 2. UPDATED SCHEMES (Using your new vibrant colors)
private val DarkVibrantScheme = darkColorScheme(
    primary = VividIndigo,
    onPrimary = Color.White,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    background = DeepNavy,
    onBackground = Color.White,
    surface = GlassySlate,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = GlowingRed,
    outline = Color(0xFF475569) // For those "glossy" borders
)

private val LightVibrantScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    background = GlassyWhite,
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    error = Color(0xFFDC2626)
)

@Composable
fun MyTodoAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // SET TO FALSE to prevent wallpaper colors from ruining your custom UI
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // FIXED: Now correctly pointing to your Vibrant Schemes
        darkTheme -> DarkVibrantScheme
        else -> LightVibrantScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar color is handled by edge-to-edge, we just need to fix icon visibility
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Ensure your Typography.kt is set up
        content = content
    )
}