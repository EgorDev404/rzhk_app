package ru.rzk.schedule.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ru.rzk.schedule.data.Settings
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val NAME = "check_schedules"
    private const val REPORTS_NAME = "dev_reports"

    /**
     * Основной способ узнать о новом расписании — push с сервера. Фоновая проверка раз в час —
     * запасной вариант на случай, если push по какой-то причине не дошёл.
     */
    private const val FALLBACK_MINUTES = 60L
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Включает или выключает периодическую проверку согласно настройкам. */
    fun apply(context: Context, settings: Settings) {
        Push.sync(context, settings.notifications) // подписка на push следует за тем же переключателем
        val manager = WorkManager.getInstance(context)

        // Режим разработчика: раз в ~15 минут (минимум для WorkManager) смотрим, не пожаловались ли пользователи.
        if (settings.devMode) {
            val reports = PeriodicWorkRequestBuilder<ReportsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(REPORTS_NAME, ExistingPeriodicWorkPolicy.UPDATE, reports)
        } else {
            manager.cancelUniqueWork(REPORTS_NAME)
        }
        if (!settings.notifications) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<CheckWorker>(FALLBACK_MINUTES, TimeUnit.MINUTES)
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Разовая проверка прямо сейчас (кнопка в настройках). */
    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CheckWorker>().setConstraints(online).build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
