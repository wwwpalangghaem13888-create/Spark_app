package com.spark.habittracker
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class SparkPalette(
    val background: Color, val surface: Color, val surfaceAlt: Color,
    val accent: Color, val accentLight: Color, val secondary: Color,
    val muted: Color, val foreground: Color
)
private val LocalSparkPalette = compositionLocalOf {
    SparkPalette(Color(0xFF090716), Color(0xFF151127), Color(0xFF241A43), Color(0xFF8B45FF), Color(0xFFD6B8FF), Color(0xFF72DDE0), Color(0xFFA9A0C2), Color(0xFFF8F5FF))
}
private val Ink: Color @Composable get() = LocalSparkPalette.current.background
private val Panel: Color @Composable get() = LocalSparkPalette.current.surface
private val PanelAlt: Color @Composable get() = LocalSparkPalette.current.surfaceAlt
private val Violet: Color @Composable get() = LocalSparkPalette.current.accent
private val VioletLight: Color @Composable get() = LocalSparkPalette.current.accentLight
private val Cyan: Color @Composable get() = LocalSparkPalette.current.secondary
private val Muted: Color @Composable get() = LocalSparkPalette.current.muted
private val White: Color @Composable get() = LocalSparkPalette.current.foreground

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
    @Query("SELECT * FROM habit_logs WHERE date = :date") fun observeLogs(date: String): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM habit_logs") fun observeAllLogs(): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM user_settings WHERE id = 1") fun observeSettings(): Flow<UserSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(settings: UserSettingsEntity)
    @Insert suspend fun insertHabit(habit: HabitEntity): Long
    @Update suspend fun updateHabit(habit: HabitEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLog(log: HabitLogEntity)
    @Query("DELETE FROM habit_logs WHERE habitId = :id") suspend fun deleteLogs(id: Long)
    @Query("DELETE FROM habits WHERE id = :id") suspend fun deleteHabit(id: Long)
}

@Database(entities = [HabitEntity::class, HabitLogEntity::class, UserSettingsEntity::class, TaskEntity::class], version = 4, exportSchema = false)
abstract class SparkDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao

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
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
    }
}

class MainActivity : ComponentActivity() {
    private val db by lazy { Room.databaseBuilder(applicationContext, SparkDatabase::class.java, "spark_tracker.db").addMigrations(SparkDatabase.MIGRATION_1_2, SparkDatabase.MIGRATION_2_3, SparkDatabase.MIGRATION_3_4).build() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by db.habitDao().observeSettings().collectAsState(initial = null)
            SparkTheme(settings ?: UserSettingsEntity()) { SparkApp(db.habitDao(), db) }
        }
    }
}

@Composable
private fun SparkTheme(settings: UserSettingsEntity, content: @Composable () -> Unit) {
    val accent = when (settings.accent) {
        "cyan" -> Color(0xFF42DCE8)
        "lime" -> Color(0xFFC5F36A)
        "pink" -> Color(0xFFFF75C8)
        else -> Color(0xFF9A63FF)
    }
    val background = when (settings.theme) {
        "minimal" -> Color(0xFF101820)
        "dark" -> Color(0xFF111318)
        else -> Color(0xFF090716)
    }
    val surface = when (settings.theme) {
        "minimal" -> Color(0xFF1B2731)
        "dark" -> Color(0xFF20232A)
        else -> Color(0xFF151127)
    }
    val palette = SparkPalette(background, surface, surface.copy(alpha = 0.78f), accent, accent.copy(alpha = 0.75f), Color(0xFF72DDE0), Color(0xFFA9A0C2), Color(0xFFF8F5FF))
    CompositionLocalProvider(LocalSparkPalette provides palette, LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = darkColorScheme(primary = accent, secondary = palette.secondary, background = background, surface = surface, onPrimary = Color.Black, onBackground = palette.foreground, onSurface = palette.foreground), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkApp(dao: HabitDao, db: SparkDatabase) {
    var tab by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HabitEntity?>(null) }
    var taskEditorOpen by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    val taskRepository = remember(db) { TaskRepository(db.taskDao()) }
    val tasks by taskRepository.observeActiveTasks().collectAsState(initial = emptyList())
    val habits by dao.observeHabits().collectAsState(initial = emptyList())
    val logs by remember(date) { dao.observeLogs(date.toString()) }.collectAsState(initial = emptyList())
    val todayLogs by remember { dao.observeLogs(LocalDate.now().toString()) }.collectAsState(initial = emptyList())
    val allLogs by dao.observeAllLogs().collectAsState(initial = emptyList())
    val savedSettings by dao.observeSettings().collectAsState(initial = null)
    val settings = savedSettings ?: UserSettingsEntity()
    val scope = rememberCoroutineScope()
    LaunchedEffect(savedSettings) { if (savedSettings == null) dao.saveSettings(UserSettingsEntity()) }
    val done = logs.filter { it.isCompleted }.map { it.habitId }.toSet()
    val validHabitIds = habits.map { it.id }.toSet()
    val scheduledHabits = habits.filter { habit ->
        habit.isEnabled &&
            !date.isBefore(Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()) &&
            date.dayOfWeek.name in habit.scheduledDays.split(",")
    }
    val todayHabits = habits.filter { habit ->
        habit.isEnabled &&
            !LocalDate.now().isBefore(Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()) &&
            LocalDate.now().dayOfWeek.name in habit.scheduledDays.split(",")
    }
    val completedCount = done.intersect(scheduledHabits.map { it.id }.toSet()).size
    val progress = calculateCompletionRate(completedCount, scheduledHabits.size)
    Scaffold(containerColor = Ink,
        topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.spark_logo), contentDescription = "لوگوی Spark", modifier = Modifier.size(42.dp))
            Column {
                Text("SPARK", color = VioletLight, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(listOf("عادت‌های روزانه", "مدیریت وظایف", "مرور روزها", "گزارش عملکرد", "سطح و دستاوردها")[tab], color = Muted, fontSize = 12.sp)
            }
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)) },
        bottomBar = { NavigationBar(containerColor = Panel) {
            listOf("خانه" to Icons.Default.Home, "وظایف" to Icons.Default.Checklist, "تقویم" to Icons.Default.CalendarMonth, "آمار" to Icons.Default.Insights, "پروفایل" to Icons.Default.EmojiEvents).forEachIndexed { i, item ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i; if (i == 0) date = LocalDate.now() }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, selectedTextColor = Violet, indicatorColor = PanelAlt, unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        } },
        floatingActionButton = { if (tab == 0 || tab == 1) FloatingActionButton(onClick = { if (tab == 1) { editingTask = null; taskEditorOpen = true } else { editing = null; editorOpen = true } }, containerColor = Violet, contentColor = Ink) { Icon(Icons.Default.Add, if (tab == 1) "افزودن وظیفه" else "افزودن عادت") } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> Dashboard(scheduledHabits, done, progress, settings.reducedMotion,
                    onToggle = { h, wasDone -> scope.launch { dao.saveLog(HabitLogEntity(habitId = h.id, date = date.toString(), isCompleted = !wasDone)) } },
                    onEdit = { editing = it; editorOpen = true },
                    onDelete = { h -> scope.launch { db.withTransaction { dao.deleteLogs(h.id); dao.deleteHabit(h.id) } } })
                1 -> TaskListScreen(
                    tasks = tasks,
                    onToggle = { task, completed -> scope.launch { taskRepository.setCompleted(task, completed) } },
                    onEdit = { editingTask = it; taskEditorOpen = true },
                    onArchive = { task -> scope.launch { taskRepository.archive(task.id) } },
                    onDelete = { task -> scope.launch { taskRepository.deletePermanently(task.id) } }
                )
                2 -> SparkCalendarScreen(
                    selectedDate = date,
                    onDateChange = { date = it },
                    habits = habits,
                    tasks = tasks,
                    logs = allLogs,
                    onToggleHabit = { habit, completed ->
                        scope.launch {
                            dao.saveLog(HabitLogEntity(habitId = habit.id, date = date.toString(), isCompleted = completed))
                        }
                    },
                    onToggleTask = { task, completed ->
                        scope.launch { taskRepository.setCompleted(task, completed) }
                    }
                )
                3 -> Statistics(habits, allLogs)
                else -> ProgressHub(todayHabits, allLogs, todayLogs, settings) { updated -> scope.launch { dao.saveSettings(updated) } }
            }
        }
    }
    if (editorOpen) HabitEditor(editing, { editorOpen = false }) { title, note, category ->
        scope.launch {
            val habit = HabitEntity(
                id = editing?.id ?: 0, title = title.trim(), note = note.trim(),
                category = category.trim().ifBlank { "عمومی" },
                createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                scheduledDays = editing?.scheduledDays ?: "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
                reminderTime = editing?.reminderTime ?: "08:00", isEnabled = editing?.isEnabled ?: true
            )
            if (editing == null) dao.insertHabit(habit) else dao.updateHabit(habit)
        }
        editorOpen = false
    }
    if (taskEditorOpen) TaskEditorDialog(
        initial = editingTask,
        onDismiss = { taskEditorOpen = false; editingTask = null },
        onSave = { task ->
            scope.launch {
                taskRepository.save(task)
                taskEditorOpen = false
                editingTask = null
            }
        }
    )
}

@Composable
private fun Dashboard(habits: List<HabitEntity>, done: Set<Long>, progress: Float, reducedMotion: Boolean, onToggle: (HabitEntity, Boolean) -> Unit, onEdit: (HabitEntity) -> Unit, onDelete: (HabitEntity) -> Unit) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 1100, easing = FastOutSlowInEasing),
        label = "daily-progress"
    )
    val percentage = (animatedProgress * 100f).roundToInt().coerceIn(0, 100)
    val completedCount = done.intersect(habits.map { it.id }.toSet()).size
    LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Color(0xFF130D27), Ink))), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF241644), Panel, Color(0xFF17112D)))).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                    val palette = LocalSparkPalette.current
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(palette.surfaceAlt, style = Stroke(10.dp.toPx()))
                        drawArc(
                            brush = Brush.sweepGradient(listOf(palette.accentLight, palette.accent, Color(0xFF5420C5), palette.accentLight)),
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawCircle(Color(0x558B45FF), radius = size.minDimension * 0.34f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$percentage٪", color = White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        Text("امروز", color = Muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("پیشرفت امروز", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (habits.isEmpty()) "اولین عادتت را اضافه کن." else "$completedCount از ${habits.size} عادت انجام شده", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(9.dp))
                    Text(if (habits.isNotEmpty() && completedCount == habits.size) "همه کارها انجام شد!" else "قدم‌های کوچک، تغییرهای بزرگ.", color = VioletLight, fontSize = 11.sp)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("کل عادت‌ها", habits.size.toString(), Modifier.weight(1f))
            Metric("انجام‌شده", completedCount.toString(), Modifier.weight(1f))
            Metric("باقی‌مانده", (habits.size - completedCount).coerceAtLeast(0).toString(), Modifier.weight(1f))
        } }
        item { Text("فهرست امروز", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        if (habits.isEmpty()) item { EmptyState() } else items(habits, key = { it.id }) { h -> HabitCard(h, h.id in done, { onToggle(h, h.id in done) }, { onEdit(h) }, { onDelete(h) }) }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Panel).padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Cyan, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, tint = Violet, modifier = Modifier.size(34.dp))
        Text("هنوز عادتی ثبت نشده", color = White, fontWeight = FontWeight.Bold)
        Text("با دکمه + اولین عادتت را بساز.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun HabitCard(h: HabitEntity, completed: Boolean, toggle: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
    val palette = LocalSparkPalette.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (completed) Violet else Color.Transparent).clickable(onClick = toggle), contentAlignment = Alignment.Center) {
            if (completed) Icon(Icons.Default.Check, null, tint = Ink) else Canvas(Modifier.fillMaxSize()) { drawCircle(palette.muted, style = Stroke(1.5.dp.toPx())) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = toggle)) {
            Text(h.title, color = if (completed) Muted else White, fontWeight = FontWeight.SemiBold, textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None)
            Text(listOf(h.category, h.note, h.reminderTime).filter { it.isNotBlank() }.joinToString(" • "), color = Muted, fontSize = 11.sp, maxLines = 2)
        }
        Box {
            TextButton(onClick = { menu = true }) { Text("•••", color = Muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("ویرایش") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; edit() })
                DropdownMenuItem(text = { Text("حذف") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; delete() })
            }
        }
    }
}

@Composable
private fun History(date: LocalDate, changeDate: (LocalDate) -> Unit, habits: List<HabitEntity>, done: Set<Long>) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE، d MMMM", Locale("fa")) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { changeDate(date.minusDays(1)) }) { Icon(Icons.Default.ChevronRight, "روز قبل", tint = White) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(date.format(formatter), color = White, fontWeight = FontWeight.Bold); Text(date.toString(), color = Muted, fontSize = 11.sp) }
            IconButton(onClick = { if (date.isBefore(LocalDate.now())) changeDate(date.plusDays(1)) }) { Icon(Icons.Default.ChevronLeft, "روز بعد", tint = if (date.isBefore(LocalDate.now())) White else Muted) }
        }
        Text("${done.size} از ${habits.size} عادت انجام شده", color = Violet, fontWeight = FontWeight.Bold)
        if (habits.isEmpty()) EmptyState() else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(habits, key = { it.id }) { h ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (h.id in done) Icons.Default.CheckCircle else Icons.Default.Circle, null, tint = if (h.id in done) Violet else Muted)
                    Spacer(Modifier.width(12.dp)); Text(h.title, color = White, modifier = Modifier.weight(1f))
                    Text(if (h.id in done) "انجام شد" else "ثبت نشده", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Statistics(habits: List<HabitEntity>, logs: List<HabitLogEntity>) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val byDate = logs.filter { it.isCompleted }.groupBy { it.date }
    val eligibleHabits = habits.filter { it.isEnabled }
    val completed = logs.count { log ->
        log.isCompleted && eligibleHabits.any { habit ->
            habit.id == log.habitId && runCatching {
                val day = LocalDate.parse(log.date)
                day in days &&
                    !day.isBefore(Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()) &&
                    day.dayOfWeek.name in habit.scheduledDays.split(",")
            }.getOrDefault(false)
        }
    }
    val possible = eligibleHabits.sumOf { habit ->
        days.count { day ->
            !day.isBefore(Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()) &&
                day.dayOfWeek.name in habit.scheduledDays.split(",")
        }
    }
    val rate = (calculateCompletionRate(completed, possible) * 100f).roundToInt()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(18.dp)) {
            Text("عملکرد هفت روز اخیر", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("$rate٪", color = Violet, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("نسبت ثبت‌های موفق به تعداد عادت‌ها در این هفته", color = Muted, fontSize = 11.sp)
        } }
        item { Text("روند روزانه", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        item { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            days.forEach { day ->
                val count = byDate[day.toString()]?.count { log ->
                    eligibleHabits.any { habit ->
                        habit.id == log.habitId &&
                            !day.isBefore(Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()) &&
                            day.dayOfWeek.name in habit.scheduledDays.split(",")
                    }
                } ?: 0
                val expected = eligibleHabits.count { habit ->
                    !day.isBefore(Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()) &&
                        day.dayOfWeek.name in habit.scheduledDays.split(",")
                }
                val ratio = calculateCompletionRate(count, expected)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.width(24.dp).height(100.dp), contentAlignment = Alignment.BottomCenter) { Box(Modifier.fillMaxWidth().fillMaxHeight(ratio).clip(RoundedCornerShape(8.dp)).background(Violet)) }
                    Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("fa")), color = Muted, fontSize = 10.sp)
                }
            }
        } }
        item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
            Text("جمع‌بندی", color = White, fontWeight = FontWeight.Bold)
            Text("تعداد عادت‌ها: ${habits.size}", color = Muted, fontSize = 13.sp)
            Text("ثبت‌های موفق در این هفته: $completed", color = Muted, fontSize = 13.sp)
            val monthStart = today.withDayOfMonth(1)
            val monthlyCount = logs.count { it.isCompleted && runCatching { val d = LocalDate.parse(it.date); !d.isBefore(monthStart) && !d.isAfter(today) }.getOrDefault(false) }
            Text("ثبت‌های موفق این ماه: $monthlyCount", color = Muted, fontSize = 13.sp)
            if (habits.isEmpty()) Text("برای شروع آمار، یک عادت اضافه کن.", color = Violet, fontSize = 12.sp)
        } }
    }
}


@Composable
private fun ProgressHub(habits: List<HabitEntity>, allLogs: List<HabitLogEntity>, todayLogs: List<HabitLogEntity>, settings: UserSettingsEntity, onSettingsChange: (UserSettingsEntity) -> Unit) {
    val completedLogs = allLogs.filter { it.isCompleted }
    val xp = calculateExperience(completedLogs.size)
    val level = calculateLevel(xp)
    val xpInLevel = xp % 100
    val activeCount = habits.count { it.isEnabled }
    val todayDone = todayLogs.count { it.isCompleted && habits.any { h -> h.id == it.habitId && h.isEnabled } }
    val challengeTarget = minOf(3, activeCount)
    val challengeProgress = if (challengeTarget == 0) 0f else (todayDone.toFloat() / challengeTarget).coerceIn(0f, 1f)
    val milestones = listOf(1 to "اولین جرقه", 10 to "ده قدم", 30 to "پیوستگی", 100 to "صد ثبت موفق")
    val recentStart = LocalDate.now().minusDays(6)
    val previousStart = LocalDate.now().minusDays(13)
    val weekDone = completedLogs.count { runCatching { val d = LocalDate.parse(it.date); d >= recentStart && d <= LocalDate.now() }.getOrDefault(false) }
    val previousWeek = completedLogs.count { runCatching { val d = LocalDate.parse(it.date); d >= previousStart && d < recentStart }.getOrDefault(false) }
    val insight = when {
        completedLogs.isEmpty() -> "هنوز داده‌ی کافی برای تحلیل وجود ندارد. چند عادت را ثبت کن تا الگوها قابل بررسی شوند."
        weekDone > previousWeek -> "ثبت‌های موفق این هفته نسبت به هفت روز قبل بیشتر بوده است. همین روند را حفظ کن."
        weekDone < previousWeek -> "ثبت‌های موفق این هفته کمتر از هفته‌ی قبل است. هدف روزانه را کوچک‌تر و قابل انجام‌تر کن."
        else -> "فعالیت این هفته تقریباً هم‌سطح هفته‌ی قبل است. تداوم را حفظ کن."
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF251744), Panel, Color(0xFF111020)))).padding(20.dp)) {
                Text("SPARK LEVEL", color = VioletLight, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("سطح $level", color = White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(10.dp))
                    Text("$xp XP", color = Cyan, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(PanelAlt)) {
                    Box(Modifier.fillMaxWidth(xpInLevel / 100f).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Violet, Cyan))))
                }
                Text("$xpInLevel از ۱۰۰ XP تا سطح بعدی", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                Text("هر ثبت موفق عادت = ۱۰ XP. امتیازها از سوابق ذخیره‌شده محاسبه می‌شوند.", color = Muted, fontSize = 10.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Text("چالش امروز", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(if (challengeTarget == 0) "برای شروع، یک عادت فعال بساز." else "انجام $challengeTarget عادت امروز", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = challengeProgress, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Cyan, trackColor = PanelAlt)
                Text(if (challengeTarget == 0) "بدون عادت فعال" else "$todayDone از $challengeTarget", color = Cyan, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Text("دستاوردها", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                milestones.forEach { (required, name) ->
                    val unlocked = completedLogs.size >= required
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock, null, tint = if (unlocked) Cyan else Muted)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = if (unlocked) White else Muted, fontWeight = FontWeight.SemiBold)
                            Text("$required ثبت موفق موردنیاز", color = Muted, fontSize = 10.sp)
                        }
                        Text(if (unlocked) "باز شد" else "${completedLogs.size}/$required", color = if (unlocked) Cyan else Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Text("بینش هوشمند", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(7.dp))
                Text(insight, color = Muted, fontSize = 13.sp)
                Text("مبنای تحلیل: $weekDone ثبت این هفته، $previousWeek ثبت در هفته‌ی قبل", color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ظاهر برنامه", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("تم", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("neon" to "نئون", "dark" to "تیره", "minimal" to "مینیمال").forEach { (key, label) ->
                        FilterChip(selected = settings.theme == key, onClick = { onSettingsChange(settings.copy(theme = key)) }, label = { Text(label) })
                    }
                }
                Text("رنگ تأکیدی", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("violet" to "بنفش", "cyan" to "فیروزه‌ای", "lime" to "سبز", "pink" to "صورتی").forEach { (key, label) ->
                        FilterChip(selected = settings.accent == key, onClick = { onSettingsChange(settings.copy(accent = key)) }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("کاهش انیمیشن‌ها", color = White)
                        Text("برای حرکت کمتر و مصرف پایین‌تر", color = Muted, fontSize = 10.sp)
                    }
                    Switch(checked = settings.reducedMotion, onCheckedChange = { onSettingsChange(settings.copy(reducedMotion = it)) })
                }
            }
        }
    }
}

@Composable
private fun HabitEditor(initial: HabitEntity?, dismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "عمومی") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, containerColor = Panel,
        title = { Text(if (initial == null) "عادت جدید" else "ویرایش عادت", color = White) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(title, { title = it; error = false }, label = { Text("عنوان عادت") }, singleLine = true)
            OutlinedTextField(category, { category = it }, label = { Text("دسته‌بندی") }, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") }, minLines = 2)
            if (error) Text("عنوان نمی‌تواند خالی باشد.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { if (title.isBlank()) error = true else save(title, note, category) }) { Text("ذخیره", color = Violet) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("انصراف", color = Muted) } })
}
