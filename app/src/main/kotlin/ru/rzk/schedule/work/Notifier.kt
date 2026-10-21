package ru.rzk.schedule.work

import android.annotation.SuppressLint
import android.app.Notification
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
    /** Id канала. Важность канала после создания меняет только пользователь, поэтому при смене — новый id. */
    const val CHANNEL = "schedule_v3"
    private const val OLD_CHANNEL = "schedule_v2"
    const val EXTRA_DAY = "day"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(OLD_CHANNEL) // старый канал был с обычной важностью

        val channel = NotificationChannel(CHANNEL, "Расписание", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Сообщает, когда расписание выложено или изменено"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            enableLights(true)
            lightColor = 0xFF1976D2.toInt()
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Уведомление на конкретный день. Метка (tag) = дата, номер = 0: ровно так же помечает свои
     * уведомления push с сервера (tag в сообщении). Поэтому, если о том же расписании сообщили и push,
     * и фоновая проверка, на экране остаётся ОДНО уведомление, а не два.
     */
    @SuppressLint("MissingPermission")
    fun notify(context: Context, day: LocalDate, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DAY, day.toString())
        }
        val pending = PendingIntent.getActivity(
            context,
            day.toEpochDay().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true) // замена существующего уведомления — без повторного звука
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        try {
            manager.notify(day.toString(), 0, notification)
        } catch (_: SecurityException) {
            // разрешение отозвали между проверкой и показом — молча пропускаем
        }
    }
}
