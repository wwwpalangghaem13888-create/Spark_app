package com.spark.habittracker

/**
 * Typed destinations keep navigation intent out of raw integer indexes.
 * The current bottom-navigation UI can migrate incrementally without changing
 * persisted data or the existing screen implementations.
 */
enum class SparkDestination(val route: String, val tabIndex: Int) {
    Dashboard("dashboard", 0),
    Tasks("tasks", 1),
    Calendar("calendar", 2),
    Analytics("analytics", 3),
    Achievements("achievements", 4),
    Goals("goals", 5),
    Focus("focus", 6),
    Backup("backup", 7);

    companion object {
        fun fromTab(index: Int): SparkDestination =
            entries.firstOrNull { it.tabIndex == index } ?: Backup

        fun fromRoute(route: String): SparkDestination? =
            entries.firstOrNull { it.route == route }
    }
}
