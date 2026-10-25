package ru.rzk.schedule.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

enum class LoadStage { Connecting, Searching, Rendering }
enum class FailureKind { Offline, SiteDown, Certificate, BadFile }

/**
 * Расписание на день. Либо PDF-файл ([pdf]), либо набор картинок со страницы сайта ([images]):
 * колледж выкладывает его то так, то так.
 */
data class Snapshot(
    val day: LocalDate,
    val pdf: File?,
    val sha256: String,
    val checkedAt: Instant,
    val images: List<File> = emptyList(),
)

sealed interface FetchOutcome {
    /** [changed] — содержимое отличается от того, что было; [stale] — сайт недоступен, отдана сохранённая копия. */
    data class Ok(val snapshot: Snapshot, val changed: Boolean, val stale: Boolean = false) : FetchOutcome
    data object NotPublished : FetchOutcome
    data class Failed(val kind: FailureKind) : FetchOutcome
}

/**
 * Единственное место, которое знает, как получить расписание на день:
 * кэш на диске → сеть (подбор имён → страницы сайта) → сохранение → рендер страниц.
 */
class ScheduleRepository(private val context: Context, private val site: SiteClient) {
    private val meta = context.getSharedPreferences("schedule_meta", Context.MODE_PRIVATE)
    private val seen = context.getSharedPreferences("schedule_seen", Context.MODE_PRIVATE)
    private val pdfDir = File(context.filesDir, "pdf").apply { mkdirs() }
    private val renderDir = File(context.filesDir, "render").apply { mkdirs() }
    private val locks = ConcurrentHashMap<LocalDate, Mutex>()
    private val imageSchedule = ImageSchedule(context, site)

    @Volatile private var links: List<String> = emptyList()
    @Volatile private var schedulePages: List<String> = emptyList()
    @Volatile private var linksAt = 0L

    // ---- кэш ------------------------------------------------------------------------------------

    fun cached(day: LocalDate): Snapshot? {
        val file = pdfFile(day)
        if (!file.exists() || file.length() == 0L) return cachedImages(day)
        val sha = meta.getString("sha_$day", null)
            ?: sha256(file.readBytes()).also { meta.edit().putString("sha_$day", it).apply() }
        val checkedAt = meta.getLong("at_$day", file.lastModified())
        return Snapshot(day, file, sha, Instant.ofEpochMilli(checkedAt))
    }

    /** Расписание, сохранённое картинками (см. [ImageSchedule]). */
    private fun cachedImages(day: LocalDate): Snapshot? {
        val names = meta.getString("img_$day", null) ?: return null
        val files = names.split('|').filter { it.isNotEmpty() }.map { File(imageSchedule.dir, it) }
        if (files.isEmpty() || files.any { !it.exists() }) return null
        val sha = meta.getString("sha_$day", null) ?: return null
        return Snapshot(day, null, sha, Instant.ofEpochMilli(meta.getLong("at_$day", 0L)), files)
    }

    /** Страницы для показа: картинки отдаём как есть, PDF превращаем в картинки. */
    suspend fun pages(snapshot: Snapshot): List<File> {
        if (snapshot.images.isNotEmpty()) return snapshot.images
        val pdf = snapshot.pdf ?: return emptyList()
        return PdfPages.render(pdf, File(renderDir, snapshot.sha256.take(24)))
    }

    // ---- что пользователь уже видел (для уведомлений) -------------------------------------------

    fun seenSha(day: LocalDate): String? = seen.getString("sha_$day", null)
    fun markSeen(day: LocalDate, sha: String) = seen.edit().putString("sha_$day", sha).apply()

    // ---- загрузка -------------------------------------------------------------------------------

    /**
     * [force] = true — игнорировать кэш и перекачать файл заново (даже если имя то же самое).
     * Без [force] свежий кэш (моложе 5 минут) отдаётся без обращения к сайту.
     */
    suspend fun fetch(day: LocalDate, force: Boolean, onStage: (LoadStage) -> Unit = {}): FetchOutcome =
        locks.getOrPut(day) { Mutex() }.withLock {
            val cached = cached(day)
            if (!force && cached != null && Duration.between(cached.checkedAt, Instant.now()) < FRESH_FOR) {
                return@withLock FetchOutcome.Ok(cached, changed = false)
            }

            onStage(LoadStage.Searching)
            when (val result = find(day, force)) {
                is Found.Hit -> {
                    val sha = sha256(result.bytes)
                    val previous = meta.getString("sha_$day", null)
                    val file = pdfFile(day)
                    if (previous != sha || !file.exists()) writeAtomically(file, result.bytes)
                    val now = System.currentTimeMillis()
                    meta.edit().putString("sha_$day", sha).putLong("at_$day", now).remove("img_$day").apply {
                        result.template?.let { putString("template", it) } // запоминаем, какой шаблон сработал
                    }.apply()
                    FetchOutcome.Ok(Snapshot(day, file, sha, Instant.ofEpochMilli(now)), changed = previous != sha)
                }
                is Found.Images -> {
                    val previous = meta.getString("sha_$day", null)
                    val now = System.currentTimeMillis()
                    meta.edit()
                        .putString("sha_$day", result.sha)
                        .putLong("at_$day", now)
                        .putString("img_$day", result.files.joinToString("|") { it.name })
                        .apply()
                    FetchOutcome.Ok(
                        Snapshot(day, null, result.sha, Instant.ofEpochMilli(now), result.files),
                        changed = previous != result.sha,
                    )
                }
                Found.NotFound ->
                    if (cached != null) FetchOutcome.Ok(cached, changed = false) else FetchOutcome.NotPublished
                is Found.Unreachable ->
                    if (cached != null) FetchOutcome.Ok(cached, changed = false, stale = true)
                    else FetchOutcome.Failed(failureKind(result.cause))
            }
        }

    // ---- поиск файла ----------------------------------------------------------------------------

    private sealed interface Found {
        class Hit(val template: String?, val bytes: ByteArray) : Found
        class Images(val files: List<File>, val sha: String) : Found
        data object NotFound : Found
        class Unreachable(val cause: Throwable?) : Found
    }

    private class Item(val url: String, val template: String?)
    private class Probed(val hit: Found.Hit?, val anyMissing: Boolean, val cause: Throwable?)

    private suspend fun find(day: LocalDate, force: Boolean): Found {
        val candidates = Naming.candidates(day, meta.getString("template", null))
        val primary = probeOrdered(candidates.map { Item(it.url, it.template) }, force)
        primary.hit?.let { return it }

        // Все запросы упали. Это сломанная сеть или сайт просто «рвёт» соединение на несуществующих файлах?
        if (!primary.anyMissing && !site.siteAlive()) return Found.Unreachable(primary.cause)

        // Запасной путь: имя может быть записано как угодно — ищем по ссылкам на страницах сайта.
        val tried = candidates.mapTo(HashSet()) { it.url }
        val extra = Naming.matchLinks(day, listing()).filter { it !in tried }
        if (extra.isNotEmpty()) {
            probeOrdered(extra.map { Item(it, null) }, force).hit?.let { return it }
        }

        // Расписание могли выложить не файлом, а картинками прямо на странице раздела.
        imageSchedule.forDay(day, force) { schedulePages() }?.let { return Found.Images(it.files, it.sha) }
        return Found.NotFound
    }

    /** Проверяет адреса параллельно (по 4), но результат выбирает строго по приоритету списка. */
    private suspend fun probeOrdered(items: List<Item>, force: Boolean): Probed = coroutineScope {
        val gate = Semaphore(4)
        val jobs = items.map { item -> async { item to gate.withPermit { site.probe(item.url, force) } } }
        var anyMissing = false
        var cause: Throwable? = null
        for (job in jobs) {
            val (item, probe) = job.await()
            when (probe) {
                is Probe.Pdf -> {
                    jobs.forEach { it.cancel() }
                    return@coroutineScope Probed(Found.Hit(item.template, probe.bytes), anyMissing, cause)
                }
                Probe.Missing -> anyMissing = true
                is Probe.Failed -> if (cause == null) cause = probe.cause
            }
        }
        Probed(null, anyMissing, cause)
    }

    private suspend fun listing(): List<String> {
        val now = SystemClock.elapsedRealtime()
        if (linksAt != 0L && now - linksAt < LISTING_TTL_MS) return links
        val html = site.fetchHtml(Naming.SITE_ROOT)
        val found = LinkedHashSet<String>()
        val pages = LinkedHashSet<String>()
        if (html != null) {
            found += Naming.extractPdfLinks(Naming.SITE_ROOT, html)
            pages += Naming.extractSchedulePages(Naming.SITE_ROOT, html)
        }
        pages += Naming.GUESSED_SCHEDULE_PAGES // на страницу раздела с главной может не быть ссылки
        for (page in pages) {
            site.fetchHtml(page)?.let { found += Naming.extractPdfLinks(page, it) }
        }
        links = found.toList()
        schedulePages = pages.toList()
        // не получилось открыть главную — повторим через минуту, а не через 5
        linksAt = if (html != null) now else now - LISTING_TTL_MS + 60_000
        return links
    }

    /** Страницы раздела «Расписание», где могут лежать ссылки на PDF и картинки. */
    private suspend fun schedulePages(): List<String> {
        listing()
        return schedulePages
    }

    // ---- служебное ------------------------------------------------------------------------------

    private fun failureKind(cause: Throwable?): FailureKind = when {
        !isOnline() || cause is UnknownHostException -> FailureKind.Offline
        cause is SSLException -> FailureKind.Certificate
        else -> FailureKind.SiteDown
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun pdfFile(day: LocalDate) = File(pdfDir, "$day.pdf")

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Удаляет старые файлы, чтобы не копились. */
    fun cleanup(keepDays: Int = 14) {
        val cutoff = System.currentTimeMillis() - keepDays * 86_400_000L
        pdfDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        renderDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        imageSchedule.cleanup(keepDays)
    }

    private companion object {
        val FRESH_FOR: Duration = Duration.ofMinutes(5)
        const val LISTING_TTL_MS = 5 * 60_000L
    }
}
