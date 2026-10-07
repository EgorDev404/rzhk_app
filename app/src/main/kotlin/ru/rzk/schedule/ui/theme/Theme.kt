package ru.rzk.schedule.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ru.rzk.schedule.data.Settings
import ru.rzk.schedule.data.ThemeMode

// Запасная палитра для Android 11 и ниже (и если динамические цвета выключены).
private val LightColors = lightColorScheme(
    primary = Color(0xFF3A5BA9), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF), onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF575E71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBE2F9), onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF725572), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBD7FC), onTertiaryContainer = Color(0xFF2A122C),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF8FF), onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF8FF), onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC), onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF757780), outlineVariant = Color(0xFFC5C6D0),
    surfaceDim = Color(0xFFDAD9E0), surfaceBright = Color(0xFFFAF8FF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4), surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E2E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0C6FF), onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF204485), onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBFC6DC), onSecondary = Color(0xFF292F42),
    secondaryContainer = Color(0xFF3F4759), onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDEBCDF), onTertiary = Color(0xFF402843),
    tertiaryContainer = Color(0xFF583E5A), onTertiaryContainer = Color(0xFFFBD7FC),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318), onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF121318), onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF44474F), onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F9099), outlineVariant = Color(0xFF44474F),
    surfaceDim = Color(0xFF121318), surfaceBright = Color(0xFF38393F),
    surfaceContainerLowest = Color(0xFF0D0E13), surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF1E1F25), surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF33343A),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ScheduleTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    // Цвет иконок в строке состояния должен следовать выбранной теме, а не системной.
    val view = LocalView.current
    SideEffect {
        val window = context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
