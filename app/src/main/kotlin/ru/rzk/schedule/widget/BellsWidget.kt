package ru.rzk.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.RemoteViews
import ru.rzk.schedule.MainActivity
import ru.rzk.schedule.R
import ru.rzk.schedule.data.BellSchedule
import ru.rzk.schedule.data.BellState
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.ui.theme.WidgetColors
import java.time.LocalDateTime

/**
 * Виджет с текущим состоянием звонков. Копия карточки «Сейчас» с экрана «Звонки».
 *
 * Цвета берутся из [WidgetColors] — они сохраняются при смене темы в приложении.
 * При смене темы приложение вызывает [BellsWidgetUpdater.refreshAll], и виджет
 * немедленно перерисовывается в новых цветах.
 */
class BellsWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED,
            -> BellsWidgetUpdater.refreshAll(context)
        }
    }

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {
        

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, LAYOUT)
            val now = LocalDateTime.now(Dates.zone)
            val state = BellSchedule.state(now, BellSchedule.SHORT_DAY)

            // Тап по любой части виджета — открыть приложение на вкладке «Звонки».
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_BELLS)
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            applyState(views, state)
            applyThemeColors(context, views, state)
            return views
        }

        /** Заполняет тексты и видимость блоков в соответствии с [state]. */
        private fun applyState(views: RemoteViews, state: BellState) {
            val chipText: String
            val bigText: String
            val bigLabel: String
            val statsLeftLabel: String
            val statsLeftValue: String
            val statsRightLabel: String
            val statsRightValue: String
            val footer: String
            val showStats: Boolean
            val showRing: Boolean

            when (state) {
                is BellState.InLesson -> {
                    chipText = "Идёт ${state.bell.number} урок"
                    bigText = BellSchedule.countdown(state.remaining)
                    bigLabel = "до конца урока"
                    statsLeftLabel = "Начался"
                    statsLeftValue = BellSchedule.hm(state.bell.start)
                    statsRightLabel = "Закончится"
                    statsRightValue = BellSchedule.hm(state.bell.end)
                    footer = state.next?.let {
                        "Дальше: перемена ${BellSchedule.minutes(state.breakAfter!!)}, затем ${it.number} урок в ${BellSchedule.hm(it.start)}"
                    } ?: "Это последний урок на сегодня"
                    showStats = true
                    showRing = true
                }
                is BellState.InBreak -> {
                    chipText = "Перемена"
                    bigText = BellSchedule.countdown(state.remaining)
                    bigLabel = "до начала ${state.next.number} урока"
                    statsLeftLabel = "Урок ${state.after.number} закончился"
                    statsLeftValue = BellSchedule.hm(state.after.end)
                    statsRightLabel = "Урок ${state.next.number} начнётся"
                    statsRightValue = BellSchedule.hm(state.next.start)
                    footer = "Перемена длится ${BellSchedule.minutes(state.total)}"
                    showStats = true
                    showRing = true
                }
                is BellState.BeforeClasses -> {
                    chipText = "Занятия ещё не начались"
                    bigText = BellSchedule.countdown(state.remaining)
                    bigLabel = "до начала ${state.first.number} урока"
                    statsLeftLabel = "Начало занятий"
                    statsLeftValue = BellSchedule.hm(state.first.start)
                    statsRightLabel = ""
                    statsRightValue = ""
                    footer = "${state.first.number} урок: ${BellSchedule.range(state.first)}"
                    showStats = true
                    showRing = false
                }
                is BellState.Finished -> {
                    chipText = "Занятия закончились"
                    bigText = "На сегодня всё"
                    bigLabel = "Последний урок закончился в ${BellSchedule.hm(state.last.end)}"
                    statsLeftLabel = ""
                    statsLeftValue = ""
                    statsRightLabel = ""
                    statsRightValue = ""
                    footer = (if (state.tomorrowIsMonday) "В понедельник" else "Завтра") +
                        " занятия начнутся в ${BellSchedule.hm(state.firstTomorrow)}"
                    showStats = false
                    showRing = false
                }
                is BellState.DayOff -> {
                    chipText = "Воскресенье"
                    bigText = "Выходной"
                    bigLabel = "Звонков сегодня нет"
                    statsLeftLabel = ""
                    statsLeftValue = ""
                    statsRightLabel = ""
                    statsRightValue = ""
                    footer = "В понедельник занятия начнутся в ${BellSchedule.hm(state.nextStart)}"
                    showStats = false
                    showRing = false
                }
            }

            views.setTextViewText(R.id.widget_chip, chipText)
            views.setTextViewText(R.id.widget_big, bigText)
            views.setTextViewText(R.id.widget_big_label, bigLabel)
            views.setTextViewText(R.id.widget_footer, footer)
            views.setViewVisibility(R.id.widget_stats, if (showStats) View.VISIBLE else View.GONE)
            if (showStats) {
                views.setTextViewText(R.id.widget_stats_left_label, statsLeftLabel)
                views.setTextViewText(R.id.widget_stats_left_value, statsLeftValue)
                views.setTextViewText(R.id.widget_stats_right_label, statsRightLabel)
                views.setTextViewText(R.id.widget_stats_right_value, statsRightValue)
                views.setViewVisibility(R.id.widget_stats_right, if (statsRightValue.isEmpty()) View.GONE else View.VISIBLE)
            }
            views.setViewVisibility(R.id.widget_ring, if (showRing) View.VISIBLE else View.INVISIBLE)
            if (showRing && state is BellState.InLesson) {
                views.setTextViewText(R.id.widget_ring_percent, "${(state.progress * 100).toInt()}%")
            } else if (showRing && state is BellState.InBreak) {
                views.setTextViewText(R.id.widget_ring_percent, "${(state.progress * 100).toInt()}%")
            }
        }

        /** Применяет цвета из текущей темы приложения: фон, текст, иконки, чипы. */
        private fun applyThemeColors(context: Context, views: RemoteViews, state: BellState) {
            val c = WidgetColors.load(context)

            // Пара (фон, контент) в зависимости от состояния — как в Compose-версии NowCard.
            val (container, onContainer) = when (state) {
                is BellState.InLesson -> c.primaryContainer to c.onPrimaryContainer
                is BellState.InBreak -> c.tertiaryContainer to c.onTertiaryContainer
                is BellState.BeforeClasses -> c.secondaryContainer to c.onSecondaryContainer
                else -> c.surfaceContainerHigh to c.onSurface
            }

            // Фон карточки. Используем setColorStateList на backgroundTintList, чтобы
            // не затирать shape-drawable (setBackgroundColor ломает скругление на API < 31).
            views.setColorStateList(
                R.id.widget_root,
                "setBackgroundTintList",
                ColorStateList.valueOf(container),
            )

            // Основной текст — на контейнере.
            views.setTextColor(R.id.widget_chip, onContainer)
            views.setTextColor(R.id.widget_big, onContainer)
            views.setTextColor(R.id.widget_ring_percent, onContainer)

            // Второстепенный текст — с прозрачностью.
            views.setTextColor(R.id.widget_big_label, WidgetColors.withAlpha(onContainer, 0.8f))
            views.setTextColor(R.id.widget_footer, WidgetColors.withAlpha(onContainer, 0.85f))

            // Чип статуса.
            views.setColorStateList(
                R.id.widget_chip_container,
                "setBackgroundTintList",
                ColorStateList.valueOf(WidgetColors.withAlpha(onContainer, 0.12f)),
            )

            // «Таблетки» со временами.
            views.setColorStateList(
                R.id.widget_stats_left,
                "setBackgroundTintList",
                ColorStateList.valueOf(WidgetColors.withAlpha(onContainer, 0.10f)),
            )
            views.setColorStateList(
                R.id.widget_stats_right,
                "setBackgroundTintList",
                ColorStateList.valueOf(WidgetColors.withAlpha(onContainer, 0.10f)),
            )
            views.setTextColor(R.id.widget_stats_left_label, WidgetColors.withAlpha(onContainer, 0.75f))
            views.setTextColor(R.id.widget_stats_left_value, onContainer)
            views.setTextColor(R.id.widget_stats_right_label, WidgetColors.withAlpha(onContainer, 0.75f))
            views.setTextColor(R.id.widget_stats_right_value, onContainer)

            // Иконки — цветом контента.
            views.setColorStateList(
                R.id.widget_chip_icon,
                "setImageTintList",
                ColorStateList.valueOf(onContainer),
            )
            views.setColorStateList(
                R.id.widget_ring_image,
                "setImageTintList",
                ColorStateList.valueOf(WidgetColors.withAlpha(onContainer, 0.14f)),
            )
        }
    }
}