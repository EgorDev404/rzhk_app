package ru.rzk.schedule.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/** Картинки, относящиеся к одному дню, и «отпечаток» их содержимого (для обнаружения замен). */
class ImageSet(val files: List<File>, val sha: String)

/**
 * Расписание, выложенное не PDF-файлом, а картинками прямо на странице «Расписание занятий».
 *
 * Порядок работы: находим картинки на страницах раздела → скачиваем → читаем из каждой дату
 * (см. [ImageDate]) → отдаём те, что относятся к нужному дню. Дата каждой картинки запоминается
 * по её содержимому, поэтому распознавание выполняется один раз.
 */
class ImageSchedule(context: Context, private val site: SiteClient) {
    val dir = File(context.filesDir, "images").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("schedule_images", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private class Entry(val url: String, val file: File, val sha: String, val day: LocalDate?)

    private var entries: List<Entry> = emptyList()
    private var refreshedAt = 0L

    /** Картинки на [day] или null, если на странице таких нет. [pages] — страницы, где искать. */
    suspend fun forDay(day: LocalDate, force: Boolean, pages: suspend () -> List<String>): ImageSet? =
        mutex.withLock {
            refresh(force, pages)
            val matching = entries.filter { it.day == day }
            if (matching.isEmpty()) null
            else ImageSet(matching.map { it.file }, sha256(matching.map { it.sha }.sorted().joinToString("\n")))
        }

    private suspend fun refresh(force: Boolean, pages: suspend () -> List<String>) {
        val now = SystemClock.elapsedRealtime()
        val minAge = if (force) FORCE_MIN_MS else TTL_MS
        if (refreshedAt != 0L && now - refreshedAt < minAge) return

        val urls = LinkedHashSet<String>()
        var reachable = false
        for (page in pages().take(MAX_PAGES)) {
            val html = site.fetchHtml(page) ?: continue
            reachable = true
            urls += Naming.extractImages(page, html)
        }
        if (!reachable) {
            refreshedAt = now - minAge + RETRY_MS // сайт не ответил — повторим через минуту, старое не выбрасываем
            return
        }
        refreshedAt = now

        val fresh = ArrayList<Entry>()
        for (url in urls.take(MAX_IMAGES)) {
            val old = entries.firstOrNull { it.url == url }
            val bytes = site.fetchImage(url, bustCache = force)
            if (bytes == null) {
                if (old != null) fresh += old // скачать не вышло — остаёмся на том, что уже знаем
                continue
            }
            val sha = sha256(bytes)
            if (old != null && old.sha == sha && old.day != null) {
                fresh += old
                continue
            }
            val file = save(bytes, sha)
            fresh += Entry(url, file, sha, dayOf(sha, file))
        }
        entries = fresh
    }

    private suspend fun dayOf(sha: String, file: File): LocalDate? {
        prefs.getString("day_$sha", null)?.let { saved ->
            return if (saved == "-") null else runCatching { LocalDate.parse(saved) }.getOrNull()
        }
        val day = try {
            ImageDate.read(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null // распознавание не сработало — не запоминаем, попробуем при следующей проверке
        }
        prefs.edit().putString("day_$sha", day?.toString() ?: "-").apply()
        return day
    }

    private fun save(bytes: ByteArray, sha: String): File {
        val ext = when {
            bytes[0] == 0x89.toByte() -> "png"
            bytes[0] == 'R'.code.toByte() -> "webp"
            else -> "jpg"
        }
        val file = File(dir, "${sha.take(24)}.$ext")
        if (!file.exists() || file.length() != bytes.size.toLong()) {
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                file.writeBytes(bytes)
                tmp.delete()
            }
        } else {
            file.setLastModified(System.currentTimeMillis()) // файл ещё нужен — чтобы очистка его не удалила
        }
        return file
    }

    /** Удаляет старые картинки, чтобы не копились. */
    fun cleanup(keepDays: Int = 14) {
        val cutoff = System.currentTimeMillis() - keepDays * 86_400_000L
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun sha256(text: String) = sha256(text.toByteArray())

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TTL_MS = 5 * 60_000L        // как часто перечитывать страницу в обычном режиме
        const val FORCE_MIN_MS = 10_000L      // «принудительно» (push, обновление жестом): не чаще раза в 10 секунд
        const val RETRY_MS = 60_000L
        const val MAX_PAGES = 3
        const val MAX_IMAGES = 12
    }
}
