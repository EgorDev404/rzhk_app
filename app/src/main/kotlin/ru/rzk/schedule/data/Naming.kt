package ru.rzk.schedule.data

import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Как колледж называет файлы с расписанием и как мы их находим.
 *
 * 1. «Быстрый путь» — подбор по шаблонам имён (+ версии `_1`, `_2`, `_3`: так выглядят заменённые файлы).
 * 2. «Запасной путь» — читаем ссылки на PDF со страниц сайта и выбираем подходящую по дате и дню недели,
 *    как бы имя ни было записано.
 *
 * Весь код здесь — чистая логика без Android, поэтому её можно проверять обычными тестами.
 */
object Naming {
    const val SITE_ROOT = "https://rzn-jd62.gosuslugi.ru/"
    const val BASE_URL = "https://rzn-jd62.gosuslugi.ru/netcat_files/userfiles"
    const val MAX_VERSION = 3

    private val latin: Map<DayOfWeek, List<String>> = mapOf(
        DayOfWeek.MONDAY to listOf("Ponedelnik"),
        DayOfWeek.TUESDAY to listOf("Vtornik"),
        DayOfWeek.WEDNESDAY to listOf("Sreda"),
        DayOfWeek.THURSDAY to listOf("Chetverg"),
        DayOfWeek.FRIDAY to listOf("Pyatnitsa"),
        DayOfWeek.SATURDAY to listOf("Subbota"),
        DayOfWeek.SUNDAY to listOf("Voskresenie"),
    )
    private val russianCap: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "Понедельник",
        DayOfWeek.TUESDAY to "Вторник",
        DayOfWeek.WEDNESDAY to "Среда",
        DayOfWeek.THURSDAY to "Четверг",
        DayOfWeek.FRIDAY to "Пятница",
        DayOfWeek.SATURDAY to "Суббота",
        DayOfWeek.SUNDAY to "Воскресенье",
    )
    private val russianStems: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "понедельник",
        DayOfWeek.TUESDAY to "вторник",
        DayOfWeek.WEDNESDAY to "сред",
        DayOfWeek.THURSDAY to "четверг",
        DayOfWeek.FRIDAY to "пятниц",
        DayOfWeek.SATURDAY to "суббот",
        DayOfWeek.SUNDAY to "воскресен",
    )

    /** Поля: {day} {day_ru} {dd} {mm} {yyyy} {yy}. */
    val templates: List<String> = listOf(
        "{day}_{dd}.{mm}.pdf",
        "{day}_{dd}.{mm}.{yyyy}.pdf",
        "{day}_{dd}.{mm}.{yy}.pdf",
        "{day_ru}_{dd}.{mm}.pdf",
    )

    data class Candidate(val template: String, val url: String)

    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun fill(template: String, day: LocalDate, latinName: String): String = template
        .replace("{day_ru}", russianCap.getValue(day.dayOfWeek))
        .replace("{day}", latinName)
        .replace("{dd}", two(day.dayOfMonth))
        .replace("{mm}", two(day.monthValue))
        .replace("{yyyy}", day.year.toString())
        .replace("{yy}", two(day.year % 100))

    private fun encode(name: String): String = URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    /**
     * Адреса-кандидаты по убыванию вероятности. Для основного шаблона сначала идут версии
     * `_3`, `_2`, `_1` и только потом исходное имя: новейшая версия важнее старой.
     */
    fun candidates(day: LocalDate, preferredTemplate: String? = null): List<Candidate> {
        val ordered = buildList {
            if (preferredTemplate != null && preferredTemplate in templates) add(preferredTemplate)
            addAll(templates.filter { it != preferredTemplate })
        }
        val seen = LinkedHashSet<String>()
        val result = ArrayList<Candidate>()
        ordered.forEachIndexed { index, template ->
            val variants = if (index == 0 && MAX_VERSION > 0 && template.endsWith(".pdf")) {
                (MAX_VERSION downTo 1).map { template.removeSuffix(".pdf") + "_$it.pdf" } + template
            } else {
                listOf(template)
            }
            for (variant in variants) {
                for (name in latin.getValue(day.dayOfWeek)) {
                    val url = "$BASE_URL/" + encode(fill(variant, day, name))
                    if (seen.add(url)) result += Candidate(template, url)
                }
            }
        }
        return result
    }

    // ---- «понимание» нестандартных имён --------------------------------------------------------

    private val dateRe = Regex("""(?<!\d)(\d{1,2})[._\-/ ](\d{1,2})(?:[._\-/ ](\d{2,4}))?(?!\d)""")
    private val suffixRe = Regex("""[_\-\s(](\d{1,2})\)?$""")

    private fun weekdayHits(text: String): Set<DayOfWeek> = DayOfWeek.values().filter { weekday ->
        val names = latin.getValue(weekday).map { it.lowercase() } + russianStems.getValue(weekday)
        names.any { text.contains(it) }
    }.toSet()

    /**
     * Ссылки на PDF, подходящие по имени к дате; самые вероятные — первыми.
     * Понимает разные разделители («01.10», «01-10», «01_10»), год из 2 и 4 цифр, номер версии
     * в конце («_1»), русские и латинские названия дней. Файл с другим днём недели или годом отбрасывается.
     * Среди подходящих выигрывает больший номер версии.
     */
    fun matchLinks(day: LocalDate, links: List<String>): List<String> {
        class Scored(val version: Int, val bonus: Int, val order: Int, val url: String)

        val scored = ArrayList<Scored>()
        links.forEachIndexed { order, url ->
            val rawName = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
            var stem = try {
                URLDecoder.decode(rawName, "UTF-8").lowercase()
            } catch (_: IllegalArgumentException) {
                return@forEachIndexed
            }
            stem = stem.removeSuffix(".pdf")

            var found = false
            var version = 0
            for (m in dateRe.findAll(stem)) {
                if (m.groupValues[1].toInt() != day.dayOfMonth || m.groupValues[2].toInt() != day.monthValue) continue
                val tail = m.groupValues[3]
                var v = 0
                if (tail.isNotEmpty()) {
                    val n = tail.toInt()
                    when {
                        tail.length == 4 -> if (n != day.year) continue
                        n in 20..39 -> if (n != day.year % 100) continue
                        else -> v = n // «…_12» — номер версии, а не год
                    }
                }
                if (v == 0) {
                    suffixRe.find(stem.substring(m.range.last + 1))?.let { v = it.groupValues[1].toInt() }
                }
                found = true
                version = v
                break
            }
            if (!found) return@forEachIndexed

            val hits = weekdayHits(stem)
            if (hits.isNotEmpty() && day.dayOfWeek !in hits) return@forEachIndexed // другой день недели
            scored += Scored(version, if (day.dayOfWeek in hits) 0 else 1, order, url)
        }
        return scored.sortedWith(compareBy({ -it.version }, { it.bonus }, { it.order })).map { it.url }
    }

    // ---- разбор HTML страниц -------------------------------------------------------------------

    private val hrefRe = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val anchorRe = Regex(
        """<a\b[^>]*?href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val tagRe = Regex("""<[^>]+>""")

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&#38;", "&").trim()

    private fun resolve(base: String, href: String): String? = try {
        URL(URL(base), href).toString()
    } catch (_: Exception) {
        null
    }

    private fun sameHost(a: String, b: String) = try {
        URL(a).host.equals(URL(b).host, ignoreCase = true)
    } catch (_: Exception) {
        false
    }

    /** Все ссылки на PDF внутри страницы (только с того же сайта). */
    fun extractPdfLinks(pageUrl: String, html: String): List<String> =
        hrefRe.findAll(html).mapNotNull { m ->
            val href = unescape(m.groupValues[1])
            if (!href.substringBefore('?').substringBefore('#').lowercase().endsWith(".pdf")) return@mapNotNull null
            resolve(pageUrl, href)?.takeIf { sameHost(it, pageUrl) }
        }.distinct().toList()

    /** Страницы, похожие на раздел «Расписание» (ищем на главной по тексту ссылки и адресу). */
    fun extractSchedulePages(rootUrl: String, html: String, limit: Int = 5): List<String> {
        val pages = LinkedHashSet<String>()
        for (m in anchorRe.findAll(html)) {
            val href = unescape(m.groupValues[1])
            val blob = (href + " " + tagRe.replace(m.groupValues[2], " ")).lowercase()
            if (!(blob.contains("распис") || blob.contains("raspis"))) continue
            if (href.substringBefore('?').lowercase().endsWith(".pdf")) continue
            resolve(rootUrl, href)?.takeIf { sameHost(it, rootUrl) }?.let { pages += it }
            if (pages.size >= limit) break
        }
        return pages.toList()
    }
}
