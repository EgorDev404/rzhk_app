package ru.rzk.schedule.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.rzk.schedule.app
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.data.FetchOutcome
import java.time.DayOfWeek

/** Фоновая проверка сайта: сегодня и ближайшие два дня. Сообщает только о том, чего пользователь ещё не видел. */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = applicationContext.app.repository
        val today = Dates.today()
        for (offset in 0L..2L) {
            val day = today.plusDays(offset)
            if (day.dayOfWeek == DayOfWeek.SUNDAY) continue
            val outcome = repository.fetch(day, force = true)
            if (outcome !is FetchOutcome.Ok || outcome.stale) continue

            val sha = outcome.snapshot.sha256
            val previous = repository.seenSha(day)
            if (previous == sha) continue
            repository.markSeen(day, sha)

            val title = if (previous == null) "Вышло расписание" else "Расписание изменено"
            Notifier.notify(applicationContext, day, title, Dates.full(day, today))
        }
        return Result.success()
    }
}
