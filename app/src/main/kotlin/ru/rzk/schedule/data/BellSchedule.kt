package ru.rzk.schedule.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Один урок в расписании звонков. */
data class Bell(val number: Int, val start: LocalTime, val end: LocalTime) {
    val duration: Duration get() = Duration.between(start, end)
}

/** Что происходит прямо сейчас относительно звонков. Чистая логика без Android — проверяется обычными тестами. */
sealed interface BellState {
    /** Воскресенье: звонков нет. */
    data class DayOff(val nextStart: LocalTime) : BellState

    /** Занятия ещё не начались. */
    data class BeforeClasses(val first: Bell, val remaining: Duration) : BellState

    /** Идёт урок. [progress] — от 0 до 1. */
    data class InLesson(
        val bell: Bell,
        val next: Bell?,
        val breakAfter: Duration?,
        val elapsed: Duration,
        val remaining: Duration,
        val progress: Float,
    ) : BellState

    /** Перемена между [after] и [next]. */
    data class InBreak(
        val after: Bell,
        val next: Bell,
        val total: Duration,
        val remaining: Duration,
        val progress: Float,
    ) : BellState

    /** Последний урок закончился. */
    data class Finished(val last: Bell, val firstTomorrow: LocalTime, val tomorrowIsMonday: Boolean) : BellState
}

object BellSchedule {
    /** Звонки колледжа: одинаковы с понедельника по субботу. */
    val all: List<Bell> = listOf(
        bell(1, 8, 30, 9, 15),
        bell(2, 9, 20, 10, 5),
        bell(3, 10, 15, 11, 0),
        bell(4, 11, 5, 11, 50),
        bell(5, 12, 0, 12, 45),
        bell(6, 12, 50, 13, 35),
        bell(7, 13, 45, 14, 30),
        bell(8, 14, 35, 15, 20),
        bell(9, 15, 30, 16, 15),
        bell(10, 16, 20, 17, 5),
        bell(11, 17, 15, 18, 0),
        bell(12, 18, 5, 18, 50),
    )

    const val SHORT_DAY = 8
    const val FULL_DAY = 12

    private fun bell(n: Int, sh: Int, sm: Int, eh: Int, em: Int) = Bell(n, LocalTime.of(sh, sm), LocalTime.of(eh, em))

    /** Первые [count] уроков (по умолчанию в таблице 8, по желанию — все 12). */
    fun lessons(count: Int): List<Bell> = all.take(count.coerceIn(1, all.size))

    /** Перемена после урока [bell] в списке [lessons] или null, если урок последний. */
    fun breakAfter(bell: Bell, lessons: List<Bell>): Duration? {
        val next = lessons.getOrNull(lessons.indexOf(bell) + 1) ?: return null
        return Duration.between(bell.end, next.start)
    }

    /**
     * Состояние на момент [now] для дня из [count] уроков. Время — по часам колледжа (МСК), его подаёт вызывающий код.
     * Уроки: [начало, конец); перемена: [конец урока, начало следующего).
     */
    fun state(now: LocalDateTime, count: Int = FULL_DAY): BellState {
        val lessons = lessons(count)
        val first = lessons.first()
        if (now.dayOfWeek == DayOfWeek.SUNDAY) return BellState.DayOff(first.start)

        val t = now.toLocalTime()
        if (t < first.start) return BellState.BeforeClasses(first, Duration.between(t, first.start))

        for ((index, lesson) in lessons.withIndex()) {
            val next = lessons.getOrNull(index + 1)
            if (t >= lesson.start && t < lesson.end) {
                val elapsed = Duration.between(lesson.start, t)
                return BellState.InLesson(
                    bell = lesson,
                    next = next,
                    breakAfter = next?.let { Duration.between(lesson.end, it.start) },
                    elapsed = elapsed,
                    remaining = Duration.between(t, lesson.end),
                    progress = ratio(elapsed, lesson.duration),
                )
            }
            if (next != null && t >= lesson.end && t < next.start) {
                val total = Duration.between(lesson.end, next.start)
                return BellState.InBreak(
                    after = lesson,
                    next = next,
                    total = total,
                    remaining = Duration.between(t, next.start),
                    progress = ratio(Duration.between(lesson.end, t), total),
                )
            }
        }
        val tomorrowIsSunday = now.dayOfWeek.plus(1) == DayOfWeek.SUNDAY
        return BellState.Finished(lessons.last(), first.start, tomorrowIsMonday = tomorrowIsSunday)
    }

    private fun ratio(part: Duration, whole: Duration): Float =
        (part.seconds.toFloat() / whole.seconds.toFloat()).coerceIn(0f, 1f)

    // ---- форматирование ------------------------------------------------------------------------

    private fun two(n: Long) = n.toString().padStart(2, '0')

    /** «08:30». */
    fun hm(t: LocalTime): String = two(t.hour.toLong()) + ":" + two(t.minute.toLong())

    /** «08:30 – 09:15». */
    fun range(b: Bell): String = hm(b.start) + " – " + hm(b.end)

    /** Обратный отсчёт: «24:31» (меньше часа) или «1 ч 12 мин». */
    fun countdown(d: Duration): String {
        val total = d.seconds.coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) "$hours ч ${two(minutes)} мин" else "${two(minutes)}:${two(seconds)}"
    }

    /** «45 мин». */
    fun minutes(d: Duration): String = "${d.toMinutes()} мин"
}
