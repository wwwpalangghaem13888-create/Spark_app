package com.spark.habittracker

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

    suspend fun export(context: Context, db: SparkDatabase, uri: Uri) {
        val habits = db.habitDao().getAllHabits()
        val logs = db.habitDao().getAllLogs()
        val tasks = db.taskDao().getAllTasks()
        val productivity = db.productivityDao()
        val root = JSONObject()
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
        db.habitDao().getSettings()?.let { root.put("settings", it.toJson()) }
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("Cannot open destination for writing.")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
    }

    suspend fun import(context: Context, db: SparkDatabase, uri: Uri): Int {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup file.")
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        require(text.length <= 20_000_000) { "Backup file is too large." }
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

        require(habits.map { it.id }.distinct().size == habits.size) { "Duplicate habit IDs in backup." }
        require(tasks.map { it.id }.distinct().size == tasks.size) { "Duplicate task IDs in backup." }
        require(goals.map { it.id }.distinct().size == goals.size) { "Duplicate goal IDs in backup." }
        val habitIds = habits.map { it.id }.toSet()
        val taskIds = tasks.map { it.id }.toSet()
        val goalIds = goals.map { it.id }.toSet()
        require(logs.all { it.habitId in habitIds }) { "Habit history references a missing habit." }
        require(subtasks.all { it.taskId in taskIds } && history.all { it.taskId in taskIds }) { "Task history references a missing task." }
        require(taskLinks.all { it.goalId in goalIds && it.taskId in taskIds }) { "Goal-task link is invalid." }
        require(habitLinks.all { it.goalId in goalIds && it.habitId in habitIds }) { "Goal-habit link is invalid." }

        db.withTransaction {
            db.habitDao().restoreHabits(habits)
            db.habitDao().restoreLogs(logs)
            db.taskDao().restoreTasks(tasks)
            val dao = db.productivityDao()
            dao.restoreSubtasks(subtasks)
            dao.restoreTaskHistory(history)
            dao.restoreGoals(goals)
            dao.restoreGoalTaskLinks(taskLinks)
            dao.restoreGoalHabitLinks(habitLinks)
            dao.restoreFocusSessions(sessions)
            dao.restoreReminders(reminders)
            settings?.let { db.habitDao().saveSettings(it) }
        }
        return habits.size + logs.size + tasks.size + subtasks.size + history.size + goals.size + sessions.size + reminders.size
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
        .put("enabled", enabled).put("snoozedUntil", snoozedUntil ?: JSONObject.NULL).put("createdAt", createdAt)

    private fun UserSettingsEntity.toJson() = JSONObject()
        .put("id", id).put("theme", theme).put("accent", accent).put("reducedMotion", reducedMotion)

    private fun JSONObject.toHabit() = HabitEntity(
        id = requiredLong("id"), title = requiredString("title"), note = optString("note"),
        category = optString("category", "عمومی"), createdAt = requiredLong("createdAt"),
        scheduledDays = optString("scheduledDays", "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY"),
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
        snoozedUntil = optionalLong("snoozedUntil"), createdAt = requiredLong("createdAt")
    )

    private fun JSONObject.toSettings() = UserSettingsEntity(
        id = optInt("id", 1), theme = optString("theme", "neon"),
        accent = optString("accent", "violet"), reducedMotion = optBoolean("reducedMotion")
    )
}
