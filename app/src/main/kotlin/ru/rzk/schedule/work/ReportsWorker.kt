package ru.rzk.schedule.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.rzk.schedule.app
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.data.Reports

/**
 * Работает только в режиме разработчика: смотрит, не пожаловались ли пользователи на красную лампочку,
 * и показывает уведомление, если жалоб стало больше, чем разработчик уже видел сегодня.
 */
class ReportsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = applicationContext.app.settings
        if (!store.current.devMode) return Result.success()

        val day = Dates.today().toString()
        val count = Reports.countToday() ?: return Result.retry()
        if (count > store.devSeen(day)) {
            store.setDevSeen(day, count)
            Notifier.notifyReports(applicationContext, count)
        }
        return Result.success()
    }
}
