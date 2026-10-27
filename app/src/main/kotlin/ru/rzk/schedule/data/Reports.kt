package ru.rzk.schedule.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Жалобы пользователей на красную лампочку.
 *
 * Жалоба — одна маленькая запись в Firebase Realtime Database: reports/<дата по Москве>/<номер установки>.
 * Номер установки случайный и ничего не говорит о человеке. Правила базы разрешают создать запись один раз
 * и не позволяют её менять, поэтому повторные нажатия ничего не дают.
 * Телефон разработчика (режим разработчика) раз в ~15 минут смотрит, сколько записей накопилось за сегодня.
 */
object Reports {
    enum class SendResult { Sent, AlreadySent, Failed }

    private val json = "application/json".toMediaType()

    suspend fun send(installId: String, version: String, lastCheckAgeSec: Long?): SendResult = withContext(Dispatchers.IO) {
        try {
            val url = "${SystemStatus.DB_URL}/reports/${Dates.today()}/$installId.json"

            // Уже жаловались сегодня (например, после переустановки или очистки данных)?
            val exists = SystemStatus.http.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build())
                .execute().use { response ->
                    if (!response.isSuccessful) return@withContext SendResult.Failed
                    response.body?.string().orEmpty().trim().let { it.isNotEmpty() && it != "null" }
                }
            if (exists) return@withContext SendResult.AlreadySent

            val body = JSONObject()
                .put("t", JSONObject().put(".sv", "timestamp")) // время ставит сервер Firebase, а не телефон
                .put("v", version.take(24))
                .apply { if (lastCheckAgeSec != null) put("age", lastCheckAgeSec) }
                .toString()
            SystemStatus.http.newCall(Request.Builder().url(url).put(body.toRequestBody(json)).build())
                .execute().use { response ->
                    if (response.isSuccessful) SendResult.Sent else SendResult.Failed
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SendResult.Failed
        }
    }

    /** Сколько жалоб поступило сегодня; null — не удалось узнать. */
    suspend fun countToday(): Int? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${SystemStatus.DB_URL}/reports/${Dates.today()}.json?shallow=true")
                .header("Cache-Control", "no-cache")
                .build()
            SystemStatus.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty().trim()
                if (body.isEmpty() || body == "null") 0 else JSONObject(body).length()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
