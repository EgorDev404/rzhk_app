package ru.rzk.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.rzk.schedule.data.Naming
import java.time.LocalDate

class NamingTest {
    private val thu = LocalDate.of(2026, 10, 1)   // четверг
    private val wed = LocalDate.of(2026, 9, 30)   // среда
    private val base = "https://rzn-jd62.gosuslugi.ru/netcat_files/userfiles"

    @Test fun standardAndVersionedNamesAreCandidates() {
        val urls = Naming.candidates(thu).map { it.url }
        assertTrue("$base/Chetverg_01.10.pdf" in urls)
        assertTrue("$base/Chetverg_01.10_1.pdf" in urls)   // реальный пример: заменённый файл
        assertEquals("$base/Chetverg_01.10_3.pdf", urls.first())  // новейшая версия — первой
        assertEquals(urls.size, urls.toSet().size)
    }

    @Test fun matchesRealWorldName() {
        val links = listOf("$base/Chetverg_01.10_1.pdf", "$base/Sreda_30.09.pdf")
        assertEquals(listOf("$base/Chetverg_01.10_1.pdf"), Naming.matchLinks(thu, links))
    }

    @Test fun matchesWeirdNames() {
        val links = listOf(
            "$base/Raspisanie_na_Chetverg_01-10.pdf",
            "$base/%D0%A7%D0%B5%D1%82%D0%B2%D0%B5%D1%80%D0%B3_01.10.2026.pdf",
            "$base/raspisanie 01.10.26.pdf",
        )
        assertEquals(3, Naming.matchLinks(thu, links).size)
    }

    @Test fun rejectsWrongDayYearOrWeekday() {
        assertTrue(Naming.matchLinks(thu, listOf("$base/Chetverg_02.10.pdf")).isEmpty())
        assertTrue(Naming.matchLinks(thu, listOf("$base/Chetverg_01.10.2025.pdf")).isEmpty())
        assertTrue(Naming.matchLinks(thu, listOf("$base/Sreda_01.10.pdf")).isEmpty())
    }

    @Test fun higherVersionWins() {
        val links = listOf("$base/Chetverg_01.10.pdf", "$base/Chetverg_01.10_2.pdf", "$base/Chetverg_01.10_1.pdf")
        assertEquals("$base/Chetverg_01.10_2.pdf", Naming.matchLinks(thu, links).first())
    }

    @Test fun twoDigitSuffixIsVersionNotYear() {
        val links = listOf("$base/Chetverg_01.10_12.pdf")
        assertEquals(links, Naming.matchLinks(thu, links))
    }

    @Test fun extractsLinksAndPages() {
        val html = """<a href="/raspisanie/">Расписание занятий</a><a href="/news">Новости</a>
            <a href='/netcat_files/userfiles/Sreda_30.09.pdf'>файл</a><a href="https://other.ru/x.pdf">чужой</a>"""
        assertEquals(listOf("https://rzn-jd62.gosuslugi.ru/raspisanie/"),
            Naming.extractSchedulePages(Naming.SITE_ROOT, html))
        assertEquals(listOf("https://rzn-jd62.gosuslugi.ru/netcat_files/userfiles/Sreda_30.09.pdf"),
            Naming.extractPdfLinks(Naming.SITE_ROOT, html))
        assertTrue(Naming.matchLinks(wed, Naming.extractPdfLinks(Naming.SITE_ROOT, html)).isNotEmpty())
    }
}
