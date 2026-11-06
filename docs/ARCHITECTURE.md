# SPARK architecture

## Current direction

SPARK uses a deliberately incremental MVVM architecture. Existing screens and Room schema remain in place while persistence and state ownership move behind explicit boundaries.

- **Presentation:** Compose screens, typed SparkDestination identifiers, and SparkViewModel for task-list state and mutations. Goals and focus screens now depend on ProductivityRepository rather than receiving a Room DAO.
- **Domain-facing boundaries:** TaskRepository, HabitRepository, and ProductivityRepository. Goal creation validates input at the repository boundary and persists the goal plus its links in one Room transaction.
- **Data:** SparkDatabase, Room DAOs, and SparkDatabaseFactory. DAOs are injected into repositories and are not passed to goals/focus composables.
- **Dependency injection:** SparkAppContainer is the composition root, created once by SparkApplication. This manual DI approach avoids adding a runtime framework before the dependency graph justifies it.
- **Errors:** SparkError and SparkResult provide a stable error vocabulary; task ViewModel mutation failures are surfaced through a SharedFlow and displayed by the UI. Goal/focus error-state migration remains pending.

## Navigation

SparkDestination maps stable route names to the existing bottom-navigation destinations. This is a compatibility bridge, not a claim that a full Navigation Compose back stack has already replaced the current tab host.

## Database integrity and migration policy

- Room schema version is 7. Version 6 upgrades through the additive `MIGRATION_6_7`; older versions continue through the contiguous 1→2→3→4→5→6→7 chain.
- Never use destructive migration fallback. A migration failure must surface as an error and be fixed without dropping the user database.
- Focus sessions reference tasks with `ON DELETE SET NULL`; subtasks, task history, and goal links use cascading foreign keys. Reminder owner references are polymorphic, so SQLite triggers validate new owners and clean up reminders on owner deletion.
- Version 7 adds indexes for habit date lookup, task-history aggregation, active-goal ordering, focus-task lookup, and enabled-reminder scheduling.
- Backups retain format version 1. The exported `databaseVersion` is informational and optional on import, so older version-1 backups remain compatible.
- The Android instrumentation test creates a real version-6 SQLite file with representative linked rows, opens it through the production migration registry, verifies data preservation and `PRAGMA foreign_key_check`, then tests cascades, `SET NULL`, and reminder-owner validation.

## Migration strategy

1. Add forward-only Room migrations; never reset or destructively recreate a user database.
2. Move persistence declarations out of the Activity.
3. Route one vertical slice (tasks) through a ViewModel and repository.
4. Migrate habit, goals, focus, backup, and reminder flows one at a time.
5. Introduce Navigation Compose only when deep links/back-stack behavior is designed and tested.
6. Consider Hilt only if the manual container becomes difficult to maintain.

## Known remaining work

- Move remaining direct DAO access from MainActivity and Compose screens (settings, reminders, backup, analytics, and task side effects) into ViewModels/repositories.
- Add dedicated GoalsViewModel and FocusViewModel with explicit UI state and error events.
- Complete transactional and lifecycle tests for repository operations.
- Split the large Compose screen file into feature presentation files.
- Convert all screens to typed navigation and add back-stack/deep-link tests.
- Add instrumentation tests for database and lifecycle behavior.
- Run both JVM unit tests and emulator-backed Room migration instrumentation tests in Android CI before considering the database work verified.
