package ru.rzk.schedule.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.rzk.schedule.widget.BellsWidgetUpdater

enum class ThemeMode { System, Light, Dark }

/** Цвет приложения по умолчанию (синий): для него используется фирменная палитра. */
val DEFAULT_SEED_COLOR: Int = 0xFF3A5BA9.toInt()

data class Settings(
    val notifications: Boolean = true,
    val dynamicColor: Boolean = true,
    /** Основной цвет приложения, когда «Цвета из обоев» выключены (ARGB). */
    val seedColor: Int = DEFAULT_SEED_COLOR,
    val theme: ThemeMode = ThemeMode.System,
    /** false — таблица звонков из 8 уроков (по умолчанию), true — все 12. */
    val bellsFull: Boolean = false,
    /** Скрытый режим разработчика: на этот телефон приходят жалобы пользователей на красную лампочку. */
    val devMode: Boolean = false,
    /** Зимнее оформление (снег) разрешено; показывается в сезон (1–19 декабря) или при включении разработчиком. */
    val winter: Boolean = true,
    /** Режим разработчика: показывать зимнее оформление независимо от даты. */
    val devWinter: Boolean = false,
    /** Новогоднее оформление (снег, ёлка, гирлянда, трости, счётчик) разрешено; показывается с 20 декабря по 9 января. */
    val newYear: Boolean = true,
    /** Режим разработчика: показывать новогоднее оформление независимо от даты. */
    val devNewYear: Boolean = false,
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
            .putInt("seed_color", next.seedColor)
            .putString("theme", next.theme.name)
            .putBoolean("bells_full", next.bellsFull)
            .putBoolean("dev_mode", next.devMode)
            .putBoolean("winter", next.winter)
            .putBoolean("dev_winter", next.devWinter)
            .putBoolean("new_year", next.newYear)
            .putBoolean("dev_new_year", next.devNewYear)
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

    private fun read(): Settings {
        // При наступлении нового зимнего (1–19 декабря) или новогоднего (20 декабря – 9 января) сезона
        // оформление автоматически включается (если раньше его отключали вручную), но внутри текущего сезона
        // ручное отключение сохраняется.
        val today = Dates.today()
        val currentSeason = when {
            (today.monthValue == 12 && today.dayOfMonth >= 20) || (today.monthValue == 1 && today.dayOfMonth <= 9) ->
                "ny_${if (today.monthValue == 12) today.year else today.year - 1}"
            today.monthValue == 12 && today.dayOfMonth in 1..19 ->
                "w_${today.year}"
            else -> ""
        }
        val savedSeason = prefs.getString("auto_season_id", "") ?: ""
        if (currentSeason.isNotEmpty() && currentSeason != savedSeason) {
            val editor = prefs.edit().putString("auto_season_id", currentSeason)
            if (currentSeason.startsWith("ny_")) editor.putBoolean("new_year", true)
            if (currentSeason.startsWith("w_")) editor.putBoolean("winter", true)
            editor.apply()
        } else if (currentSeason.isEmpty() && savedSeason.isNotEmpty()) {
            prefs.edit().putString("auto_season_id", "").apply()
        }

        val devNewYear = prefs.getBoolean("dev_new_year", false)
        val devWinter = if (devNewYear) false else prefs.getBoolean("dev_winter", false)

        return Settings(
            notifications = prefs.getBoolean("notifications", true),
            dynamicColor = prefs.getBoolean("dynamic", true),
            seedColor = prefs.getInt("seed_color", DEFAULT_SEED_COLOR),
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "System") }
                .getOrDefault(ThemeMode.System),
            bellsFull = prefs.getBoolean("bells_full", false),
            devMode = prefs.getBoolean("dev_mode", false),
            winter = prefs.getBoolean("winter", true),
            devWinter = devWinter,
            newYear = prefs.getBoolean("new_year", true),
            devNewYear = devNewYear,
        )
    }
}
