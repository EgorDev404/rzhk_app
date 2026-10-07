package ru.rzk.schedule.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.rzk.schedule.MainActivity
import ru.rzk.schedule.R
import java.time.LocalDate

object Notifier {
    private const val CHANNEL = "schedule"
    const val EXTRA_DAY = "day"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Расписание", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Сообщает, когда расписание выложено или изменено"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun notify(context: Context, day: LocalDate, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DAY, day.toString())
        }
        val id = day.toEpochDay().toInt()
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // разрешение отозвали между проверкой и показом — молча пропускаем
        }
    }
}
