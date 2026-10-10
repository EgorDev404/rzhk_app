package ru.rzk.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Обновление всех экземпляров виджета. Периодичность обеспечивается системным broadcast
 * `ACTION_TIME_TICK`, который приходит каждую минуту — никакого AlarmManager не нужно.
 *
 * Для каждого экземпляра читаем его текущий размер, чтобы не показывать лишние элементы.
 */
object BellsWidgetUpdater {

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, BellsWidget::class.java))
        if (ids.isEmpty()) return
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val size = BellsWidget.SizeClass.from(options)
            manager.updateAppWidget(id, BellsWidget.buildViews(context, size))
        }
    }
}