package com.spark.habittracker

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
