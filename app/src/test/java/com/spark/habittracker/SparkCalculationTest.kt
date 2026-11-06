package com.spark.habittracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SparkCalculationTest {
    @Test fun emptyHabitListHasZeroProgress() {
        assertEquals(0f, calculateCompletionRate(0, 0), 0.0001f)
    }

    @Test fun progressUsesCompletedOverTotal() {
        assertEquals(0.5f, calculateCompletionRate(2, 4), 0.0001f)
    }

    @Test fun progressNeverExceedsOneHundredPercent() {
        assertEquals(1f, calculateCompletionRate(9, 4), 0.0001f)
    }

    @Test fun negativeCompletionCountCannotCreateNegativeProgress() {
        assertEquals(0f, calculateCompletionRate(-2, 4), 0.0001f)
    }

    @Test fun experienceComesOnlyFromCompletedLogs() {
        assertEquals(30, calculateExperience(3))
        assertEquals(0, calculateExperience(-3))
    }

    @Test fun levelStartsAtOneAndAdvancesEveryHundredXp() {
        assertEquals(1, calculateLevel(0))
        assertEquals(1, calculateLevel(99))
        assertEquals(2, calculateLevel(100))
        assertEquals(3, calculateLevel(200))
    }
    @Test fun habitStreakSkipsUnscheduledDaysButBreaksOnMissedScheduledDays() {
        val today = LocalDate.of(2026, 11, 6) // Friday
        val habit = HabitEntity(
            id = 1,
            title = "Study",
            note = "",
            category = "Study",
            createdAt = today.minusDays(8).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            scheduledDays = "MONDAY,WEDNESDAY,FRIDAY"
        )
        val completed = setOf(
            today,
            today.minusDays(2), // Wednesday
            today.minusDays(4)  // Monday
        )
        assertEquals(3, calculateHabitStreak(habit, completed, today, ZoneId.of("UTC")))
        assertEquals(1, calculateHabitStreak(habit, setOf(today.minusDays(2)), today, ZoneId.of("UTC")))
    }

    @Test fun missedTodayDoesNotErasePreviousScheduledStreakUntilDayEnds() {
        val today = LocalDate.of(2026, 11, 6)
        val habit = HabitEntity(
            id = 2,
            title = "Read",
            note = "",
            category = "Reading",
            createdAt = today.minusDays(7).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            scheduledDays = "THURSDAY,FRIDAY"
        )
        assertEquals(1, calculateHabitStreak(habit, setOf(today.minusDays(1)), today, ZoneId.of("UTC")))
    }

    @Test fun aMissedPreviousScheduledDayBreaksTheStreak() {
        val today = LocalDate.of(2026, 11, 6)
        val habit = HabitEntity(
            id = 3,
            title = "Practice",
            note = "",
            category = "Study",
            createdAt = today.minusDays(10).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            scheduledDays = "MONDAY,WEDNESDAY,FRIDAY"
        )
        // Friday is still in progress; Wednesday was missed, so Monday cannot extend the streak.
        assertEquals(0, calculateHabitStreak(habit, setOf(today.minusDays(4)), today, ZoneId.of("UTC")))
    }


    @Test fun onlyCompletedLogsOnOrBeforeTodayEarnExperience() {
        val today = LocalDate.of(2026, 11, 6)
        val logs = listOf(
            HabitLogEntity(id = 1, habitId = 1, date = "2026-11-05", isCompleted = true),
            HabitLogEntity(id = 2, habitId = 1, date = "2026-11-06", isCompleted = true),
            HabitLogEntity(id = 3, habitId = 1, date = "2026-11-07", isCompleted = true),
            HabitLogEntity(id = 4, habitId = 1, date = "2026-11-04", isCompleted = false),
            HabitLogEntity(id = 5, habitId = 1, date = "not-a-date", isCompleted = true)
        )
        val earned = completedHabitLogsThroughDate(logs, today)
        assertEquals(listOf(1L, 2L), earned.map { it.id })
        assertEquals(20, calculateExperience(earned.size))
    }


}
