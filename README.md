# SPARK 5.0

A Persian-first, offline-capable Android productivity application built with Kotlin, Jetpack Compose, Material 3 and Room.

## Implemented on the `spark-5.0` branch

### Tasks
- Create, edit, duplicate, complete, archive, restore and permanently delete tasks.
- Descriptions, categories, tags, four priority levels, due dates, start/end times and search/filter.
- Daily, weekly, weekdays-only and monthly recurrence with calendar-date handling and duplicate-occurrence protection.
- Subtasks and checklists with persistent completion state.
- Task completion history; completion and history changes are stored transactionally.
- Optional task reminders.

### Habits and calendar
- Habit creation with custom selected weekdays and a local reminder time.
- Daily recurring habit reminders based on selected days.
- Habit history and schedule-aware streak calculation.
- Month, week and day calendar views, Saturday-first week layout and Persian-calendar labels.
- Task and habit activity shown from saved records. Future habit check-offs are disabled.

### Focus and goals
- Persisted focus sessions with selectable 25, 50 or 90 minute durations.
- Timer recovery from saved timestamps after app backgrounding or process recreation.
- Pause, resume and finish actions with stored session history.
- Measurable goals linked to tasks, with progress calculated from completed linked tasks.

### Analytics, XP and insights
- Interactive 7, 30 and 90 day analytics periods.
- Habit opportunities, completed tasks, focus duration, daily activity and category summaries derived from local records.
- XP for completed habit-days and unique task completions per task/day, avoiding repeat-toggle reward inflation.
- Levels, milestones, daily challenge and deterministic local weekly insights.

### Reminders and widgets
- Android reminder notifications with a 10-minute snooze action.
- Inexact alarm scheduling using `setAndAllowWhileIdle`; delivery is subject to Android battery and background restrictions. Exact-alarm delivery is not claimed.
- Habit reminders reschedule for the next selected local weekday.
- One-off task reminders are disabled after completion.
- Reboot recovery reschedules enabled future reminders.
- A real Android home-screen widget displays up to three open tasks and supports quick task completion.

### Backup, restore and appearance
- Versioned local JSON backup includes habits, habit logs, tasks, subtasks, task history, goals, goal links, focus sessions, reminders and appearance settings.
- Validated merge-only restore. The importer checks format, version, dates and references before writing in a transaction; it does not clear the existing database.
- CSV export for tasks with quoted/escaped text.
- Dark/neon, minimal and light appearance options; violet, cyan, lime and pink accent choices; reduced-motion preference.
- Persian RTL interface and English-safe date/data storage.

## Data safety and privacy

- Local database name: `spark_tracker.db`.
- Room schema migrations are additive from the existing schema through version 6; no destructive fallback migration is configured.
- Imported records are validated before transactional merge.
- No sign-in, cloud synchronization, remote AI or analytics SDK is required. Core features work offline.
- No API keys or service credentials are embedded in the app.

## Build

Requirements: JDK 17, Gradle 8.5 and Android SDK 34.

```bash
gradle testDebugUnitTest assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds and uploads the debug APK as `Spark-debug-apk` for pushes to the configured development branches. Check the run for the exact commit before treating an APK as current.

## Not yet complete or not yet verified

This branch is a substantial implementation step, but it is not yet a fully production-qualified release.

- The presentation/database code is not yet fully split into ViewModel, domain, data and DI modules; MainActivity still contains legacy database/theme/application wiring.
- Navigation is Compose tab state, not a dedicated Navigation Compose graph.
- Goals currently link tasks in the UI; habit linking, milestone editing, long-term planning workflows and goal history UI remain incomplete.
- Focus is a persisted countdown timer; stopwatch mode, configurable short/long breaks, session goals and ambient audio are not implemented.
- The widget is task-focused; dedicated habit, daily-progress and focus widgets remain incomplete.
- Reminder delivery is inexact and depends on OS permission/battery behavior. Quiet hours, notification preferences, notification history and per-reminder editing UI remain incomplete.
- Analytics currently emphasizes habit/task/focus totals. Yearly comparisons, best category/day analysis and a full heatmap are not all surfaced in the UI.
- Gamification has basic XP, levels, milestones and a daily challenge; a full weekly challenge and personal-record system remain incomplete.
- Backup restore merges by IDs; a guided conflict-resolution UI and CSV import are not implemented.
- Cloud sync is intentionally not enabled without a secure backend and user-controlled configuration.
- Migration tests on real legacy database fixtures, Android UI tests, performance profiling, Baseline Profiles and device-level QA remain to be completed.
- Release APK signing, R8 validation and a production release build have not yet been verified.

## Branch safety

Development is isolated on `spark-5.0`. The original `main` branch has not been overwritten or merged by this work. Do not distribute a debug APK as a production release.
