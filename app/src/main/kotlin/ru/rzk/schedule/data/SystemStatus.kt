package ru.rzk.schedule.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Состояние «лампочки» системы оповещения (серверной части, которая шлёт push). */
enum class LampState {
    /** Ещё проверяем. */
    Loading,

    /** Сервер работает и проверяет сайт колледжа. */
    Working,

    /** Сервер остановлен или давно не выходит на связь. */
    Down,

    /** Сервер работает, но не может открыть сайт колледжа. */
    SiteUnreachable,

    /** У телефона нет интернета. */
    Offline,

    /** Интернет есть, но состояние сервера узнать не удалось. */
    Unknown,
}

/** [lastCheckAgeSec] — сколько секунд назад сервер в последний раз проверял сайт (если известно). */
data class LampStatus(val state: LampState, val lastCheckAgeSec: Long? = null)

/** Запись, которую сервер раз в минуту кладёт в Firebase Realtime Database (см. rzk_push/status.py). */
class ServerStatus(val polled: Long, val updated: Long, val site: String, val stopping: Boolean)

object SystemStatus {
    /**
     * Адрес вашей Realtime Database в Firebase (без «/» в конце). Берётся в консоли Firebase: Build → Realtime Database.
     * Если база создана не в США, адрес другой, например
     * https://schedulerrzhk-default-rtdb.europe-west1.firebasedatabase.app
     */
    const val DB_URL = "https://schedulerrzhk-default-rtdb.firebaseio.com"

    const val STATUS_URL = "$DB_URL/status.json"

    /** Сервер опрашивает сайт каждые 20–60 секунд; если запись не обновлялась дольше — считаем, что он не работает. */
    const val STALE_AFTER_SEC = 300L

    sealed interface Result {
        /** [status] == null — запись в базе пуста (сервер ещё ни разу не публиковал статус). */
        class Ok(val status: ServerStatus?, val serverNowSec: Long) : Result
        object Failed : Result
    }

    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(STATUS_URL).header("Cache-Control", "no-cache").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use Result.Failed
                val body = response.body?.string().orEmpty().trim()
                // Время сверяем по часам сервера Google (заголовок Date), а не по часам телефона: они могут врать.
                Result.Ok(parse(body), serverNow(response.header("Date")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed
        }
    }

    private fun parse(body: String): ServerStatus? {
        if (body.isEmpty() || body == "null") return null
        val json = JSONObject(body)
        return ServerStatus(
            polled = json.optLong("polled", 0L),
            updated = json.optLong("updated", 0L),
            site = json.optString("site", "unknown"),
            stopping = json.optBoolean("stopping", false),
        )
    }

    private fun serverNow(dateHeader: String?): Long =
        runCatching { ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() }
            .getOrNull() ?: (System.currentTimeMillis() / 1000)

    /** Превращает ответ сервера в состояние лампочки. */
    fun lampFor(result: Result): LampStatus = when (result) {
        Result.Failed -> LampStatus(LampState.Unknown)
        is Result.Ok -> {
            val status = result.status
            when {
                status == null || status.stopping -> LampStatus(LampState.Down)
                // Сервер только что запущен и ещё не завершил первый опрос сайта.
                status.polled == 0L ->
                    if (result.serverNowSec - status.updated > STALE_AFTER_SEC) {
                        LampStatus(LampState.Down, (result.serverNowSec - status.updated).coerceAtLeast(0L))
                    } else {
                        LampStatus(LampState.Loading)
                    }
                else -> {
                    val age = (result.serverNowSec - status.polled).coerceAtLeast(0L)
                    when {
                        age > STALE_AFTER_SEC -> LampStatus(LampState.Down, age)
                        status.site == "down" -> LampStatus(LampState.SiteUnreachable, age)
                        else -> LampStatus(LampState.Working, age)
                    }
                }
            }
        }
    }
}
