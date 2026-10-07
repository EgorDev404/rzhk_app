package ru.rzk.schedule.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Что вернул запрос к одному адресу. */
sealed interface Probe {
    class Pdf(val bytes: ByteArray) : Probe
    data object Missing : Probe                       // 403/404/410 или «страница-заглушка» вместо PDF
    class Failed(val cause: Throwable?) : Probe       // сеть, таймаут, 5xx
}

/** Тонкая обёртка над OkHttp: только сеть, без знания про расписание. */
class SiteClient(private val http: OkHttpClient = defaultClient()) {

    suspend fun probe(url: String, bustCache: Boolean): Probe = withContext(Dispatchers.IO) {
        val target = if (bustCache) "$url?v=${System.currentTimeMillis()}" else url
        val request = Request.Builder().url(target).apply {
            if (bustCache) {
                header("Cache-Control", "no-cache")
                header("Pragma", "no-cache")
            }
        }.build()
        try {
            http.newCall(request).await().use { response ->
                when {
                    response.code in MISSING_CODES -> Probe.Missing
                    response.code != 200 -> Probe.Failed(IOException("HTTP ${response.code}"))
                    else -> {
                        val body = response.body ?: return@use Probe.Failed(IOException("empty body"))
                        val bytes = readLimited(body.byteStream()) ?: return@use Probe.Failed(IOException("too large"))
                        // Сигнатура надёжнее Content-Type: CMS часто отдаёт octet-stream,
                        // а «страницу 404» — со статусом 200.
                        if (looksLikePdf(bytes)) Probe.Pdf(bytes) else Probe.Missing
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Probe.Failed(e)
        }
    }

    /** HTML страницы или null, если не удалось получить. */
    suspend fun fetchHtml(url: String): String? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url).build()).await().use { response ->
                val type = response.header("Content-Type").orEmpty().lowercase()
                if (response.code != 200 || "pdf" in type) null
                else response.body?.string()?.take(1_500_000)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        }
    }

    /** Отвечает ли сайт вообще (главная страница). Нужно, чтобы отличить «файла нет» от «сайт лежит». */
    suspend fun siteAlive(): Boolean = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(Naming.SITE_ROOT).build()).await().use { it.code in 200..399 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            false
        }
    }

    private fun looksLikePdf(bytes: ByteArray): Boolean =
        String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1).contains("%PDF")

    private fun readLimited(input: InputStream, max: Int = 25 * 1024 * 1024): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > max) return null
        }
        return out.toByteArray()
    }

    companion object {
        private val MISSING_CODES = setOf(403, 404, 410)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) RzkSchedule/1.0")
                        .build(),
                )
            }
            .build()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
    continuation.invokeOnCancellation { cancel() }
}
