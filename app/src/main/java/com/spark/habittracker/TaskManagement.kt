package com.spark.habittracker

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * A task is a one-off actionable item. Habit schedules remain separate so
 * recurring behavior is not conflated with deadline-driven work.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["dueDate"]),
        Index(value = ["archived", "completed"]),
        Index(value = ["priority"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "عمومی",
    /** ISO-8601 local date (yyyy-MM-dd); null means no deadline. */
    val dueDate: String? = null,
    /** 0 = low, 1 = normal, 2 = high, 3 = urgent. */
    val priority: Int = 1,
    val tags: String = "",
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE archived = 0 ORDER BY completed ASC, priority DESC, CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, createdAt DESC")
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET completed = :completed, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id AND archived = 0")
    suspend fun setCompletion(id: Long, completed: Boolean, completedAt: Long?, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE tasks SET archived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Long, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deletePermanently(id: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE archived = 0 AND completed = 0 AND dueDate < :today")
    fun observeOverdueCount(today: String): Flow<Int>
}

/** Small repository boundary so UI code does not own task persistence rules. */
class TaskRepository(private val dao: TaskDao) {
    fun observeActiveTasks(): Flow<List<TaskEntity>> = dao.observeActiveTasks()

    suspend fun save(task: TaskEntity): Long {
        val normalized = task.copy(
            title = task.title.trim(),
            description = task.description.trim(),
            category = task.category.trim().ifBlank { "عمومی" },
            tags = task.tags.split(",").map(String::trim).filter(String::isNotBlank)
                .distinct().joinToString(","),
            priority = task.priority.coerceIn(0, 3),
            updatedAt = System.currentTimeMillis()
        )
        require(normalized.title.isNotBlank()) { "Task title must not be blank." }
        normalized.dueDate?.let(LocalDate::parse)
        return if (normalized.id == 0L) dao.insert(normalized)
        else {
            dao.update(normalized)
            normalized.id
        }
    }

    suspend fun setCompleted(task: TaskEntity, completed: Boolean) {
        dao.setCompletion(
            id = task.id,
            completed = completed,
            completedAt = if (completed) System.currentTimeMillis() else null
        )
    }

    suspend fun archive(taskId: Long) {
        dao.archive(taskId)
    }

    suspend fun deletePermanently(taskId: Long) {
        dao.deletePermanently(taskId)
    }
}

internal fun taskIsOverdue(task: TaskEntity, today: LocalDate): Boolean =
    !task.archived && !task.completed && task.dueDate?.let { LocalDate.parse(it).isBefore(today) } == true

internal fun taskPriorityLabel(priority: Int): String = when (priority.coerceIn(0, 3)) {
    0 -> "کم"
    2 -> "زیاد"
    3 -> "فوری"
    else -> "معمولی"
}
