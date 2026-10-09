package ru.rzk.schedule.work

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Подписка телефона на push-уведомления о расписании (Firebase Cloud Messaging, «тема» schedule).
 * Сервер шлёт одно сообщение в тему — Google сам доставляет его всем подписанным телефонам.
 *
 * Если Firebase не настроен (нет google-services.json) — тихо ничего не делаем:
 * остаётся фоновая проверка через WorkManager.
 */
object Push {
    private const val TAG = "Push"
    const val TOPIC = "schedule"

    /** Тема для тестов: в неё подписываются только отладочные сборки, студенты тестовые пуши не увидят. */
    private const val TEST_TOPIC = "schedule-test"

    /** Идемпотентно: можно вызывать при каждом запуске и при каждом изменении настроек. */
    fun sync(context: Context, enabled: Boolean) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return

        val topics = buildList {
            add(TOPIC)
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) add(TEST_TOPIC)
        }
        for (topic in topics) {
            val task = if (enabled) messaging.subscribeToTopic(topic) else messaging.unsubscribeFromTopic(topic)
            task.addOnFailureListener { Log.w(TAG, "Не удалось ${if (enabled) "подписаться на" else "отписаться от"} $topic", it) }
        }
    }
}
