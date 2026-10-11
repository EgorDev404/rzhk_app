package ru.rzk.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import ru.rzk.schedule.MainActivity
import ru.rzk.schedule.R
import ru.rzk.schedule.data.BellSchedule
import ru.rzk.schedule.data.BellState
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.ui.theme.WidgetColors
import java.time.LocalDateTime

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

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            manager.updateAppWidget(id, buildViews(context, SizeClass.from(options)))
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        manager.updateAppWidget(id, buildViews(context, SizeClass.from(newOptions)))
    }

    enum class SizeClass {
        Compact, Normal, Full;

        companion object {
            fun from(options: Bundle): SizeClass {
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
                return when {
                    heightDp < 140 -> Compact
                    heightDp < 180 -> Normal
                    else -> Full
                }
            }
        }
    }

    companion object {
        fun buildViews(context: Context, size: SizeClass = SizeClass.Full): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bells_large)
            val now = LocalDateTime.now(Dates.zone)
            val state = BellSchedule.state(now, BellSchedule.SHORT_DAY)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_BELLS)
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_bg, pending)

            applyState(context, views, state, size)
            return views
        }

        private fun applyState(context: Context, views: RemoteViews, state: BellState, size: SizeClass) {
            val chipText: String
            val bigText: String
            val bigLabel: String
            val statsLeftLabel: String
            val statsLeftValue: String
            val statsRightLabel: String
            val statsRightValue: String
            val footer: String
            val hasStats: Boolean
            val showRing: Boolean
            val progress: Float

            when (state) {
                is BellState.InLesson -> {
                    chipText = "Идёт ${state.bell.number} урок"
                    bigText = BellSchedule.remainingLabel(state.remaining)
                    bigLabel = "до конца урока"
                    statsLeftLabel = "Начался"
                    statsLeftValue = BellSchedule.hm(state.bell.start)
                    statsRightLabel = "Закончится"
                    statsRightValue = BellSchedule.hm(state.bell.end)
                    footer = state.next?.let {
                        "Дальше: перемена ${BellSchedule.minutes(state.breakAfter!!)}, затем ${it.number} урок в ${BellSchedule.hm(it.start)}"
                    } ?: "Это последний урок на сегодня"
                    hasStats = true
                    showRing = true
                    progress = state.progress
                }
                is BellState.InBreak -> {
                    chipText = "Перемена"
                    bigText = BellSchedule.remainingLabel(state.remaining)
                    bigLabel = "до начала ${state.next.number} урока"
                    statsLeftLabel = "Урок ${state.after.number} закончился"
                    statsLeftValue = BellSchedule.hm(state.after.end)
                    statsRightLabel = "Урок ${state.next.number} начнётся"
                    statsRightValue = BellSchedule.hm(state.next.start)
                    footer = "Перемена длится ${BellSchedule.minutes(state.total)}"
                    hasStats = true
                    showRing = true
                    progress = state.progress
                }
                is BellState.BeforeClasses -> {
                    chipText = "Занятия ещё не начались"
                    bigText = BellSchedule.remainingLabel(state.remaining)
                    bigLabel = "до начала ${state.first.number} урока"
                    statsLeftLabel = "Начало занятий"
                    statsLeftValue = BellSchedule.hm(state.first.start)
                    statsRightLabel = ""
                    statsRightValue = ""
                    footer = "${state.first.number} урок: ${BellSchedule.range(state.first)}"
                    hasStats = true
                    showRing = false
                    progress = 0f
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
                    hasStats = false
                    showRing = false
                    progress = 1f
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
                    hasStats = false
                    showRing = false
                    progress = 1f
                }
            }

            val showStats = hasStats && size != SizeClass.Compact
            val showFooter = size == SizeClass.Full
            val showBigLabel = size != SizeClass.Compact
            val showRingNow = showRing && size != SizeClass.Compact

            views.setTextViewText(R.id.widget_chip, chipText)
            views.setTextViewText(R.id.widget_ring_percent, "${(progress * 100).toInt()}%")
            views.setTextViewText(R.id.widget_big, bigText)
            views.setTextViewText(R.id.widget_big_label, bigLabel)
            views.setTextViewText(R.id.widget_footer, footer)

            views.setViewVisibility(R.id.widget_big_label, if (showBigLabel) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_stats, if (showStats) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_footer, if (showFooter) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_ring, if (showRingNow) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_big_timer, View.GONE)

            if (showStats) {
                views.setTextViewText(R.id.widget_stat_left_label, statsLeftLabel)
                views.setTextViewText(R.id.widget_stat_left_value, statsLeftValue)
                views.setTextViewText(R.id.widget_stat_right_label, statsRightLabel)
                views.setTextViewText(R.id.widget_stat_right_value, statsRightValue)
                views.setViewVisibility(R.id.widget_stat_right, if (statsRightValue.isEmpty()) View.GONE else View.VISIBLE)
            }

            applyThemeColors(context, views, state, progress)
        }

        private fun applyThemeColors(context: Context, views: RemoteViews, state: BellState, progress: Float) {
            val c = WidgetColors.load(context)

            val (container, onContainer) = when (state) {
                is BellState.InLesson -> c.primaryContainer to c.onPrimaryContainer
                is BellState.InBreak -> c.tertiaryContainer to c.onTertiaryContainer
                is BellState.BeforeClasses -> c.secondaryContainer to c.onSecondaryContainer
                else -> c.primaryContainer to c.onPrimaryContainer
            }

            views.setColorStateList(R.id.widget_bg, "setImageTintList", ColorStateList.valueOf(container))
            views.setTextColor(R.id.widget_chip, onContainer)
            views.setTextColor(R.id.widget_big, onContainer)
            views.setTextColor(R.id.widget_ring_percent, onContainer)
            views.setTextColor(R.id.widget_big_label, WidgetColors.withAlpha(onContainer, 0.8f))
            views.setTextColor(R.id.widget_footer, WidgetColors.withAlpha(onContainer, 0.85f))

            views.setColorStateList(R.id.widget_chip_bg, "setImageTintList",
                ColorStateList.valueOf(WidgetColors.withAlpha(onContainer, 0.12f)))

            views.setColorStateList(R.id.widget_stat_left, "setBackgroundTintList",
                ColorStateList.valueOf(WidgetColors.withAlpha(onContainer, 0.10f)))
            views.setColorStateList(R.id.widget_stat_right, "setBackgroundTintList",
                ColorStateList.valueOf(WidgetColors.withAlpha(onContainer, 0.10f)))
            views.setColorStateList(R.id.widget_stat_left_bg, "setImageTintList",
                ColorStateList.valueOf(WidgetColors.withAlpha(onContainer, 0.10f)))
            views.setColorStateList(R.id.widget_stat_right_bg, "setImageTintList",
                ColorStateList.valueOf(WidgetColors.withAlpha(onContainer, 0.10f)))
            views.setTextColor(R.id.widget_stat_left_label, WidgetColors.withAlpha(onContainer, 0.75f))
            views.setTextColor(R.id.widget_stat_left_value, onContainer)
            views.setTextColor(R.id.widget_stat_right_label, WidgetColors.withAlpha(onContainer, 0.75f))
            views.setTextColor(R.id.widget_stat_right_value, onContainer)

            views.setColorStateList(R.id.widget_chip_icon, "setImageTintList",
                ColorStateList.valueOf(onContainer))

            val ringBitmap = renderProgressRing(
                size = 140,
                progress = progress,
                trackColor = WidgetColors.withAlpha(onContainer, 0.14f),
                progressColor = onContainer,
                strokeWidth = 14f,
            )
            views.setImageViewBitmap(R.id.widget_ring_image, ringBitmap)
        }

        private fun renderProgressRing(
            size: Int,
            progress: Float,
            trackColor: Int,
            progressColor: Int,
            strokeWidth: Float,
        ): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val inset = strokeWidth / 2f
            val rect = RectF(inset, inset, size - inset, size - inset)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = trackColor
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, 0f, 360f, false, trackPaint)

            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = progressColor
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, -90f, 360f * progress.coerceIn(0f, 1f), false, progressPaint)

            return bmp
        }
    }
}