package ru.rzk.schedule.ui.theme

import android.content.Context
import android.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb

object WidgetColors {
    private const val PREFS = "widget_colors"

    private const val K_PRIMARY_CONTAINER = "primary_container"
    private const val K_ON_PRIMARY_CONTAINER = "on_primary_container"
    private const val K_TERTIARY_CONTAINER = "tertiary_container"
    private const val K_ON_TERTIARY_CONTAINER = "on_tertiary_container"
    private const val K_SECONDARY_CONTAINER = "secondary_container"
    private const val K_ON_SECONDARY_CONTAINER = "on_secondary_container"
    private const val K_SURFACE_CONTAINER_HIGH = "surface_container_high"
    private const val K_ON_SURFACE = "on_surface"
    private const val K_ON_SURFACE_VARIANT = "on_surface_variant"

    data class Snapshot(
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val tertiaryContainer: Int,
        val onTertiaryContainer: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
        val surfaceContainerHigh: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
    )

    fun save(context: Context, scheme: ColorScheme) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(K_PRIMARY_CONTAINER, scheme.primaryContainer.toArgb())
            .putInt(K_ON_PRIMARY_CONTAINER, scheme.onPrimaryContainer.toArgb())
            .putInt(K_TERTIARY_CONTAINER, scheme.tertiaryContainer.toArgb())
            .putInt(K_ON_TERTIARY_CONTAINER, scheme.onTertiaryContainer.toArgb())
            .putInt(K_SECONDARY_CONTAINER, scheme.secondaryContainer.toArgb())
            .putInt(K_ON_SECONDARY_CONTAINER, scheme.onSecondaryContainer.toArgb())
            .putInt(K_SURFACE_CONTAINER_HIGH, scheme.surfaceContainerHigh.toArgb())
            .putInt(K_ON_SURFACE, scheme.onSurface.toArgb())
            .putInt(K_ON_SURFACE_VARIANT, scheme.onSurfaceVariant.toArgb())
            .apply()
    }

    fun load(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            primaryContainer = prefs.getInt(K_PRIMARY_CONTAINER, DEFAULT_PRIMARY_CONTAINER),
            onPrimaryContainer = prefs.getInt(K_ON_PRIMARY_CONTAINER, DEFAULT_ON_PRIMARY_CONTAINER),
            tertiaryContainer = prefs.getInt(K_TERTIARY_CONTAINER, DEFAULT_TERTIARY_CONTAINER),
            onTertiaryContainer = prefs.getInt(K_ON_TERTIARY_CONTAINER, DEFAULT_ON_TERTIARY_CONTAINER),
            secondaryContainer = prefs.getInt(K_SECONDARY_CONTAINER, DEFAULT_SECONDARY_CONTAINER),
            onSecondaryContainer = prefs.getInt(K_ON_SECONDARY_CONTAINER, DEFAULT_ON_SECONDARY_CONTAINER),
            surfaceContainerHigh = prefs.getInt(K_SURFACE_CONTAINER_HIGH, DEFAULT_SURFACE),
            onSurface = prefs.getInt(K_ON_SURFACE, DEFAULT_ON_SURFACE),
            onSurfaceVariant = prefs.getInt(K_ON_SURFACE_VARIANT, DEFAULT_ON_SURFACE_VARIANT),
        )
    }

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private val DEFAULT_SURFACE = 0xFF1E1F25.toInt()
    private val DEFAULT_PRIMARY_CONTAINER = 0xFF204485.toInt()
    private val DEFAULT_ON_PRIMARY_CONTAINER = 0xFFD9E2FF.toInt()
    private val DEFAULT_TERTIARY_CONTAINER = 0xFF583E5A.toInt()
    private val DEFAULT_ON_TERTIARY_CONTAINER = 0xFFFBD7FC.toInt()
    private val DEFAULT_SECONDARY_CONTAINER = 0xFF3F4759.toInt()
    private val DEFAULT_ON_SECONDARY_CONTAINER = 0xFFDBE2F9.toInt()
    private val DEFAULT_ON_SURFACE = 0xFFE3E2E9.toInt()
    private val DEFAULT_ON_SURFACE_VARIANT = 0xFFC5C6D0.toInt()
}
