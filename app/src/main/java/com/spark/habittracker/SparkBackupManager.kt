package com.spark.habittracker

import android.app.Activity
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import androidx.room.withTransaction
import java.time.LocalDate

/**
 * Versioned, merge-only local backup. Import validates the entire document before
 * opening a transaction; existing records are never cleared.
 */
object SparkBackupManager {
    private const val FORMAT_VERSION = 1
    private const val MAX_BACKUP_CHARS = 20_000_000

    suspend fun export(context: Context, db: SparkDatabase, uri: Uri) {
        val root = db.withTransaction {
            val habits = db.habitDao().getAllHabits()
            val logs = db.habitDao().getAllLogs()
            val tasks = db.taskDao().getAllTasks()
            val productivity = db.productivityDao()
            val result = JSONObject()
                .put("format", "spark-backup")
                .put("version", FORMAT_VERSION)
                .put("exportedAt", System.currentTimeMillis())
                .put("habits", JSONArray().apply { habits.forEach { put(it.toJson()) } })
                .put("habitLogs", JSONArray().apply { logs.forEach { put(it.toJson()) } })
                .put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
                .put("subtasks", JSONArray().apply { productivity.getAllSubtasks().forEach { put(it.toJson()) } })
                .put("taskHistory", JSONArray().apply { productivity.getAllTaskHistory().forEach { put(it.toJson()) } })
                .put("goals", JSONArray().apply { productivity.getAllGoals().forEach { put(it.toJson()) } })
                .put("goalTaskLinks", JSONArray().apply { productivity.getAllGoalTaskLinks().forEach { put(it.toJson()) } })
                .put("goalHabitLinks", JSONArray().apply { productivity.getAllGoalHabitLinks().forEach { put(it.toJson()) } })
                .put("focusSessions", JSONArray().apply { productivity.getAllFocusSessions().forEach { put(it.toJson()) } })
                .put("reminders", JSONArray().apply { productivity.getAllReminders().forEach { put(it.toJson()) } })
            db.habitDao().getSettings()?.let { result.put("settings", it.toJson()) }
            result
        }
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("Cannot open destination for writing.")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
    }
    suspend fun import(context: Context, db: SparkDatabase, uri: Uri): Int {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup file.")
        val text = input.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8192)
            var total = 0
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_BACKUP_CHARS) { "Backup file is too large." }
                result.append(buffer, 0, read)
            }
            result.toString()
        }
        val root = JSONObject(text)
        require(root.optString("format") == "spark-backup") { "This is not a Spark backup." }
        require(root.optInt("version", -1) == FORMAT_VERSION) { "Unsupported backup version." }

        val habits = root.array("habits").map { it.asObject().toHabit() }
        val logs = root.array("habitLogs").map { it.asObject().toHabitLog() }
        val tasks = root.array("tasks").map { it.asObject().toTask() }
        val subtasks = root.array("subtasks").map { it.asObject().toSubtask() }
        val history = root.array("taskHistory").map { it.asObject().toTaskHistory() }
        val goals = root.array("goals").map { it.asObject().toGoal() }
        val taskLinks = root.array("goalTaskLinks").map { it.asObject().toGoalTaskLink() }
        val habitLinks = root.array("goalHabitLinks").map { it.asObject().toGoalHabitLink() }
        val sessions = root.array("focusSessions").map { it.asObject().toFocusSession() }
        val reminders = root.array("reminders").map { it.asObject().toReminder() }
        val settings = if (root.has("settings") && !root.isNull("settings")) root.getJSONObject("settings").toSettings() else null

        ensureUniqueIds("habit", habits) { it.id }
        ensureUniqueIds("habit log", logs) { it.id }
        ensureUniqueIds("task", tasks) { it.id }
        ensureUniqueIds("subtask", subtasks) { it.id }
        ensureUniqueIds("task history", history) { it.id }
        ensureUniqueIds("goal", goals) { it.id }
        ensureUniqueIds("focus session", sessions) { it.id }
        ensureUniqueIds("reminder", reminders) { it.id }
        require(taskLinks.distinct().size == taskLinks.size) { "Duplicate goal-task links in backup." }
        require(habitLinks.distinct().size == habitLinks.size) { "Duplicate goal-habit links in backup." }

        val habitIds = habits.map { it.id }.toSet()
        val taskIds = tasks.map { it.id }.toSet()
        val goalIds = goals.map { it.id }.toSet()
        val sessionIds = sessions.map { it.id }.toSet()
        require(logs.all { it.habitId in habitIds }) { "Habit history references a missing habit." }
        require(subtasks.all { it.taskId in taskIds } && history.all { it.taskId in taskIds }) { "Task history references a missing task." }
        require(taskLinks.all { it.goalId in goalIds && it.taskId in taskIds }) { "Goal-task link is invalid." }
        require(habitLinks.all { it.goalId in goalIds && it.habitId in habitIds }) { "Goal-habit link is invalid." }
        require(tasks.all { it.recurrenceParentId == null || it.recurrenceParentId in taskIds }) { "Recurring task references a missing parent." }
        require(sessions.all { it.linkedTaskId == null || it.linkedTaskId in taskIds }) { "Focus session references a missing task." }
        require(reminders.all { reminder ->
            reminder.mode in setOf("NOTIFICATION", "ALARM") &&
                reminder.ownerId > 0 && when (reminder.ownerType) {
                "TASK" -> reminder.ownerId in taskIds
                "HABIT" -> reminder.ownerId in habitIds
                "FOCUS" -> reminder.ownerId in sessionIds
                else -> false
            }
        }) { "Reminder references an unsupported or missing owner." }
        val allowedDays = java.time.DayOfWeek.values().map { it.name }.toSet()
        require(habits.all { habit ->
            val rawDays = habit.scheduledDays.split(",").map { it.trim().uppercase(java.util.Locale.ROOT) }
            val days = rawDays.filter(String::isNotEmpty)
            days.isNotEmpty() && days.size == rawDays.size && days.distinct().size == days.size &&
                days.all { it in allowedDays } &&
                (habit.reminderTime.isBlank() || runCatching { java.time.LocalTime.parse(habit.reminderTime) }.isSuccess)
        }) { "Habit schedule or reminder time is invalid." }
        require(settings == null || (
            settings.id == 1 &&
                settings.theme in setOf("neon", "dark", "minimal", "light") &&
                settings.accent in setOf("violet", "cyan", "lime", "pink") &&
                settings.language in setOf("fa", "en", "ar")
        )) { "Appearance settings are invalid." }

        // Room's REPLACE strategy would overwrite an unrelated row with a colliding ID.
        // Accept exact repeats (making restore idempotent), but reject conflicting rows.
        db.withTransaction {
            val habitDao = db.habitDao()
            val taskDao = db.taskDao()
            val productivity = db.productivityDao()
            val existingHabits = habitDao.getAllHabits()
            val existingLogs = habitDao.getAllLogs()
            val existingTasks = taskDao.getAllTasks()
            ensureNoIdConflicts("habit", habits, existingHabits) { it.id }
            ensureNoIdConflicts("habit log", logs, existingLogs) { it.id }
            ensureNoIdConflicts("task", tasks, existingTasks) { it.id }
            ensureNoIdConflicts("subtask", subtasks, productivity.getAllSubtasks()) { it.id }
            ensureNoIdConflicts("task history", history, productivity.getAllTaskHistory()) { it.id }
            ensureNoIdConflicts("goal", goals, productivity.getAllGoals()) { it.id }
            ensureNoIdConflicts("focus session", sessions, productivity.getAllFocusSessions()) { it.id }
            ensureNoIdConflicts("reminder", reminders, productivity.getAllReminders()) { it.id }
            ensureNoHabitLogConflicts(logs, existingLogs)

            // IGNORE avoids SQLite REPLACE's delete-then-insert behavior and
            // therefore preserves existing dependent rows under foreign keys.
            habitDao.restoreHabits(habits)
            habitDao.restoreLogs(logs)
            taskDao.restoreTasks(tasks)
            productivity.restoreSubtasks(subtasks)
            productivity.restoreTaskHistory(history)
            productivity.restoreGoals(goals)
            productivity.restoreGoalTaskLinks(taskLinks)
            productivity.restoreGoalHabitLinks(habitLinks)
            productivity.restoreFocusSessions(sessions)
            productivity.restoreReminders(reminders)
            settings?.let { habitDao.saveSettings(it) }
        }
        if (settings != null) {
            syncLanguagePreference(context, settings.language)
            (context as? Activity)?.recreate()
        }
        db.productivityDao().getEnabledReminders().forEach {
            ReminderScheduler.cancel(context, it.id)
            // Keep imported ALARM settings intact when Android exact-alarm
            // access is currently denied; the permission receiver reschedules later.
            ReminderScheduler.schedule(context, it)
        }
        return habits.size + logs.size + tasks.size + subtasks.size + history.size +
            goals.size + taskLinks.size + habitLinks.size + sessions.size + reminders.size +
            if (settings != null) 1 else 0
    }
    private fun <T> ensureUniqueIds(name: String, items: List<T>, idOf: (T) -> Long) {
        val ids = items.map(idOf)
        require(ids.all { it > 0L }) { "Backup contains an invalid $name ID." }
        require(ids.distinct().size == ids.size) { "Duplicate $name IDs in backup." }
    }

    private fun <T> ensureNoIdConflicts(name: String, incoming: List<T>, existing: List<T>, idOf: (T) -> Long) {
        val existingById = existing.associateBy(idOf)
        incoming.forEach { item ->
            val current = existingById[idOf(item)]
            require(current == null || current == item) {
                "Backup conflicts with an existing $name ID: ${idOf(item)}."
            }
        }
    }

    private fun ensureNoHabitLogConflicts(incoming: List<HabitLogEntity>, existing: List<HabitLogEntity>) {
        val incomingByDay = incoming.associateBy { it.habitId to it.date }
        require(incomingByDay.size == incoming.size) { "Duplicate habit/day records in backup." }
        val existingByDay = existing.associateBy { it.habitId to it.date }
        incomingByDay.forEach { (key, item) ->
            val current = existingByDay[key]
            require(current == null || current == item) {
                "Backup conflicts with an existing habit/day record."
            }
        }
    }

    private fun JSONObject.requiredString(key: String): String =
        optString(key).takeIf { has(key) && it.isNotBlank() && it != "null" } ?: error("Missing or invalid '$key'.")

    private fun JSONObject.requiredLong(key: String): Long =
        if (has(key) && !isNull(key)) getLong(key) else error("Missing '$key'.")

    private fun JSONObject.optionalLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun JSONObject.array(key: String): List<Any> {
        require(has(key) && !isNull(key)) { "Backup is missing the '$key' collection." }
        val array = optJSONArray(key) ?: error("Backup collection '$key' is invalid.")
        return (0 until array.length()).map { array.get(it) }
    }

    private fun Any.asObject(): JSONObject = this as? JSONObject ?: error("Invalid backup record.")

    private fun HabitEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("note", note).put("category", category)
        .put("createdAt", createdAt).put("scheduledDays", scheduledDays)
        .put("reminderTime", reminderTime).put("isEnabled", isEnabled)

    private fun HabitLogEntity.toJson() = JSONObject()
        .put("id", id).put("habitId", habitId).put("date", date)
        .put("isCompleted", isCompleted).put("completionTimestamp", completionTimestamp)

    private fun TaskEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("description", description).put("category", category)
        .put("dueDate", dueDate ?: JSONObject.NULL).put("startAt", startAt ?: JSONObject.NULL)
        .put("endAt", endAt ?: JSONObject.NULL).put("reminderAt", reminderAt ?: JSONObject.NULL)
        .put("recurrenceRule", recurrenceRule ?: JSONObject.NULL).put("recurrenceParentId", recurrenceParentId ?: JSONObject.NULL)
        .put("priority", priority).put("tags", tags).put("completed", completed)
        .put("completedAt", completedAt ?: JSONObject.NULL).put("archived", archived)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun TaskSubtaskEntity.toJson() = JSONObject()
        .put("id", id).put("taskId", taskId).put("title", title).put("isCompleted", isCompleted)
        .put("position", position).put("createdAt", createdAt)

    private fun TaskCompletionEntity.toJson() = JSONObject()
        .put("id", id).put("taskId", taskId).put("completed", completed)
        .put("activityDate", activityDate).put("completedAt", completedAt)

    private fun GoalEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("description", description).put("category", category)
        .put("targetValue", targetValue).put("unit", unit).put("period", period)
        .put("startDate", startDate).put("targetDate", targetDate ?: JSONObject.NULL)
        .put("archived", archived).put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun GoalTaskLinkEntity.toJson() = JSONObject().put("goalId", goalId).put("taskId", taskId)
    private fun GoalHabitLinkEntity.toJson() = JSONObject().put("goalId", goalId).put("habitId", habitId)

    private fun FocusSessionEntity.toJson() = JSONObject()
        .put("id", id).put("mode", mode).put("startedAt", startedAt)
        .put("plannedDurationSeconds", plannedDurationSeconds).put("actualDurationSeconds", actualDurationSeconds)
        .put("pausedAt", pausedAt ?: JSONObject.NULL).put("accumulatedPausedSeconds", accumulatedPausedSeconds)
        .put("completed", completed).put("endedAt", endedAt ?: JSONObject.NULL).put("linkedTaskId", linkedTaskId ?: JSONObject.NULL)

    private fun ReminderEntity.toJson() = JSONObject()
        .put("id", id).put("ownerType", ownerType).put("ownerId", ownerId).put("triggerAt", triggerAt)
        .put("enabled", enabled).put("snoozedUntil", snoozedUntil ?: JSONObject.NULL)
        .put("createdAt", createdAt).put("mode", mode)

    private fun UserSettingsEntity.toJson() = JSONObject()
        .put("id", id).put("theme", theme).put("accent", accent)
        .put("reducedMotion", reducedMotion).put("language", language)

    private fun JSONObject.toHabit() = HabitEntity(
        id = requiredLong("id"), title = requiredString("title"), note = optString("note"),
        category = optString("category", "عمومی"), createdAt = requiredLong("createdAt"),
        scheduledDays = optString("scheduledDays", "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
            .split(",").map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.distinct().joinToString(","),
        reminderTime = optString("reminderTime", "08:00"), isEnabled = optBoolean("isEnabled", true)
    )

    private fun JSONObject.toHabitLog() = HabitLogEntity(
        id = requiredLong("id"), habitId = requiredLong("habitId"), date = requiredString("date"),
        isCompleted = getBoolean("isCompleted"), completionTimestamp = requiredLong("completionTimestamp")
    ).also { LocalDate.parse(it.date) }

    private fun JSONObject.toTask() = TaskEntity(
        id = requiredLong("id"), title = requiredString("title"), description = optString("description"),
        category = optString("category", "عمومی"), dueDate = optNullableString("dueDate"),
        startAt = optionalLong("startAt"), endAt = optionalLong("endAt"), reminderAt = optionalLong("reminderAt"),
        recurrenceRule = optNullableString("recurrenceRule"), recurrenceParentId = optionalLong("recurrenceParentId"),
        priority = optInt("priority", 1), tags = optString("tags"), completed = optBoolean("completed"),
        completedAt = optionalLong("completedAt"), archived = optBoolean("archived"),
        createdAt = requiredLong("createdAt"), updatedAt = requiredLong("updatedAt")
    ).also {
        it.dueDate?.let(LocalDate::parse)
        require(it.startAt == null || it.endAt == null || it.endAt > it.startAt)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.toSubtask() = TaskSubtaskEntity(
        id = requiredLong("id"), taskId = requiredLong("taskId"), title = requiredString("title"),
        isCompleted = optBoolean("isCompleted"), position = optInt("position"), createdAt = requiredLong("createdAt")
    )

    private fun JSONObject.toTaskHistory() = TaskCompletionEntity(
        id = requiredLong("id"), taskId = requiredLong("taskId"), completed = getBoolean("completed"),
        activityDate = requiredString("activityDate"), completedAt = requiredLong("completedAt")
    ).also { LocalDate.parse(it.activityDate) }

    private fun JSONObject.toGoal() = GoalEntity(
        id = requiredLong("id"), title = requiredString("title"), description = optString("description"),
        category = optString("category", "عمومی"), targetValue = getDouble("targetValue"),
        unit = optString("unit", "فعالیت"), period = optString("period", "CUSTOM"),
        startDate = requiredString("startDate"), targetDate = optNullableString("targetDate"),
        archived = optBoolean("archived"), createdAt = requiredLong("createdAt"), updatedAt = requiredLong("updatedAt")
    ).also {
        require(it.targetValue.isFinite() && it.targetValue > 0.0)
        LocalDate.parse(it.startDate)
        it.targetDate?.let(LocalDate::parse)
    }

    private fun JSONObject.toGoalTaskLink() = GoalTaskLinkEntity(requiredLong("goalId"), requiredLong("taskId"))
    private fun JSONObject.toGoalHabitLink() = GoalHabitLinkEntity(requiredLong("goalId"), requiredLong("habitId"))

    private fun JSONObject.toFocusSession() = FocusSessionEntity(
        id = requiredLong("id"), mode = optString("mode", "FOCUS"), startedAt = requiredLong("startedAt"),
        plannedDurationSeconds = requiredLong("plannedDurationSeconds"), actualDurationSeconds = requiredLong("actualDurationSeconds"),
        pausedAt = optionalLong("pausedAt"), accumulatedPausedSeconds = requiredLong("accumulatedPausedSeconds"),
        completed = optBoolean("completed"), endedAt = optionalLong("endedAt"), linkedTaskId = optionalLong("linkedTaskId")
    ).also { require(it.plannedDurationSeconds > 0 && it.actualDurationSeconds >= 0) }

    private fun JSONObject.toReminder() = ReminderEntity(
        id = requiredLong("id"), ownerType = requiredString("ownerType"), ownerId = requiredLong("ownerId"),
        triggerAt = requiredLong("triggerAt"), enabled = optBoolean("enabled", true),
        snoozedUntil = optionalLong("snoozedUntil"), createdAt = requiredLong("createdAt"),
        mode = optString("mode", "NOTIFICATION")
    )

    private fun JSONObject.toSettings() = UserSettingsEntity(
        id = optInt("id", 1), theme = optString("theme", "neon"),
        accent = optString("accent", "violet"), reducedMotion = optBoolean("reducedMotion"),
        language = optString("language", "fa")
    )
}
