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
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Включает или выключает периодическую проверку согласно настройкам. */
    fun apply(context: Context, settings: Settings) {
        Push.sync(context, settings.notifications) // подписка на push следует за тем же переключателем
        val manager = WorkManager.getInstance(context)
        if (!settings.notifications) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<CheckWorker>(settings.intervalMinutes.toLong(), TimeUnit.MINUTES)
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
