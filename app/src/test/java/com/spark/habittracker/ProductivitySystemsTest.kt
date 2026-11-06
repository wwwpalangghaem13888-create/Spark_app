package com.spark.habittracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProductivitySystemsTest {
    @Test
    fun recurrenceRulesAdvanceCalendarDatesCorrectly() {
        assertEquals(LocalDate.parse("2026-11-07"), nextRecurringDate("DAILY", LocalDate.parse("2026-11-06")))
        assertEquals(LocalDate.parse("2026-11-13"), nextRecurringDate("WEEKLY", LocalDate.parse("2026-11-06")))
        assertEquals(LocalDate.parse("2026-11-09"), nextRecurringDate("WEEKDAYS", LocalDate.parse("2026-11-06")))
        assertEquals(LocalDate.parse("2027-02-28"), nextRecurringDate("MONTHLY", LocalDate.parse("2027-01-31")))
        assertEquals(null, nextRecurringDate("UNKNOWN", LocalDate.parse("2026-11-06")))
        assertEquals(null, nextRecurringDate(null, LocalDate.parse("2026-11-06")))
    }

    @Test
    fun focusTimerRecoversAcrossBackgroundAndPause() {
        val running = FocusSessionEntity(
            startedAt = 1_000_000L,
            plannedDurationSeconds = 1_500L
        )
        assertEquals(1_000L, focusRemainingSeconds(running, 1_500_000L))

        val paused = running.copy(pausedAt = 1_200_000L, accumulatedPausedSeconds = 60L)
        assertEquals(1_360L, focusRemainingSeconds(paused, 1_900_000L))
    }

    @Test
    fun goalProgressIsBoundedAndRejectsInvalidTargets() {
        assertEquals(0.5f, goalProgressRatio(5, 10.0), 0.0001f)
        assertEquals(1f, goalProgressRatio(15, 10.0), 0.0001f)
        assertEquals(0f, goalProgressRatio(-5, 10.0), 0.0001f)
        assertEquals(0f, goalProgressRatio(5, 0.0), 0.0001f)
        assertEquals(0f, goalProgressRatio(5, Double.NaN), 0.0001f)
    }

    @Test
    fun csvExportEscapesQuotesAndCommas() {
        val csv = exportTasksCsv(
            listOf(
                TaskEntity(
                    id = 7,
                    title = "Read, learn",
                    description = "Say \"hello\"",
                    category = "Study",
                    tags = "one,two"
                )
            )
        )
        assertTrue(csv.contains("\"Read, learn\""))
        assertTrue(csv.contains("\"Say \"\"hello\"\"\""))
        assertTrue(csv.contains("\"one,two\""))
        assertFalse(csv.contains("Read, learn,"))
    }
}
