package ru.rzk.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import ru.rzk.schedule.data.BellSchedule

/**
 * Обновление экземпляров виджета. Для каждого экземпляра читаем его актуальные размеры
 * (`getAppWidgetOptions`), чтобы раскладка и размеры шрифтов соответствовали реальному размеру на экране.
 * После каждого обновления ставим следующий будильник ([WidgetAlarm]) — так виджет «тикает» сам.
 */
object BellsWidgetUpdater {

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, BellsWidget::class.java))
        if (ids.isEmpty()) {
            WidgetAlarm.cancel(context)
            return
        }
        refresh(context, manager, ids)
    }

    fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, BellsWidget.buildViews(context, manager.getAppWidgetOptions(id)))
        }
        WidgetAlarm.schedule(context)
    }

    /** Сколько уроков в дне: 8 или 12 — то же переключатель, что на экране «Звонки» (SettingsStore, ключ bells_full). */
    fun lessonCount(context: Context): Int {
        val full = context.applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("bells_full", false)
        return if (full) BellSchedule.FULL_DAY else BellSchedule.SHORT_DAY
    }
}
