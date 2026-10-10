package ru.rzk.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.rzk.schedule.data.BellSchedule
import ru.rzk.schedule.data.BellState
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class BellScheduleTest {
    // 2026-10-07 — среда, 2026-10-04 — воскресенье, 2026-10-10 — суббота.
    private fun at(day: Int, h: Int, m: Int, s: Int = 0) = LocalDateTime.of(2026, 10, day, h, m, s)

    @Test fun tableMatchesTheBellCard() {
        assertEquals(12, BellSchedule.all.size)
        assertEquals("08:30 – 09:15", BellSchedule.range(BellSchedule.all[0]))
        assertEquals("16:20 – 17:05", BellSchedule.range(BellSchedule.all[9]))
        assertEquals("18:05 – 18:50", BellSchedule.range(BellSchedule.all[11]))
        assertTrue(BellSchedule.all.all { it.duration.toMinutes() == 45L })
    }

    @Test fun beforeClasses() {
        val s = BellSchedule.state(at(7, 7, 30), 8) as BellState.BeforeClasses
        assertEquals(3600, s.remaining.seconds)
    }

    @Test fun midLessonProgress() {
        val s = BellSchedule.state(at(7, 10, 37, 30), 8) as BellState.InLesson  // урок 3, 22.5 из 45 мин
        assertEquals(3, s.bell.number)
        assertEquals(0.5f, s.progress, 0.001f)
        assertEquals(22 * 60 + 30, s.remaining.seconds)
    }

    @Test fun lessonBoundariesAreHalfOpen() {
        assertEquals(1, (BellSchedule.state(at(7, 8, 30), 8) as BellState.InLesson).bell.number)
        assertTrue(BellSchedule.state(at(7, 9, 15), 8) is BellState.InBreak)
        assertEquals(2, (BellSchedule.state(at(7, 9, 20), 8) as BellState.InLesson).bell.number)
    }

    @Test fun breaks() {
        val s = BellSchedule.state(at(7, 10, 10), 8) as BellState.InBreak
        assertEquals(2, s.after.number); assertEquals(3, s.next.number)
        assertEquals(10, s.total.toMinutes()); assertEquals(5 * 60, s.remaining.seconds)
        assertEquals(0.5f, s.progress, 0.001f)
    }

    @Test fun eightLessonDayEndsAfterLesson8() {
        assertTrue(BellSchedule.state(at(7, 15, 20), 8) is BellState.Finished)
        assertEquals(9, (BellSchedule.state(at(7, 15, 40), 12) as BellState.InLesson).bell.number)
        assertTrue(BellSchedule.state(at(7, 19, 0), 12) is BellState.Finished)
    }

    @Test fun lastLessonHasNoBreakAfterIt() {
        val s = BellSchedule.state(at(7, 15, 0), 8) as BellState.InLesson
        assertEquals(8, s.bell.number); assertEquals(null, s.next); assertEquals(null, s.breakAfter)
    }

    @Test fun sundayIsDayOffAndSaturdayWorks() {
        assertTrue(BellSchedule.state(at(4, 10, 0), 8) is BellState.DayOff)
        assertTrue(BellSchedule.state(at(10, 10, 0), 8) is BellState.InLesson)
        assertTrue((BellSchedule.state(at(10, 20, 0), 8) as BellState.Finished).tomorrowIsMonday)
        assertTrue(!(BellSchedule.state(at(7, 20, 0), 8) as BellState.Finished).tomorrowIsMonday)
    }

    @Test fun formatting() {
        assertEquals("24:31", BellSchedule.countdown(Duration.ofSeconds(24 * 60 + 31)))
        assertEquals("1 ч 12 мин", BellSchedule.countdown(Duration.ofMinutes(72)))
        assertEquals("08:05", BellSchedule.hm(LocalTime.of(8, 5)))
    }
}
