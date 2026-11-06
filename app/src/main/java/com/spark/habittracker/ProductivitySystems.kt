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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSubtask(subtask: TaskSubtaskEntity): Long

    @Query("DELETE FROM task_subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: Long): Int

    @Query("SELECT * FROM task_completion_history WHERE taskId = :taskId ORDER BY completedAt DESC")
    fun observeTaskHistory(taskId: Long): Flow<List<TaskCompletionEntity>>

    @Insert
    suspend fun insertTaskHistory(item: TaskCompletionEntity): Long

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY targetDate IS NULL, targetDate")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Insert
    suspend fun insertGoal(goal: GoalEntity): Long

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

    @Query("UPDATE focus_sessions SET pausedAt = :pausedAt, accumulatedPausedSeconds = :accumulatedPausedSeconds WHERE id = :id")
    suspend fun updateFocusPause(id: Long, pausedAt: Long?, accumulatedPausedSeconds: Long): Int

    @Query("UPDATE focus_sessions SET actualDurationSeconds = :actualDurationSeconds, completed = :completed, endedAt = :endedAt, pausedAt = NULL WHERE id = :id")
    suspend fun finishFocusSession(id: Long, actualDurationSeconds: Long, completed: Boolean, endedAt: Long): Int

    @Insert
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND (snoozedUntil IS NULL OR snoozedUntil <= :now) ORDER BY triggerAt")
    fun observeDueReminders(now: Long): Flow<List<ReminderEntity>>

    @Query("UPDATE reminders SET snoozedUntil = :untilMillis WHERE id = :id")
    suspend fun snoozeReminder(id: Long, untilMillis: Long): Int

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun setReminderEnabled(id: Long, enabled: Boolean): Int

    @Transaction
    suspend fun recordTaskCompletion(taskId: Long, completed: Boolean, atMillis: Long, date: String): Boolean {
        val changed = setTaskCompletion(taskId, completed, if (completed) atMillis else null, atMillis)
        if (changed == 0) return false
        insertTaskHistory(TaskCompletionEntity(taskId = taskId, completed = completed, activityDate = date, completedAt = atMillis))
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

internal fun goalProgressRatio(completedActivities: Int, targetValue: Double): Float {
    if (!targetValue.isFinite() || targetValue <= 0.0) return 0f
    return (completedActivities.coerceAtLeast(0) / targetValue).toFloat().coerceIn(0f, 1f)
}

/** CSV export uses RFC-4180-style quoting and never writes unescaped user text. */
internal fun exportTasksCsv(tasks: List<TaskEntity>): String {
    fun cell(value: String?): String {
        val raw = value.orEmpty()
        return "\"" + raw.replace("\"", "\"\"") + "\""
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
