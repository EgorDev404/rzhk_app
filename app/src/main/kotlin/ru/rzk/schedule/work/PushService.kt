package ru.rzk.schedule.work

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.rzk.schedule.app
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.data.FetchOutcome
import java.time.LocalDate

/**
 * Принимает push от Firebase Cloud Messaging.
 * Показывает уведомление и кэширует свежий файл, чтобы по нажатию
 * расписание открывалось мгновенно.
 */
class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val day = message.data[Notifier.EXTRA_DAY]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val title = message.notification?.title ?: message.data["title"] ?: "Расписание"
        val text = message.notification?.body ?: message.data["body"] ?: Dates.full(day)

        Notifier.notify(applicationContext, day, title, text)

        // У сервиса есть около 20 секунд — укладываемся в 15.
        val repository = applicationContext.app.repository
        val outcome = runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(15_000) { repository.fetch(day, force = true) }
        }
        // Файл уже скачан и пользователь о нём знает — фоновая проверка не должна уведомлять второй раз.
        if (outcome is FetchOutcome.Ok && !outcome.stale) repository.markSeen(day, outcome.snapshot.sha256)
    }

    /** Гарантируем переподписку на тему при обновлении/ротации токена устройством. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        runCatching {
            val settings = applicationContext.app.settings.current
            Push.sync(applicationContext, settings.notifications)
        }
    }
}
