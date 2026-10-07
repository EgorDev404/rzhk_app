package ru.rzk.schedule.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

data class Settings(
    val notifications: Boolean = false,
    val intervalMinutes: Int = 15,
    val dynamicColor: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
)

/** Настройки в SharedPreferences + поток для Compose. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Settings> = _flow.asStateFlow()

    val current: Settings get() = _flow.value

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_flow.value)
        prefs.edit()
            .putBoolean("notifications", next.notifications)
            .putInt("interval", next.intervalMinutes)
            .putBoolean("dynamic", next.dynamicColor)
            .putString("theme", next.theme.name)
            .apply()
        _flow.value = next
    }

    private fun read() = Settings(
        notifications = prefs.getBoolean("notifications", false),
        intervalMinutes = prefs.getInt("interval", 15),
        dynamicColor = prefs.getBoolean("dynamic", true),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "System") }
            .getOrDefault(ThemeMode.System),
    )
}
