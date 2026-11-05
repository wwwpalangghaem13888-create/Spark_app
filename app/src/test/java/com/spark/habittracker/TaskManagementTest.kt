package com.spark.habittracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskManagementTest {
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
}
