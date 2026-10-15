package de.privat.kalorienzaehler.domain.format

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GermanDateTest {

    @Test
    fun parsesGermanDates() {
        assertEquals(LocalDate.of(1990, 10, 13), GermanDate.parse("13.10.1990"))
        assertEquals(LocalDate.of(1990, 2, 1), GermanDate.parse("1.2.1990"))
        assertEquals(LocalDate.of(2000, 2, 29), GermanDate.parse(" 29.02.2000 "))
    }

    @Test
    fun rejectsEverythingElse() {
        listOf("", "13.10.90", "1990-10-13", "13/10/1990", "31.02.2000", "29.02.2001", "00.01.2000", "13.13.2000", "a.b.cccc", "13.10.1990.")
            .forEach { assertNull(GermanDate.parse(it), "expected null for \"$it\"") }
    }

    @Test
    fun roundTripsWithTheFormatter() {
        val date = LocalDate.of(2026, 3, 5)
        assertEquals(date, GermanDate.parse(GermanFormat.date(date)))
    }
}
