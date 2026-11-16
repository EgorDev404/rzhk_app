package ru.rzk.schedule.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Сохранённая закладка с расписанием.
 *
 * Все страницы копируются в отдельную папку `filesDir/bookmarks/<id>/`, поэтому закладка
 * остаётся доступной навсегда — даже после автоматической очистки кэша старых расписаний.
 */
data class BookmarkEntry(
    val id: String,
    val day: LocalDate,
    val comment: String,
    val createdAt: Instant,
    val pages: List<File>,
)

class BookmarksStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("bookmarks_store", Context.MODE_PRIVATE)
    private val rootDir by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        File(appContext.filesDir, "bookmarks").apply { mkdirs() }
    }

    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<List<BookmarkEntry>> = _flow.asStateFlow()

    val current: List<BookmarkEntry> get() = _flow.value

    /** Есть ли закладка на указанный день [day]. */
    fun findByDay(day: LocalDate): BookmarkEntry? = _flow.value.firstOrNull { it.day == day }

    /**
     * Сохраняет или обновляет закладку на день [day] с комментарием [comment].
     * Копирует файлы страниц [sourcePages] в постоянное хранилище закладок.
     */
    suspend fun save(day: LocalDate, sourcePages: List<File>, comment: String): BookmarkEntry =
        withContext(Dispatchers.IO) {
            val existing = findByDay(day)
            val id = existing?.id ?: day.toString()
            val dir = File(rootDir, id).apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            val copied = ArrayList<File>(sourcePages.size)
            sourcePages.forEachIndexed { index, src ->
                if (src.exists()) {
                    val ext = src.extension.ifEmpty { "jpg" }
                    val target = File(dir, "page_${(index + 1).toString().padStart(2, '0')}.$ext")
                    src.copyTo(target, overwrite = true)
                    copied += target
                }
            }
            val entry = BookmarkEntry(
                id = id,
                day = day,
                comment = comment.trim(),
                createdAt = existing?.createdAt ?: Instant.now(),
                pages = copied,
            )
            val updated = buildList {
                add(entry)
                _flow.value.forEach { if (it.id != id) add(it) }
            }.sortedWith(compareByDescending<BookmarkEntry> { it.day }.thenByDescending { it.createdAt })

            persist(updated)
            _flow.value = updated
            entry
        }

    /** Обновляет комментарий у существующей закладки [id]. */
    fun updateComment(id: String, comment: String) {
        val trimmed = comment.trim()
        val updated = _flow.value.map {
            if (it.id == id) it.copy(comment = trimmed) else it
        }
        persist(updated)
        _flow.value = updated
    }

    /** Удаляет закладку [id] и её сохранённые файлы. */
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        File(rootDir, id).deleteRecursively()
        val updated = _flow.value.filterNot { it.id == id }
        persist(updated)
        _flow.value = updated
    }

    /** Удаляет закладку по дате [day]. */
    suspend fun removeByDay(day: LocalDate) {
        val entry = findByDay(day) ?: return
        remove(entry.id)
    }

    /**
     * Умный поиск по закладкам: ищет по тексту комментария, дате (`16.11`, `16.11.2026`, `2026-11-16`),
     * дню недели (`понедельник`, `пн`) и названию месяца (`ноябрь`, `ноября`).
     */
    fun matches(entry: BookmarkEntry, rawQuery: String): Boolean {
        val q = rawQuery.trim().lowercase(ruLocale)
        if (q.isEmpty()) return true
        val haystack = buildString {
            append(entry.comment.lowercase(ruLocale)).append(' ')
            append(Dates.full(entry.day).lowercase(ruLocale)).append(' ')
            append(Dates.weekday(entry.day).lowercase(ruLocale)).append(' ')
            append(Dates.weekdayShort(entry.day).lowercase(ruLocale)).append(' ')
            append(Dates.dayMonth(entry.day)).append(' ')
            append(entry.day.toString()).append(' ')
            append(
                "%02d.%02d.%04d".format(
                    entry.day.dayOfMonth,
                    entry.day.monthValue,
                    entry.day.year,
                ),
            ).append(' ')
            append(monthNames(entry.day.month))
        }
        return q.split(Regex("\\s+")).all { token -> token in haystack }
    }

    private fun monthNames(month: Month): String {
        val standalone = month.getDisplayName(TextStyle.FULL_STANDALONE, ruLocale).lowercase(ruLocale)
        val inflected = month.getDisplayName(TextStyle.FULL, ruLocale).lowercase(ruLocale)
        return "$standalone $inflected"
    }

    private fun persist(list: List<BookmarkEntry>) {
        val arr = JSONArray()
        for (item in list) {
            val filesArr = JSONArray()
            item.pages.forEach { filesArr.put(it.name) }
            val obj = JSONObject()
                .put("id", item.id)
                .put("day", item.day.toString())
                .put("comment", item.comment)
                .put("created_at", item.createdAt.toEpochMilli())
                .put("pages", filesArr)
            arr.put(obj)
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun read(): List<BookmarkEntry> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val dirBase = File(appContext.filesDir, "bookmarks")
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: continue
                    val day = runCatching { LocalDate.parse(obj.optString("day")) }.getOrNull() ?: continue
                    val comment = obj.optString("comment", "")
                    val createdAt = Instant.ofEpochMilli(obj.optLong("created_at", System.currentTimeMillis()))
                    val pagesArr = obj.optJSONArray("pages") ?: continue
                    val dir = File(dirBase, id)
                    val pages = ArrayList<File>(pagesArr.length())
                    for (j in 0 until pagesArr.length()) {
                        val name = pagesArr.optString(j)
                        if (name.isNotEmpty()) {
                            val file = File(dir, name)
                            if (file.exists()) pages += file
                        }
                    }
                    if (pages.isNotEmpty()) {
                        add(BookmarkEntry(id, day, comment, createdAt, pages))
                    }
                }
            }.sortedWith(compareByDescending<BookmarkEntry> { it.day }.thenByDescending { it.createdAt })
        }.getOrDefault(emptyList())
    }

    companion object {
        private val ruLocale: Locale = Locale.forLanguageTag("ru")
    }
}
