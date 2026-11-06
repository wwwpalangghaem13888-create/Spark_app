package com.spark.habittracker

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Entity(
    tableName = "task_subtasks",
    indices = [Index("taskId")],
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)]
)
data class TaskSubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_completion_history",
    indices = [Index("taskId"), Index("completedAt")],
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)]
)
data class TaskCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val completed: Boolean,
    val activityDate: String,
    val completedAt: Long
)

@Entity(tableName = "goals", indices = [Index("targetDate"), Index("archived")])
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "عمومی",
    val targetValue: Double,
    val unit: String = "فعالیت",
    val period: String = "CUSTOM",
    val startDate: String,
    val targetDate: String? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "goal_task_links",
    primaryKeys = ["goalId", "taskId"],
    foreignKeys = [
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("taskId")]
)
data class GoalTaskLinkEntity(val goalId: Long, val taskId: Long)

@Entity(
    tableName = "goal_habit_links",
    primaryKeys = ["goalId", "habitId"],
    foreignKeys = [
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("habitId")]
)
data class GoalHabitLinkEntity(val goalId: Long, val habitId: Long)

@Entity(
    tableName = "focus_sessions",
    indices = [Index("startedAt"), Index("completed")]
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String = "POMODORO",
    val startedAt: Long,
    val plannedDurationSeconds: Long,
    val actualDurationSeconds: Long = 0,
    val pausedAt: Long? = null,
    val accumulatedPausedSeconds: Long = 0,
    val completed: Boolean = false,
    val endedAt: Long? = null,
    val linkedTaskId: Long? = null
)

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["ownerType", "ownerId"]), Index("triggerAt")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: String,
    val ownerId: Long,
    val triggerAt: Long,
    val enabled: Boolean = true,
    val snoozedUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Persistence boundary for task history, subtasks, goals, focus sessions and reminders. */
@Dao
interface ProductivityDao {
    @Query("SELECT * FROM task_subtasks WHERE taskId = :taskId ORDER BY position, id")
    fun observeSubtasks(taskId: Long): Flow<List<TaskSubtaskEntity>>

    @Query("SELECT * FROM task_subtasks")
    suspend fun getAllSubtasks(): List<TaskSubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSubtasks(items: List<TaskSubtaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSubtask(subtask: TaskSubtaskEntity): Long

    @Query("UPDATE task_subtasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setSubtaskCompleted(id: Long, completed: Boolean): Int

    @Query("DELETE FROM task_subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: Long): Int

    @Query("SELECT * FROM task_completion_history WHERE taskId = :taskId ORDER BY completedAt DESC")
    fun observeTaskHistory(taskId: Long): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completion_history ORDER BY completedAt DESC")
    fun observeAllTaskHistory(): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completion_history")
    suspend fun getAllTaskHistory(): List<TaskCompletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreTaskHistory(items: List<TaskCompletionEntity>)

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun findTaskForCompletion(taskId: Long): TaskEntity?

    @Query("SELECT id FROM tasks WHERE recurrenceParentId = :parentId AND dueDate = :date LIMIT 1")
    suspend fun findRecurringOccurrence(parentId: Long, date: String): Long?

    @Insert
    suspend fun insertRecurringOccurrence(task: TaskEntity): Long

    @Insert
    suspend fun insertTaskHistory(item: TaskCompletionEntity): Long

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY targetDate IS NULL, targetDate")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals")
    suspend fun getAllGoals(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreGoals(items: List<GoalEntity>)

    @Query("SELECT * FROM goal_task_links")
    suspend fun getAllGoalTaskLinks(): List<GoalTaskLinkEntity>

    @Query("SELECT * FROM goal_habit_links")
    suspend fun getAllGoalHabitLinks(): List<GoalHabitLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreGoalTaskLinks(items: List<GoalTaskLinkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreGoalHabitLinks(items: List<GoalHabitLinkEntity>)

    @Query("SELECT * FROM focus_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveFocusSession(): Flow<FocusSessionEntity?>

    @Insert
    suspend fun insertGoal(goal: GoalEntity): Long

    @Query("UPDATE goals SET archived = 1, updatedAt = :updatedAt WHERE id = :goalId")
    suspend fun archiveGoal(goalId: Long, updatedAt: Long): Int

    @Query("UPDATE goals SET title = :title, description = :description, category = :category, targetValue = :targetValue, unit = :unit, period = :period, startDate = :startDate, targetDate = :targetDate, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGoal(id: Long, title: String, description: String, category: String, targetValue: Double, unit: String, period: String, startDate: String, targetDate: String?, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTaskToGoal(link: GoalTaskLinkEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkHabitToGoal(link: GoalHabitLinkEntity): Long

    @Query("SELECT taskId FROM goal_task_links WHERE goalId = :goalId")
    suspend fun linkedTaskIds(goalId: Long): List<Long>

    @Query("SELECT habitId FROM goal_habit_links WHERE goalId = :goalId")
    suspend fun linkedHabitIds(goalId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM tasks WHERE id IN (SELECT taskId FROM goal_task_links WHERE goalId = :goalId) AND completed = 1 AND completedAt >= :startMillis AND completedAt <= :endMillis")
    suspend fun completedLinkedTasks(goalId: Long, startMillis: Long, endMillis: Long): Int

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId IN (SELECT habitId FROM goal_habit_links WHERE goalId = :goalId) AND isCompleted = 1 AND date >= :startDate AND date <= :endDate")
    suspend fun completedLinkedHabits(goalId: Long, startDate: String, endDate: String): Int

    @Insert
    suspend fun insertFocusSession(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun findActiveFocusSession(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeFocusSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions")
    suspend fun getAllFocusSessions(): List<FocusSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreFocusSessions(items: List<FocusSessionEntity>)

    @Query("UPDATE focus_sessions SET pausedAt = :pausedAt, accumulatedPausedSeconds = :accumulatedPausedSeconds WHERE id = :id")
    suspend fun updateFocusPause(id: Long, pausedAt: Long?, accumulatedPausedSeconds: Long): Int

    @Query("UPDATE focus_sessions SET actualDurationSeconds = :actualDurationSeconds, completed = :completed, endedAt = :endedAt, pausedAt = NULL WHERE id = :id")
    suspend fun finishFocusSession(id: Long, actualDurationSeconds: Long, completed: Boolean, endedAt: Long): Int

    @Insert
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteRemindersForOwner(ownerType: String, ownerId: Long): Int

    @Query("UPDATE reminders SET enabled = 0 WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun disableRemindersForOwner(ownerType: String, ownerId: Long): Int

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getEnabledReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreReminders(items: List<ReminderEntity>)

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND triggerAt <= :now AND (snoozedUntil IS NULL OR snoozedUntil <= :now) ORDER BY triggerAt")
    fun observeDueReminders(now: Long): Flow<List<ReminderEntity>>

    @Query("UPDATE reminders SET snoozedUntil = :untilMillis WHERE id = :id")
    suspend fun snoozeReminder(id: Long, untilMillis: Long): Int

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun setReminderEnabled(id: Long, enabled: Boolean): Int

    @Query("UPDATE reminders SET triggerAt = :triggerAt, snoozedUntil = NULL WHERE id = :id")
    suspend fun updateReminderTrigger(id: Long, triggerAt: Long): Int

    @Transaction
    suspend fun recordTaskCompletion(taskId: Long, completed: Boolean, atMillis: Long, date: String): Boolean {
        val task = findTaskForCompletion(taskId) ?: return false
        if (task.archived || task.completed == completed) return false
        val changed = setTaskCompletion(taskId, completed, if (completed) atMillis else null, atMillis)
        if (changed == 0) return false
        insertTaskHistory(TaskCompletionEntity(taskId = taskId, completed = completed, activityDate = date, completedAt = atMillis))
        if (completed) disableRemindersForOwner("TASK", taskId)
        if (completed && task.recurrenceRule != null && task.dueDate != null) {
            val rootId = task.recurrenceParentId ?: task.id
            val nextDate = runCatching { nextRecurringDate(task.recurrenceRule, LocalDate.parse(task.dueDate)) }.getOrNull()
            if (nextDate != null && findRecurringOccurrence(rootId, nextDate.toString()) == null) {
                insertRecurringOccurrence(task.copy(
                    id = 0,
                    dueDate = nextDate.toString(),
                    completed = false,
                    completedAt = null,
                    reminderAt = null,
                    archived = false,
                    recurrenceParentId = rootId,
                    createdAt = atMillis,
                    updatedAt = atMillis
                ))
            }
        }
        return true
    }

    @Query("UPDATE tasks SET completed = :completed, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :taskId AND archived = 0")
    suspend fun setTaskCompletion(taskId: Long, completed: Boolean, completedAt: Long?, updatedAt: Long): Int
}

/** Pure recurrence rules; dates are local calendar dates, not elapsed 24-hour periods. */
internal fun nextRecurringDate(rule: String?, from: LocalDate): LocalDate? = when (rule?.uppercase()) {
    "DAILY" -> from.plusDays(1)
    "WEEKLY" -> from.plusWeeks(1)
    "WEEKDAYS" -> {
        var next = from.plusDays(1)
        while (next.dayOfWeek.value > 5) next = next.plusDays(1)
        next
    }
    "MONTHLY" -> {
        val nextMonth = YearMonth.from(from).plusMonths(1)
        nextMonth.atDay(minOf(from.dayOfMonth, nextMonth.lengthOfMonth()))
    }
    else -> null
}

/** Remaining time is reconstructed from persisted timestamps after process death. */
internal fun focusRemainingSeconds(session: FocusSessionEntity, nowMillis: Long): Long {
    val elapsed = if (session.pausedAt != null) {
        session.pausedAt - session.startedAt - session.accumulatedPausedSeconds * 1000
    } else {
        nowMillis - session.startedAt - session.accumulatedPausedSeconds * 1000
    }
    return (session.plannedDurationSeconds - elapsed.coerceAtLeast(0) / 1000).coerceAtLeast(0)
}

internal fun focusElapsedSeconds(session: FocusSessionEntity, nowMillis: Long): Long {
    val end = session.pausedAt ?: nowMillis
    return ((end - session.startedAt - session.accumulatedPausedSeconds * 1000).coerceAtLeast(0) / 1000)
}

internal fun goalProgressRatio(completedActivities: Int, targetValue: Double): Float {
    if (!targetValue.isFinite() || targetValue <= 0.0) return 0f
    return (completedActivities.coerceAtLeast(0) / targetValue).toFloat().coerceIn(0f, 1f)
}

internal fun countUniqueTaskRewards(history: List<TaskCompletionEntity>, throughDate: LocalDate): Int =
    history.asSequence()
        .filter { it.completed && runCatching { LocalDate.parse(it.activityDate) <= throughDate }.getOrDefault(false) }
        .map { it.taskId to it.activityDate }
        .distinct()
        .count()

/** CSV export uses RFC-4180-style quoting and never writes unescaped user text. */
internal fun exportTasksCsv(tasks: List<TaskEntity>): String {
    fun cell(value: String?): String {
        val raw = value.orEmpty()
        val first = raw.trimStart().firstOrNull()
        val spreadsheetSafe = if (first == '=' || first == '+' || first == '-' || first == '@') "'" + raw else raw
        return "\"" + spreadsheetSafe.replace("\"", "\"\"") + "\""
    }
    return buildString {
        appendLine("id,title,description,category,dueDate,priority,tags,completed,completedAt,archived,startAt,endAt,recurrenceRule")
        tasks.forEach { task ->
            append(listOf(
                task.id.toString(), cell(task.title), cell(task.description), cell(task.category),
                cell(task.dueDate), task.priority.toString(), cell(task.tags), task.completed.toString(),
                task.completedAt?.toString().orEmpty(), task.archived.toString(),
                task.startAt?.toString().orEmpty(), task.endAt?.toString().orEmpty(), cell(task.recurrenceRule)
            ).joinToString(","))
            append('\n')
        }
    }
}


data class AnalyticsDay(
    val date: LocalDate,
    val completedHabits: Int,
    val scheduledHabits: Int,
    val completedTasks: Int,
    val focusSeconds: Long
)

data class AnalyticsReport(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val completedHabits: Int,
    val scheduledHabitOpportunities: Int,
    val completedTasks: Int,
    val focusSeconds: Long,
    val bestDay: LocalDate?,
    val days: List<AnalyticsDay>,
    val categoryTaskCounts: Map<String, Int>
) {
    val habitCompletionRate: Float
        get() = calculateCompletionRate(completedHabits, scheduledHabitOpportunities)
}

/** Builds reports only from persisted activity; no missing value is converted into a success. */
internal fun buildAnalyticsReport(
    habits: List<HabitEntity>,
    logs: List<HabitLogEntity>,
    tasks: List<TaskEntity>,
    sessions: List<FocusSessionEntity>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): AnalyticsReport {
    require(!endDate.isBefore(startDate)) { "End date must not be before start date." }
    val activeHabits = habits.filter { it.isEnabled }
    val completedLogKeys = logs.asSequence()
        .filter { it.isCompleted }
        .mapNotNull { log ->
            runCatching { log.habitId to LocalDate.parse(log.date) }.getOrNull()
        }.filter { (_, day) -> day >= startDate && day <= endDate }
        .toSet()
    val daily = generateSequence(startDate) { current ->
        if (current < endDate) current.plusDays(1) else null
    }.map { day ->
        val scheduled = activeHabits.filter { habit ->
            val created = java.time.Instant.ofEpochMilli(habit.createdAt).atZone(zoneId).toLocalDate()
            !day.isBefore(created) && day.dayOfWeek.name in habit.scheduledDays.split(",").map(String::trim)
        }
        val completedHabits = scheduled.count { (it.id to day) in completedLogKeys }
        val completedTasks = tasks.count { task ->
            task.completed && task.completedAt?.let { at ->
                val taskDay = java.time.Instant.ofEpochMilli(at).atZone(zoneId).toLocalDate()
                taskDay == day
            } == true
        }
        val focus = sessions.filter { session ->
            val sessionDay = java.time.Instant.ofEpochMilli(session.startedAt).atZone(zoneId).toLocalDate()
            sessionDay == day
        }.sumOf { it.actualDurationSeconds.coerceAtLeast(0) }
        AnalyticsDay(day, completedHabits, scheduled.size, completedTasks, focus)
    }.toList()
    val completedTaskEntities = tasks.filter { task ->
        task.completed && task.completedAt?.let { at ->
            val day = java.time.Instant.ofEpochMilli(at).atZone(zoneId).toLocalDate()
            day >= startDate && day <= endDate
        } == true
    }
    val focusSeconds = daily.sumOf { it.focusSeconds }
    val bestDay = daily.maxByOrNull { it.completedHabits + it.completedTasks + (it.focusSeconds / 60).toInt() }
        ?.takeIf { it.completedHabits + it.completedTasks + it.focusSeconds > 0 }?.date
    return AnalyticsReport(
        startDate = startDate,
        endDate = endDate,
        completedHabits = daily.sumOf { it.completedHabits },
        scheduledHabitOpportunities = daily.sumOf { it.scheduledHabits },
        completedTasks = completedTaskEntities.size,
        focusSeconds = focusSeconds,
        bestDay = bestDay,
        days = daily,
        categoryTaskCounts = completedTaskEntities.groupingBy { it.category.ifBlank { "عمومی" } }.eachCount()
    )
}

data class GoalProgress(
    val completedActivities: Int,
    val targetValue: Double,
    val ratio: Float
)

class GoalProgressRepository(private val dao: ProductivityDao) {
    suspend fun progress(
        goal: GoalEntity,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): GoalProgress {
        require(!endDate.isBefore(startDate))
        val startMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        val tasks = dao.completedLinkedTasks(goal.id, startMillis, endMillis)
        val habits = dao.completedLinkedHabits(goal.id, startDate.toString(), endDate.toString())
        val completed = tasks + habits
        return GoalProgress(completed, goal.targetValue, goalProgressRatio(completed, goal.targetValue))
    }
}
