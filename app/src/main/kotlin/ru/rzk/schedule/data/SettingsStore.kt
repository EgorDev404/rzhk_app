package ru.rzk.schedule.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.rzk.schedule.widget.BellsWidgetUpdater

enum class ThemeMode { System, Light, Dark }

data class Settings(
    val notifications: Boolean = true,
    val dynamicColor: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
    /** false — таблица звонков из 8 уроков (по умолчанию), true — все 12. */
    val bellsFull: Boolean = false,
    /** Скрытый режим разработчика: на этот телефон приходят жалобы пользователей на красную лампочку. */
    val devMode: Boolean = false,
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
            .putBoolean("dynamic", next.dynamicColor)
            .putString("theme", next.theme.name)
            .putBoolean("bells_full", next.bellsFull)
            .putBoolean("dev_mode", next.devMode)
            .apply()
        _flow.value = next
        // Виджет считает звонки по тому же режиму (8/12 уроков) — перерисовываем сразу, а не через минуту.
        if (next.bellsFull != previous.bellsFull) BellsWidgetUpdater.refreshAll(appContext)
    }

    /** Случайный номер установки: нужен, чтобы жалоба от одного телефона за день учитывалась один раз. */
    fun installId(): String =
        prefs.getString("install_id", null) ?: java.util.UUID.randomUUID().toString().replace("-", "")
            .also { prefs.edit().putString("install_id", it).apply() }

    /** Дата (по Москве), когда пользователь последний раз отправил жалобу. */
    fun lastReportDay(): String? = prefs.getString("report_day", null)

    fun markReported(day: String) {
        prefs.edit().putString("report_day", day).apply()
    }

    /** Сколько жалоб за [day] разработчик уже видел (чтобы не уведомлять о них повторно). */
    fun devSeen(day: String): Int = if (prefs.getString("dev_seen_day", null) == day) prefs.getInt("dev_seen_count", 0) else 0

    fun setDevSeen(day: String, count: Int) {
        prefs.edit().putString("dev_seen_day", day).putInt("dev_seen_count", count).apply()
    }

    /** Приветственная плашка уже показывалась? */
    fun introShown(): Boolean = prefs.getBoolean("intro_shown", false)

    fun markIntroShown() {
        prefs.edit().putBoolean("intro_shown", true).apply()
    }

    private fun read() = Settings(
        notifications = prefs.getBoolean("notifications", true),
        dynamicColor = prefs.getBoolean("dynamic", true),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "System") }
            .getOrDefault(ThemeMode.System),
        bellsFull = prefs.getBoolean("bells_full", false),
        devMode = prefs.getBoolean("dev_mode", false),
    )
}
