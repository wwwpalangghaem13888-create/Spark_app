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
    fun habitReminderFindsNextSelectedLocalDay() {
        val habit = HabitEntity(
            id = 4,
            title = "Read",
            note = "",
            category = "Learning",
            scheduledDays = "SATURDAY,MONDAY",
            reminderTime = "08:00"
        )
        val fridayEvening = java.time.LocalDate.parse("2026-11-06")
            .atTime(20, 0).atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
        val saturdayMorning = java.time.LocalDate.parse("2026-11-07")
            .atTime(9, 0).atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(
            java.time.LocalDate.parse("2026-11-07").atTime(8, 0).atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli(),
            nextHabitReminder(habit, fridayEvening, java.time.ZoneId.of("UTC"))
        )
        assertEquals(
            java.time.LocalDate.parse("2026-11-09").atTime(8, 0).atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli(),
            nextHabitReminder(habit, saturdayMorning, java.time.ZoneId.of("UTC"))
        )
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
    fun analyticsUsesSavedRecordsAndScheduledHabitOpportunities() {
        val start = LocalDate.parse("2026-11-02")
        val end = LocalDate.parse("2026-11-03")
        val habit = HabitEntity(
            id = 1,
            title = "Read",
            note = "",
            category = "Learning",
            createdAt = start.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli(),
            scheduledDays = "MONDAY,TUESDAY"
        )
        val logs = listOf(
            HabitLogEntity(habitId = 1, date = "2026-11-02", isCompleted = true),
            HabitLogEntity(habitId = 1, date = "2026-11-03", isCompleted = false),
            HabitLogEntity(habitId = 1, date = "2026-11-04", isCompleted = true)
        )
        val taskTime = start.atTime(10, 0).atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 2, title = "Study", category = "School", completed = true, completedAt = taskTime),
            TaskEntity(id = 3, title = "Later", category = "School", completed = false)
        )
        val sessions = listOf(FocusSessionEntity(startedAt = taskTime, plannedDurationSeconds = 1500, actualDurationSeconds = 900, completed = true))
        val report = buildAnalyticsReport(habits, logs, tasks, sessions, start, end, java.time.ZoneId.of("UTC"))

        assertEquals(1, report.completedHabits)
        assertEquals(2, report.scheduledHabitOpportunities)
        assertEquals(1, report.completedTasks)
        assertEquals(900L, report.focusSeconds)
        assertEquals(start, report.bestDay)
        assertEquals(0.5f, report.habitCompletionRate, 0.0001f)
        assertEquals(1, report.categoryTaskCounts["School"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun analyticsRejectsInvertedDateRange() {
        buildAnalyticsReport(emptyList(), emptyList(), emptyList(), emptyList(),
            LocalDate.parse("2026-11-07"), LocalDate.parse("2026-11-06"))
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
