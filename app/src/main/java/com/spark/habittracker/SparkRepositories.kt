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
}
