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


/**
 * Counts consecutive scheduled days completed by a habit.
 * Unscheduled days do not break a streak; a missed scheduled day does.
 * Dates are compared as local calendar dates, never as elapsed 24-hour blocks.
 */
internal fun calculateHabitStreak(
    habit: HabitEntity,
    completedDates: Set<java.time.LocalDate>,
    today: java.time.LocalDate,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
): Int {
    val createdOn = java.time.Instant.ofEpochMilli(habit.createdAt).atZone(zoneId).toLocalDate()
    val scheduledDays = habit.scheduledDays.split(",").map { it.trim() }.toSet()
    var cursor = today
    if (cursor.dayOfWeek.name in scheduledDays && cursor !in completedDates) {
        cursor = cursor.minusDays(1)
    }
    var streak = 0
    while (!cursor.isBefore(createdOn)) {
        if (cursor.dayOfWeek.name in scheduledDays) {
            if (cursor !in completedDates) break
            streak++
        }
        cursor = cursor.minusDays(1)
    }
    return streak
}
