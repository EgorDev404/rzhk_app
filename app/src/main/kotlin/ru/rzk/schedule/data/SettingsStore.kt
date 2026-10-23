package ru.rzk.schedule.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.rzk.schedule.widget.BellsWidgetUpdater

enum class ThemeMode { System, Light, Dark }

data class Settings(
    val notifications: Boolean = true,
    val intervalMinutes: Int = 15,
    val dynamicColor: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
    /** false — таблица звонков из 8 уроков (по умолчанию), true — все 12. */
    val bellsFull: Boolean = false,
)

/** Настройки в SharedPreferences + поток для Compose. */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Settings> = _flow.asStateFlow()

    val current: Settings get() = _flow.value

    fun update(transform: (Settings) -> Settings) {
        val previous = _flow.value
        val next = transform(previous)
        prefs.edit()
            .putBoolean("notifications", next.notifications)
            .putInt("interval", next.intervalMinutes)
            .putBoolean("dynamic", next.dynamicColor)
            .putString("theme", next.theme.name)
            .putBoolean("bells_full", next.bellsFull)
            .apply()
        _flow.value = next
        // Виджет считает звонки по тому же режиму (8/12 уроков) — перерисовываем сразу, а не через минуту.
        if (next.bellsFull != previous.bellsFull) BellsWidgetUpdater.refreshAll(appContext)
    }

    private fun read() = Settings(
        notifications = prefs.getBoolean("notifications", true),
        intervalMinutes = prefs.getInt("interval", 15),
        dynamicColor = prefs.getBoolean("dynamic", true),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "System") }
            .getOrDefault(ThemeMode.System),
        bellsFull = prefs.getBoolean("bells_full", false),
    )
}
