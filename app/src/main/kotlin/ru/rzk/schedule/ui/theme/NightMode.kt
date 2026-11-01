package ru.rzk.schedule.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import ru.rzk.schedule.data.ThemeMode

/**
 * Сообщает системе, какая тема выбрана в самом приложении.
 *
 * Заставку (сплеш) рисует система ещё до запуска приложения и красит её по системной теме. Если в приложении
 * выбрана другая (например, у телефона светлая тема, а в приложении «Тёмная»), заставка получалась бы светлой.
 * Начиная с Android 12 это решается так: приложение запоминает выбор в системе, и при следующем холодном запуске
 * заставка рисуется в нужной теме. Выбор «Системная» возвращает следование системной теме.
 */
object NightMode {
    fun apply(context: Context, mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = context.getSystemService(UiModeManager::class.java) ?: return
        manager.setApplicationNightMode(
            when (mode) {
                ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }
}
