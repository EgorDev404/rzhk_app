package ru.rzk.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

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
}
