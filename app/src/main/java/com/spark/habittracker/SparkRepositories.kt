package com.spark.habittracker

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Domain-facing persistence boundary for habits. The Room DAO remains an
 * implementation detail of the data layer and can be replaced independently.
 */
class HabitRepository internal constructor(private val dao: HabitDao) {
    fun observeHabits(): Flow<List<HabitEntity>> = dao.observeHabits()
    fun observeLogs(date: LocalDate): Flow<List<HabitLogEntity>> = dao.observeLogs(date.toString())
    fun observeAllLogs(): Flow<List<HabitLogEntity>> = dao.observeAllLogs()
    suspend fun save(habit: HabitEntity): Long {
        require(habit.title.isNotBlank()) { "Habit title must not be blank." }
        require(habit.scheduledDays.split(",").any { it.isNotBlank() }) { "At least one scheduled day is required." }
        java.time.LocalTime.parse(habit.reminderTime)
        return if (habit.id == 0L) dao.insertHabit(habit.copy(title = habit.title.trim()))
        else {
            dao.updateHabit(habit.copy(title = habit.title.trim()))
            habit.id
        }
    }
    suspend fun record(log: HabitLogEntity) = dao.saveLog(log)
    suspend fun delete(habitId: Long) {
        require(habitId > 0L)
        dao.deleteLogs(habitId)
        dao.deleteHabit(habitId)
    }
}

/** Facade for the productivity domain's Room DAO. */
class ProductivityRepository internal constructor(private val dao: ProductivityDao) {
    fun observeFocusSessions(): Flow<List<FocusSessionEntity>> = dao.observeFocusSessions()
    fun observeTaskHistory(): Flow<List<TaskCompletionEntity>> = dao.observeAllTaskHistory()
    fun observeGoals(): Flow<List<GoalEntity>> = dao.observeGoals()
    fun observeActiveFocusSession(): Flow<FocusSessionEntity?> = dao.observeActiveFocusSession()
    suspend fun recordTaskCompletion(taskId: Long, completed: Boolean, now: Long, date: LocalDate): Boolean =
        dao.recordTaskCompletion(taskId, completed, now, date.toString())

    // Goal operations are exposed through this boundary; Compose never receives a Room DAO.
    suspend fun linkedTaskIds(goalId: Long): List<Long> = dao.linkedTaskIds(goalId)
    suspend fun linkedHabitIds(goalId: Long): List<Long> = dao.linkedHabitIds(goalId)
    suspend fun completedLinkedTasks(goalId: Long, startDate: String, endDate: String, startMillis: Long, endMillis: Long): Int =
        dao.completedLinkedTasks(goalId, startDate, endDate, startMillis, endMillis)
    suspend fun completedLinkedHabits(goalId: Long, startDate: String, endDate: String): Int =
        dao.completedLinkedHabits(goalId, startDate, endDate)
    suspend fun archiveGoal(goalId: Long, updatedAt: Long): Int = dao.archiveGoal(goalId, updatedAt)
    suspend fun createGoal(goal: GoalEntity, taskIds: Set<Long>, habitIds: Set<Long>): Long {
        require(goal.title.isNotBlank()) { "Goal title must not be blank." }
        require(goal.targetValue.isFinite() && goal.targetValue > 0.0) { "Goal target must be positive." }
        val id = dao.insertGoal(goal)
        taskIds.forEach { dao.linkTaskToGoal(GoalTaskLinkEntity(id, it)) }
        habitIds.forEach { dao.linkHabitToGoal(GoalHabitLinkEntity(id, it)) }
        return id
    }

    // Focus operations stay behind the same repository boundary.
    suspend fun finishFocusSession(id: Long, actualSeconds: Long, completed: Boolean, endedAt: Long): Int =
        dao.finishFocusSession(id, actualSeconds, completed, endedAt)
    suspend fun disableRemindersForOwner(ownerType: String, ownerId: Long): Int =
        dao.disableRemindersForOwner(ownerType, ownerId)
    suspend fun insertFocusSession(session: FocusSessionEntity): Long = dao.insertFocusSession(session)
    suspend fun insertReminder(reminder: ReminderEntity): Long = dao.insertReminder(reminder)
    suspend fun updateFocusPause(id: Long, pausedAt: Long?, pausedSeconds: Long): Int =
        dao.updateFocusPause(id, pausedAt, pausedSeconds)
    suspend fun deleteRemindersForOwner(ownerType: String, ownerId: Long): Int =
        dao.deleteRemindersForOwner(ownerType, ownerId)
}
