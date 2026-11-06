# Spark Habit Tracker

Spark is a Persian, right-to-left Android habit tracker built with Kotlin, Jetpack Compose, Material 3, and Room.

## Product features

### Existing Spark foundation

- Create, edit, and delete habits with locally stored completion history.

### Spark 5.0 implementation in progress
- Dedicated task domain model and Room table, added through a non-destructive v3-to-v4 migration.
- Task creation, editing, completion, archive, and permanent deletion.
- Task categories, descriptions, tags, due dates, four priority levels, and search/filter views.
- Overdue detection based on ISO local dates; malformed dates are ignored safely rather than crashing the task list.
- Task repository boundary keeps input normalization and persistence rules outside Compose screens.
- Interactive month, week, and day calendar views with Persian Saturday-first week layout.
- Calendar agenda combines real scheduled habits, habit completion records, and tasks due on the selected date.
- Schedule-aware habit streak calculation: unscheduled days are skipped, missed scheduled days break the streak, and comparisons use local calendar dates.
- Unit tests for task deadline and priority rules.
- Preserve legacy task schedules, reminder-time metadata, enabled state, and completion timestamps during Room migrations.
- Date-based history with previous/next-day navigation.
- Seven-day statistics and current-month completion totals calculated from actual records and each habit's saved schedule.
- Animated daily progress core with bounded percentage calculations.
- XP and levels derived from completed habit-log records (10 XP per completed habit-day).
- Achievement milestones unlocked from actual completion records.
- A deterministic daily challenge based on the user's active habits.
- Rule-based weekly insights comparing the latest seven days with the previous seven days. No external AI service is claimed or required.
- Persisted appearance preferences: neon, dark, and minimal themes; violet, cyan, lime, and pink accent choices; reduced-motion preference.

## Data and privacy

- Data is stored locally in the Room database `spark_tracker.db`.
- No account, cloud sync, analytics SDK, or remote AI service is included.
- XP is calculated from completed log rows. Unchecking a habit removes that completion from the current XP total; no hidden points are minted.
- Daily challenges and insights are derived from current saved habit and completion data.
- The old Glance widget was a static sample rather than a functional data-connected widget. Scheduled reminder delivery was not implemented in the original app; legacy reminder-time values are retained as metadata but are not currently scheduled as notifications.

## Build and verification

Requirements: JDK 17, Gradle 8.5, Android SDK 34.

Run unit tests and build the debug APK:

```bash
gradle testDebugUnitTest assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs unit tests and assembles the debug APK on pushes to `main` and `master`, and on pull requests targeting the main branches. The APK is uploaded as `Spark-debug-apk`.

## Spark 5.0 status

Spark 5.0 is being developed on the dedicated `spark-5.0` branch, based on the latest existing Spark development branch so the current working product is retained. The original `main` branch is not modified. This branch is an active engineering build, not a completed release.

### Next engineering areas
- Expand tasks with recurrence, subtasks, reminders, and richer completion history.
- Add calendar, goals, focus sessions, data-backed analytics, and streak calculations.
- Add notification scheduling, real home-screen widgets, and backup/restore.
- Move remaining legacy UI/database code into clearer presentation, domain, and data packages.
- Add migration and interaction tests, then verify debug and release builds.

## Current limitations

- No cloud sync, sign-in, remote AI, or cross-device backup.
- No scheduled notification delivery yet. Existing reminder-time data is preserved.
- No live home-screen widget yet; the former widget was only a hard-coded visual sample.
- Reduced-motion currently shortens the main progress animation; not every Compose transition has a separate motion setting.
- CI validates compilation and unit tests. It does not replace installation and interaction testing on a physical Android device.
