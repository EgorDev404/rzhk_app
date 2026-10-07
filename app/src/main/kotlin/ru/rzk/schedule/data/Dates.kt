package ru.rzk.schedule.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Даты по времени колледжа (Рязань, МСК), а не по часовому поясу телефона. */
object Dates {
    val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val ru: Locale = Locale.forLanguageTag("ru")

    fun today(): LocalDate = LocalDate.now(zone)

    fun weekday(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, ru)
    fun weekdayShort(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, ru)
    fun dayMonth(d: LocalDate): String =
        d.dayOfMonth.toString().padStart(2, '0') + "." + d.monthValue.toString().padStart(2, '0')

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /** «Сегодня», «Завтра», «Послезавтра» или день недели с большой буквы. */
    fun title(d: LocalDate, today: LocalDate = today()): String = when (d.toEpochDay() - today.toEpochDay()) {
        0L -> "Сегодня"
        1L -> "Завтра"
        2L -> "Послезавтра"
        else -> cap(weekday(d))
    }

    /** «Завтра (четверг, 01.10)» */
    fun full(d: LocalDate, today: LocalDate = today()): String {
        val rel = d.toEpochDay() - today.toEpochDay()
        return if (rel in 0L..2L) "${title(d, today)} (${weekday(d)}, ${dayMonth(d)})"
        else "${cap(weekday(d))}, ${dayMonth(d)}"
    }

    /** Для фраз вида «На завтра расписания нет». */
    fun accusative(d: LocalDate, today: LocalDate = today()): String = when (d.toEpochDay() - today.toEpochDay()) {
        0L -> "на сегодня"
        1L -> "на завтра"
        2L -> "на послезавтра"
        else -> "на ${weekday(d)}, ${dayMonth(d)}"
    }
}
