package com.spark.habittracker

/**
 * Pure domain calculations shared by the UI and unit tests.
 * Completion rates are bounded so corrupt or stale counts cannot render invalid percentages.
 */
internal fun calculateCompletionRate(completedCount: Int, totalCount: Int): Float {
    if (totalCount <= 0) return 0f
    return completedCount.coerceIn(0, totalCount).toFloat() / totalCount.toFloat()
}

/** Ten experience points are awarded for each currently completed habit-day record. */
internal fun calculateExperience(completedLogCount: Int): Int =
    completedLogCount.coerceAtLeast(0) * 10

/** The first level starts at one and advances once per 100 XP. */
internal fun calculateLevel(experience: Int): Int =
    experience.coerceAtLeast(0) / 100 + 1
