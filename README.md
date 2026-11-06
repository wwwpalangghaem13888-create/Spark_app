# SPARK 5.0

A Persian-first, offline-capable Android productivity application built with Kotlin, Jetpack Compose, Material 3 and Room.

## Implemented on the `spark-5.0` branch

### Task management
- Create, edit, duplicate, complete, archive, restore and permanently delete tasks.
- Descriptions, categories, tags, four priority levels, due dates, start/end times and search/filter views.
- Daily, weekly, weekdays-only and monthly recurrence using local calendar dates.
- Subtasks/checklists with persisted completion state.
- Task completion history with transactional state changes.
- Optional one-off task reminders; reminders are disabled when the task is completed or archived.

### Habits and calendar
- Habit creation with selected weekdays and a local reminder time.
- Recurring habit reminders on the selected local weekdays.
- Schedule-aware streak calculations and saved completion history.
- Monthly, weekly and daily calendar views; Saturday-first weeks and Persian-calendar date labels.
- Calendar agenda combines scheduled habits, stored habit logs and tasks due on the selected date.
- Quick task creation prefilled with the selected calendar date.
- Future habit check-offs are disabled.

### Focus and goals
- Persisted countdown sessions with selectable 25/50/90 minute focus durations, custom durations and 5/15 minute break presets.
- Stopwatch mode.
- Pause/resume and recovery from persisted timestamps after backgrounding or process recreation.
- Focus completion reminders and stored session history.
- Measurable goals with target dates, linked tasks and linked habits; progress is calculated from saved completions in the goal's date range.

### Analytics, XP and insights
- Interactive 7, 30, 90 and 365 day report periods.
- Habit opportunities, task completions, focus duration, daily activity and task-category summaries from local records.
- XP for completed habit-days and unique task completions per task/day, reducing repeated-toggle reward inflation.
- Levels, milestone achievements, daily and weekly activity challenges.
- Deterministic local weekly insights; no remote AI service is claimed.

### Reminders and widgets
- Android notifications with a 10-minute snooze action.
- Inexact alarm scheduling via `AlarmManager.setAndAllowWhileIdle`; delivery remains subject to Android power-management restrictions. Exact delivery is not claimed.
- Habit reminders reschedule for the next selected local weekday.
- One-off task and focus reminders are disabled after firing.
- Reboot recovery for enabled future reminders.
- A home-screen widget displays up to three open tasks and supports quick task completion.

### Backup, restore and customization
- Versioned JSON backup includes habits, habit logs, tasks, subtasks, task history, goals, goal links, focus sessions, reminders and appearance settings.
- Validated merge-only restore. The importer checks format, version, dates and record references before writing in a transaction; it does not clear the current database.
- CSV task export with quoting and spreadsheet-formula prefix protection.
- Neon/dark, minimal and light themes; violet, cyan, lime and pink accents; reduced-motion preference.
- Persian RTL interface and Persian-calendar display in the calendar.

## Data safety and privacy

- Local Room database: `spark_tracker.db`.
- Additive migrations from the existing schema through database version 6; no destructive fallback migration is configured.
- Restore merges by record IDs and runs in a database transaction.
- Core use does not require sign-in, internet, cloud sync or a remote AI service.
- No API keys or service credentials are embedded in the repository.

## Build

Requirements: JDK 17, Gradle 8.5 and Android SDK 34.

```bash
gradle testDebugUnitTest assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs unit tests and builds a debug APK for pushes to the configured branches. Check the workflow run for the exact commit before installing an artifact.

## Remaining work and release limitations

This is a substantially expanded engineering build, not yet a fully production-qualified commercial release.

- MainActivity still contains legacy database, theme and application wiring. Presentation, domain and data layers are not fully separated; ViewModels, dependency injection and a dedicated Navigation Compose graph remain to be completed.
- Task history is persisted but does not yet have a dedicated detailed history screen. Task recurrence has basic rules and occurrence de-duplication, but advanced exception editing and recurring reminder policies remain incomplete.
- Habit skip states, longest-streak records, full weekly/monthly heatmap views and habit performance comparisons remain incomplete.
- Goals support task/habit links and date-bounded progress, but goal editing, milestone workflows and goal-history UI remain incomplete.
- Focus stopwatch and countdown are implemented; ambient audio, configurable long-break cycles, session targets and advanced focus reports remain incomplete.
- The widget is task-focused. Separate habit-completion, daily-progress and focus-timer widgets remain incomplete.
- Reminder preferences, quiet hours, notification history and exact-alarm opt-in are not implemented. Alarm delivery is intentionally inexact.
- Analytics provides 7/30/90/365 day summaries, but full year-over-year comparisons and a dedicated habit heatmap are not implemented.
- Gamification includes XP, levels, milestones and daily/weekly challenges; a complete personal-record and achievement-history experience remains incomplete.
- Backup is local JSON merge/restore and task CSV export. CSV import, conflict-resolution UI and cloud synchronization are not implemented.
- English localization and LTR UI are not complete; the current interface is Persian RTL.
- Room migration tests against archived real-world database fixtures, Android UI/navigation tests, physical-device QA and performance profiling remain to be completed.
- R8/release optimization, Baseline Profiles, release signing and a production release APK have not been verified.

## Branch safety

Development is isolated on `spark-5.0`. The original `main` branch has not been overwritten or merged by this work. Do not distribute a debug APK as a production release.
