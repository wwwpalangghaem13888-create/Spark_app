package com.spark.habittracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskManagementTest {

    @Test
    fun taskReminderOnlySchedulesForOpenUnarchivedTasksInTheFuture() {
        val now = 1_000_000L
        val task = TaskEntity(id = 1, title = "Study", reminderAt = now + 60_000L)
        assertEquals(now + 60_000L, taskReminderTriggerAt(task, now))
        assertEquals(null, taskReminderTriggerAt(task.copy(completed = true), now))
        assertEquals(null, taskReminderTriggerAt(task.copy(archived = true), now))
        assertEquals(null, taskReminderTriggerAt(task.copy(reminderAt = now), now))
        assertEquals(null, taskReminderTriggerAt(task.copy(reminderAt = null), now))
    }

    @Test
    fun overdueRequiresAnUncompletedTaskWithPastDeadline() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val task = TaskEntity(id = 1, title = "Submit assignment", dueDate = yesterday)
        assertTrue(taskIsOverdue(task, LocalDate.now()))
        assertFalse(taskIsOverdue(task.copy(completed = true), LocalDate.now()))
        assertFalse(taskIsOverdue(task.copy(archived = true), LocalDate.now()))
    }

    @Test
    fun deadlineTodayIsNotOverdue() {
        val task = TaskEntity(id = 1, title = "Study", dueDate = LocalDate.now().toString())
        assertFalse(taskIsOverdue(task, LocalDate.now()))
    }

    @Test
    fun tasksWithoutDeadlineAreNotOverdue() {
        val task = TaskEntity(id = 1, title = "Read", dueDate = null)
        assertFalse(taskIsOverdue(task, LocalDate.now()))
    }

    @Test
    fun priorityIsClampedToSupportedRange() {
        assertEquals("کم", taskPriorityLabel(-5))
        assertEquals("معمولی", taskPriorityLabel(1))
        assertEquals("زیاد", taskPriorityLabel(2))
        assertEquals("فوری", taskPriorityLabel(30))
    }
    @Test
    fun recurrenceAcceptsOneOffAndSupportedPresets() {
        assertTrue(isSupportedTaskRecurrence(null))
        assertTrue(isSupportedTaskRecurrence("DAILY"))
        assertTrue(isSupportedTaskRecurrence("WEEKLY"))
        assertTrue(isSupportedTaskRecurrence("WEEKDAYS"))
        assertTrue(isSupportedTaskRecurrence("MONTHLY"))
    }

    @Test
    fun recurrenceAcceptsExplicitWeeklyDaySelections() {
        assertTrue(isSupportedTaskRecurrence("WEEKLY:MONDAY,WEDNESDAY,FRIDAY"))
        assertTrue(isSupportedTaskRecurrence("WEEKLY:SATURDAY"))
    }

    @Test
    fun finiteRecurrenceStopsAfterConfiguredOccurrences() {
        val limited = TaskEntity(
            id = 9,
            title = "Buy vegetables",
            dueDate = "2026-11-07",
            recurrenceRule = "WEEKLY:SATURDAY",
            repeatRemainingCount = 2
        )
        assertTrue(hasRemainingTaskOccurrences(limited))
        assertTrue(hasRemainingTaskOccurrences(limited.copy(repeatRemainingCount = 1)))
        assertFalse(hasRemainingTaskOccurrences(limited.copy(repeatRemainingCount = 0)))
        assertTrue(hasRemainingTaskOccurrences(limited.copy(repeatRemainingCount = null)))
    }

    @Test
    fun recurrenceRejectsInvalidOrDuplicateWeeklyDays() {
        assertFalse(isSupportedTaskRecurrence("WEEKLY:"))
        assertFalse(isSupportedTaskRecurrence("WEEKLY:MONDAY,MONDAY"))
        assertFalse(isSupportedTaskRecurrence("WEEKLY:FUNDAY"))
        assertFalse(isSupportedTaskRecurrence("YEARLY"))
    }

}
