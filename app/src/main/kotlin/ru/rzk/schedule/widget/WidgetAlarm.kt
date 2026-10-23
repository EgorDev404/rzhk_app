package ru.rzk.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.rzk.schedule.data.BellSchedule
import ru.rzk.schedule.data.Dates
import java.time.LocalDateTime

/**
 * Точное обновление виджета: один будильник, который каждый раз ставит следующий.
 *
 * Вместо `updatePeriodMillis` (система не даёт чаще раза в 30 минут) и `ACTION_TIME_TICK` (через манифест он
 * не приходит) используем AlarmManager. Момент следующего обновления считает [BellSchedule.nextRefresh]:
 * раз в минуту во время занятий и ровно по звонку, а когда занятий нет — почти не просыпаемся.
 *
 * Тип будильника `RTC` (не `RTC_WAKEUP`): телефон не будят ради виджета, а при включении экрана
 * пропущенный тик приходит сразу, и виджет мгновенно показывает актуальное время.
 */
object WidgetAlarm {
    const val ACTION_TICK = "ru.rzk.schedule.widget.ACTION_TICK"
    private const val REQUEST_CODE = 4101

    /** Запас после границы минуты: если будильник сработает на миллисекунды раньше, виджет не покажет «на минуту больше». */
    private const val TICK_DELAY_MS = 1_000L

    /** Ставит (или перезаписывает) будильник на ближайшее нужное обновление. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now(Dates.zone)
        val triggerAt = BellSchedule.nextRefresh(now, BellsWidgetUpdater.lessonCount(context))
            .atZone(Dates.zone).toInstant().toEpochMilli() + TICK_DELAY_MS
        val pending = pendingIntent(context)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            // Разрешение на точные будильники отозвали между проверкой и вызовом — обновимся неточно.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pending)
        }
    }

    /** Снимает будильник — когда на экране не осталось ни одного виджета. */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BellsWidget::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
