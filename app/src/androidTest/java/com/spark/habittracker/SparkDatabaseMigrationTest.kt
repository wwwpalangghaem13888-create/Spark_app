package com.spark.habittracker

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds the real v6 on-disk schema, inserts linked user data, then opens it
 * through the production migration registry. This is intentionally not an
 * in-memory/current-schema test: it exercises the 6 -> 7 upgrade path.
 */
@RunWith(AndroidJUnit4::class)
class SparkDatabaseMigrationTest {

    @Test
    fun migratesVersionSixWithoutLosingDataAndEnforcesRelations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("spark_tracker.db")
        file.parentFile?.mkdirs()
        file.delete()
        createVersionSixDatabase(file.absolutePath)

        val db = Room.databaseBuilder(context, SparkDatabase::class.java, "spark_tracker.db")
            .addMigrations(*SparkDatabaseFactory.migrations)
            .build()

        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(7, sql.version)

            assertEquals(1, db.habitDao().getAllHabits().size)
            assertEquals(1, db.habitDao().getAllLogs().size)
            assertEquals(1, db.taskDao().getAllTasks().size)
            assertEquals(1, db.productivityDao().getAllSubtasks().size)
            assertEquals(1, db.productivityDao().getAllTaskHistory().size)
            assertEquals(1, db.productivityDao().getAllGoals().size)
            assertEquals(1, db.productivityDao().getAllGoalTaskLinks().size)
            assertEquals(1, db.productivityDao().getAllGoalHabitLinks().size)
            assertEquals(1, db.productivityDao().getAllFocusSessions().size)
            assertEquals(3, db.productivityDao().getAllReminders().size)

            val migratedSession = db.productivityDao().getAllFocusSessions().single()
            assertEquals(20L, migratedSession.linkedTaskId)

            val foreignKeyViolations = sql.query("PRAGMA foreign_key_check")
            try {
                assertFalse("Migrated database must have no foreign-key violations", foreignKeyViolations.moveToFirst())
            } finally {
                foreignKeyViolations.close()
            }

            // Permanent task deletion cascades dependent task data and goal links,
            // nulls the focus-session link, and removes task-owned reminders.
            assertEquals(1, db.taskDao().deletePermanently(20L))
            assertTrue(db.productivityDao().getAllSubtasks().isEmpty())
            assertTrue(db.productivityDao().getAllTaskHistory().isEmpty())
            assertTrue(db.productivityDao().getAllGoalTaskLinks().isEmpty())
            assertNull(db.productivityDao().getAllFocusSessions().single().linkedTaskId)
            assertTrue(db.productivityDao().getAllReminders().none { it.ownerType == "TASK" })

            // Habit deletion is one transaction and cascades logs, goal links and reminders.
            assertEquals(1, db.habitDao().deleteHabitAndDependents(10L))
            assertTrue(db.habitDao().getAllLogs().isEmpty())
            assertTrue(db.productivityDao().getAllGoalHabitLinks().isEmpty())
            assertTrue(db.productivityDao().getAllReminders().none { it.ownerType == "HABIT" })

            var invalidReminderRejected = false
            try {
                db.productivityDao().insertReminder(
                    ReminderEntity(ownerType = "TASK", ownerId = 999L, triggerAt = 2_000L)
                )
            } catch (_: SQLiteConstraintException) {
                invalidReminderRejected = true
            }
            assertTrue("A reminder must not reference a missing owner", invalidReminderRejected)
        } finally {
            db.close()
            file.delete()
        }
    }

    private fun createVersionSixDatabase(path: String) {
        val legacy = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            legacy.execSQL("PRAGMA foreign_keys = ON")
            legacy.execSQL("""CREATE TABLE habits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL, note TEXT NOT NULL, category TEXT NOT NULL,
                createdAt INTEGER NOT NULL, scheduledDays TEXT NOT NULL,
                reminderTime TEXT NOT NULL, isEnabled INTEGER NOT NULL
            )""")
            legacy.execSQL("""CREATE TABLE habit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                habitId INTEGER NOT NULL, date TEXT NOT NULL, isCompleted INTEGER NOT NULL,
                completionTimestamp INTEGER NOT NULL,
                FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )""")
            legacy.execSQL("CREATE UNIQUE INDEX index_habit_logs_habitId_date ON habit_logs(habitId, date)")
            legacy.execSQL("""CREATE TABLE user_settings (
                id INTEGER NOT NULL PRIMARY KEY, theme TEXT NOT NULL,
                accent TEXT NOT NULL, reducedMotion INTEGER NOT NULL
            )""")
            legacy.execSQL("""CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL,
                dueDate TEXT, startAt INTEGER, endAt INTEGER, recurrenceRule TEXT,
                recurrenceParentId INTEGER, priority INTEGER NOT NULL, tags TEXT NOT NULL,
                completed INTEGER NOT NULL, completedAt INTEGER, archived INTEGER NOT NULL,
                createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, reminderAt INTEGER
            )""")
            legacy.execSQL("CREATE INDEX index_tasks_dueDate ON tasks(dueDate)")
            legacy.execSQL("CREATE INDEX index_tasks_archived_completed ON tasks(archived, completed)")
            legacy.execSQL("CREATE INDEX index_tasks_priority ON tasks(priority)")
            legacy.execSQL("""CREATE TABLE task_subtasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL,
                title TEXT NOT NULL, isCompleted INTEGER NOT NULL, position INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )""")
            legacy.execSQL("CREATE INDEX index_task_subtasks_taskId ON task_subtasks(taskId)")
            legacy.execSQL("""CREATE TABLE task_completion_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL,
                completed INTEGER NOT NULL, activityDate TEXT NOT NULL, completedAt INTEGER NOT NULL,
                FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )""")
            legacy.execSQL("CREATE INDEX index_task_completion_history_taskId ON task_completion_history(taskId)")
            legacy.execSQL("CREATE INDEX index_task_completion_history_completedAt ON task_completion_history(completedAt)")
            legacy.execSQL("""CREATE TABLE goals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL,
                description TEXT NOT NULL, category TEXT NOT NULL, targetValue REAL NOT NULL,
                unit TEXT NOT NULL, period TEXT NOT NULL, startDate TEXT NOT NULL,
                targetDate TEXT, archived INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )""")
            legacy.execSQL("CREATE INDEX index_goals_targetDate ON goals(targetDate)")
            legacy.execSQL("CREATE INDEX index_goals_archived ON goals(archived)")
            legacy.execSQL("""CREATE TABLE goal_task_links (
                goalId INTEGER NOT NULL, taskId INTEGER NOT NULL, PRIMARY KEY(goalId, taskId),
                FOREIGN KEY(goalId) REFERENCES goals(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )""")
            legacy.execSQL("CREATE INDEX index_goal_task_links_taskId ON goal_task_links(taskId)")
            legacy.execSQL("""CREATE TABLE goal_habit_links (
                goalId INTEGER NOT NULL, habitId INTEGER NOT NULL, PRIMARY KEY(goalId, habitId),
                FOREIGN KEY(goalId) REFERENCES goals(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )""")
            legacy.execSQL("CREATE INDEX index_goal_habit_links_habitId ON goal_habit_links(habitId)")
            legacy.execSQL("""CREATE TABLE focus_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mode TEXT NOT NULL,
                startedAt INTEGER NOT NULL, plannedDurationSeconds INTEGER NOT NULL,
                actualDurationSeconds INTEGER NOT NULL, pausedAt INTEGER,
                accumulatedPausedSeconds INTEGER NOT NULL, completed INTEGER NOT NULL,
                endedAt INTEGER, linkedTaskId INTEGER
            )""")
            legacy.execSQL("CREATE INDEX index_focus_sessions_startedAt ON focus_sessions(startedAt)")
            legacy.execSQL("CREATE INDEX index_focus_sessions_completed ON focus_sessions(completed)")
            legacy.execSQL("""CREATE TABLE reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerType TEXT NOT NULL,
                ownerId INTEGER NOT NULL, triggerAt INTEGER NOT NULL, enabled INTEGER NOT NULL,
                snoozedUntil INTEGER, createdAt INTEGER NOT NULL
            )""")
            legacy.execSQL("CREATE INDEX index_reminders_ownerType_ownerId ON reminders(ownerType, ownerId)")
            legacy.execSQL("CREATE INDEX index_reminders_triggerAt ON reminders(triggerAt)")

            legacy.execSQL("INSERT INTO habits VALUES (10, 'Read', '', 'Study', 1000, 'MONDAY', '08:00', 1)")
            legacy.execSQL("INSERT INTO habit_logs VALUES (100, 10, '2026-11-06', 1, 1000)")
            legacy.execSQL("INSERT INTO user_settings VALUES (1, 'neon', 'violet', 0)")
            legacy.execSQL("""INSERT INTO tasks VALUES (
                20, 'Legacy task', '', 'General', '2026-11-07', NULL, NULL, NULL,
                NULL, 1, '', 0, NULL, 0, 1000, 1000, NULL
            )""")
            legacy.execSQL("INSERT INTO task_subtasks VALUES (30, 20, 'Subtask', 0, 0, 1000)")
            legacy.execSQL("INSERT INTO task_completion_history VALUES (40, 20, 1, '2026-11-06', 1000)")
            legacy.execSQL("""INSERT INTO goals VALUES (
                50, 'Goal', '', 'General', 5.0, 'tasks', 'CUSTOM',
                '2026-11-01', '2026-11-30', 0, 1000, 1000
            )""")
            legacy.execSQL("INSERT INTO goal_task_links VALUES (50, 20)")
            legacy.execSQL("INSERT INTO goal_habit_links VALUES (50, 10)")
            legacy.execSQL("""INSERT INTO focus_sessions VALUES (
                60, 'POMODORO', 1000, 1500, 0, NULL, 0, 0, NULL, 20
            )""")
            legacy.execSQL("INSERT INTO reminders VALUES (70, 'TASK', 20, 2000, 1, NULL, 1000)")
            legacy.execSQL("INSERT INTO reminders VALUES (71, 'HABIT', 10, 2000, 1, NULL, 1000)")
            legacy.execSQL("INSERT INTO reminders VALUES (72, 'FOCUS', 60, 2000, 1, NULL, 1000)")
            legacy.execSQL("PRAGMA user_version = 6")
        } finally {
            legacy.close()
        }
    }
}
