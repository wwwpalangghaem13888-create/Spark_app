# SPARK architecture

## Current direction

SPARK uses a deliberately incremental MVVM architecture. Existing screens and Room schema remain in place while persistence and state ownership move behind explicit boundaries.

- **Presentation:** Compose screens, typed SparkDestination navigation identifiers, and SparkViewModel for task-list state and mutations.
- **Domain-facing boundaries:** TaskRepository, HabitRepository, and ProductivityRepository.
- **Data:** SparkDatabase, Room DAOs, and SparkDatabaseFactory.
- **Dependency injection:** SparkAppContainer is the composition root, created once by SparkApplication. This manual DI approach avoids adding a runtime framework before the dependency graph justifies it.
- **Errors:** SparkError and SparkResult provide a stable error vocabulary; ViewModel mutation failures are surfaced through a SharedFlow and displayed by the UI.

## Navigation

SparkDestination maps stable route names to the existing bottom-navigation destinations. This is a compatibility bridge, not a claim that a full Navigation Compose back stack has already replaced the current tab host.

## Migration strategy

1. Keep the existing Room schema and migration chain unchanged.
2. Move persistence declarations out of the Activity.
3. Route one vertical slice (tasks) through a ViewModel and repository.
4. Migrate habit, goals, focus, backup, and reminder flows one at a time.
5. Introduce Navigation Compose only when deep links/back-stack behavior is designed and tested.
6. Consider Hilt only if the manual container becomes difficult to maintain.

## Known remaining work

- Move remaining direct DAO access from Compose screens into ViewModels/repositories.
- Split the large Compose screen file into feature presentation files.
- Convert all screens to typed navigation and add back-stack/deep-link tests.
- Add instrumentation tests for database and lifecycle behavior.
- Run the Android CI workflow on this branch before considering the refactor verified.
