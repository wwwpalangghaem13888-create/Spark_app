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
        assertEquals(0, calculateHabitStreak(habit, setOf(today.minusDays(2)), today, ZoneId.of("UTC")))
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

}
