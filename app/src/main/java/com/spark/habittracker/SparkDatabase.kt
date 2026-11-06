package com.spark.habittracker

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val note: String, val category: String,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledDays: String = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
    val reminderTime: String = "08:00", val isEnabled: Boolean = true
)

@Entity(tableName = "habit_logs", indices = [Index(value = ["habitId", "date"], unique = true)], foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)])
data class HabitLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val habitId: Long, val date: String, val isCompleted: Boolean, val completionTimestamp: Long = System.currentTimeMillis())

@Entity(tableName = "user_settings")
data class UserSettingsEntity(@PrimaryKey val id: Int = 1, val theme: String = "neon", val accent: String = "violet", val reducedMotion: Boolean = false)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt DESC") fun observeHabits(): Flow<List<HabitEntity>>
    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1") suspend fun findHabit(id: Long): HabitEntity?
    @Query("SELECT * FROM habit_logs WHERE date = :date") fun observeLogs(date: String): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM habit_logs") fun observeAllLogs(): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM habits") suspend fun getAllHabits(): List<HabitEntity>
    @Query("SELECT * FROM habit_logs") suspend fun getAllLogs(): List<HabitLogEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun restoreHabits(items: List<HabitEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun restoreLogs(items: List<HabitLogEntity>)
    @Query("SELECT * FROM user_settings WHERE id = 1") fun observeSettings(): Flow<UserSettingsEntity?>
    @Query("SELECT * FROM user_settings WHERE id = 1") suspend fun getSettings(): UserSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(settings: UserSettingsEntity)
    @Insert suspend fun insertHabit(habit: HabitEntity): Long
    @Update suspend fun updateHabit(habit: HabitEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLog(log: HabitLogEntity)
    @Query("DELETE FROM habit_logs WHERE habitId = :id") suspend fun deleteLogs(id: Long)
    @Query("DELETE FROM habits WHERE id = :id") suspend fun deleteHabit(id: Long)
}

@Database(entities = [HabitEntity::class, HabitLogEntity::class, UserSettingsEntity::class, TaskEntity::class, TaskSubtaskEntity::class, TaskCompletionEntity::class, GoalEntity::class, GoalTaskLinkEntity::class, GoalHabitLinkEntity::class, FocusSessionEntity::class, ReminderEntity::class], version = 6, exportSchema = false)
abstract class SparkDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
    abstract fun productivityDao(): ProductivityDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS habits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL, note TEXT NOT NULL, category TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO habits (id, title, note, category, createdAt)
                    SELECT id, title, description, category, CAST(strftime('%s','now') AS INTEGER) * 1000 FROM tasks""")
                db.execSQL("""CREATE TABLE legacy_habit_metadata (
                    habitId INTEGER NOT NULL PRIMARY KEY, scheduledDays TEXT NOT NULL,
                    reminderTime TEXT NOT NULL, isEnabled INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO legacy_habit_metadata (habitId, scheduledDays, reminderTime, isEnabled)
                    SELECT id, scheduledDays, reminderTime, isEnabled FROM tasks""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS habit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    habitId INTEGER NOT NULL, date TEXT NOT NULL, isCompleted INTEGER NOT NULL,
                    FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_habit_logs_habitId_date ON habit_logs(habitId, date)")
                db.execSQL("""INSERT INTO habit_logs (id, habitId, date, isCompleted)
                    SELECT id, taskId, date, isCompleted FROM task_logs
                    WHERE taskId IN (SELECT id FROM habits)""")
                db.execSQL("""CREATE TABLE legacy_log_metadata (
                    logId INTEGER NOT NULL PRIMARY KEY, completionTimestamp INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO legacy_log_metadata (logId, completionTimestamp)
                    SELECT id, completionTimestamp FROM task_logs""")
                db.execSQL("DROP TABLE task_logs")
                db.execSQL("DROP TABLE tasks")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN startAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN endAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceRule TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceParentId INTEGER")
                db.execSQL("""CREATE TABLE task_subtasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    taskId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    isCompleted INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX index_task_subtasks_taskId ON task_subtasks(taskId)")
                db.execSQL("""CREATE TABLE task_completion_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    taskId INTEGER NOT NULL,
                    completed INTEGER NOT NULL,
                    activityDate TEXT NOT NULL,
                    completedAt INTEGER NOT NULL,
                    FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX index_task_completion_history_taskId ON task_completion_history(taskId)")
                db.execSQL("CREATE INDEX index_task_completion_history_completedAt ON task_completion_history(completedAt)")
                db.execSQL("""CREATE TABLE goals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    category TEXT NOT NULL,
                    targetValue REAL NOT NULL,
                    unit TEXT NOT NULL,
                    period TEXT NOT NULL,
                    startDate TEXT NOT NULL,
                    targetDate TEXT,
                    archived INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX index_goals_targetDate ON goals(targetDate)")
                db.execSQL("CREATE INDEX index_goals_archived ON goals(archived)")
                db.execSQL("""CREATE TABLE goal_task_links (
                    goalId INTEGER NOT NULL,
                    taskId INTEGER NOT NULL,
                    PRIMARY KEY(goalId, taskId),
                    FOREIGN KEY(goalId) REFERENCES goals(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX index_goal_task_links_taskId ON goal_task_links(taskId)")
                db.execSQL("""CREATE TABLE goal_habit_links (
                    goalId INTEGER NOT NULL,
                    habitId INTEGER NOT NULL,
                    PRIMARY KEY(goalId, habitId),
                    FOREIGN KEY(goalId) REFERENCES goals(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX index_goal_habit_links_habitId ON goal_habit_links(habitId)")
                db.execSQL("""CREATE TABLE focus_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    mode TEXT NOT NULL,
                    startedAt INTEGER NOT NULL,
                    plannedDurationSeconds INTEGER NOT NULL,
                    actualDurationSeconds INTEGER NOT NULL,
                    pausedAt INTEGER,
                    accumulatedPausedSeconds INTEGER NOT NULL,
                    completed INTEGER NOT NULL,
                    endedAt INTEGER,
                    linkedTaskId INTEGER
                )""")
                db.execSQL("CREATE INDEX index_focus_sessions_startedAt ON focus_sessions(startedAt)")
                db.execSQL("CREATE INDEX index_focus_sessions_completed ON focus_sessions(completed)")
                db.execSQL("""CREATE TABLE reminders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    ownerType TEXT NOT NULL,
                    ownerId INTEGER NOT NULL,
                    triggerAt INTEGER NOT NULL,
                    enabled INTEGER NOT NULL,
                    snoozedUntil INTEGER,
                    createdAt INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX index_reminders_ownerType_ownerId ON reminders(ownerType, ownerId)")
                db.execSQL("CREATE INDEX index_reminders_triggerAt ON reminders(triggerAt)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The legacy schema had no habit creation date. Use the earliest
                // stored activity date as a safe lower bound so migrated history
                // is not hidden by a migration-time timestamp.
                db.execSQL("""UPDATE habits
                    SET createdAt = COALESCE(
                        (SELECT MIN(CAST(strftime('%s', date) AS INTEGER) * 1000) FROM habit_logs WHERE habitId = habits.id AND date < date(habits.createdAt / 1000, 'unixepoch')),
                        createdAt
                    )""")
                db.execSQL("""CREATE TABLE tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    category TEXT NOT NULL,
                    dueDate TEXT,
                    priority INTEGER NOT NULL,
                    tags TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    completedAt INTEGER,
                    archived INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_dueDate ON tasks(dueDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_archived_completed ON tasks(archived, completed)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_priority ON tasks(priority)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN scheduledDays TEXT NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY'")
                db.execSQL("ALTER TABLE habits ADD COLUMN reminderTime TEXT NOT NULL DEFAULT '08:00'")
                db.execSQL("ALTER TABLE habits ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN completionTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""CREATE TABLE IF NOT EXISTS legacy_habit_metadata (
                    habitId INTEGER NOT NULL PRIMARY KEY, scheduledDays TEXT NOT NULL,
                    reminderTime TEXT NOT NULL, isEnabled INTEGER NOT NULL
                )""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS legacy_log_metadata (
                    logId INTEGER NOT NULL PRIMARY KEY, completionTimestamp INTEGER NOT NULL
                )""")
                db.execSQL("UPDATE habits SET scheduledDays = COALESCE((SELECT scheduledDays FROM legacy_habit_metadata WHERE habitId = habits.id), scheduledDays)")
                db.execSQL("UPDATE habits SET reminderTime = COALESCE((SELECT reminderTime FROM legacy_habit_metadata WHERE habitId = habits.id), reminderTime)")
                db.execSQL("UPDATE habits SET isEnabled = COALESCE((SELECT isEnabled FROM legacy_habit_metadata WHERE habitId = habits.id), isEnabled)")
                db.execSQL("UPDATE habit_logs SET completionTimestamp = COALESCE((SELECT completionTimestamp FROM legacy_log_metadata WHERE logId = habit_logs.id), completionTimestamp)")
                db.execSQL("DROP TABLE IF EXISTS legacy_habit_metadata")
                db.execSQL("DROP TABLE IF EXISTS legacy_log_metadata")
                db.execSQL("""CREATE TABLE IF NOT EXISTS user_settings (
                    id INTEGER NOT NULL PRIMARY KEY, theme TEXT NOT NULL, accent TEXT NOT NULL, reducedMotion INTEGER NOT NULL
                )""")
                db.execSQL("INSERT OR IGNORE INTO user_settings (id, theme, accent, reducedMotion) VALUES (1, 'neon', 'violet', 0)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderAt INTEGER")
            }
        }
    }
}
