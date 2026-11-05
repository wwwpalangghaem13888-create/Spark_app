package com.spark.habittracker
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private val Ink = Color(0xFF101318)
private val Panel = Color(0xFF1B2028)
private val PanelAlt = Color(0xFF29313B)
private val Lime = Color(0xFFC7F36B)
private val Cyan = Color(0xFF72DDE0)
private val Muted = Color(0xFF9AA5B1)
private val White = Color(0xFFF4F6F8)

@Entity(tableName = "habits")
data class HabitEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val note: String, val category: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "habit_logs", indices = [Index(value = ["habitId", "date"], unique = true)], foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)])
data class HabitLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val habitId: Long, val date: String, val isCompleted: Boolean)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt DESC") fun observeHabits(): Flow<List<HabitEntity>>
    @Query("SELECT * FROM habit_logs WHERE date = :date") fun observeLogs(date: String): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM habit_logs") fun observeAllLogs(): Flow<List<HabitLogEntity>>
    @Insert suspend fun insertHabit(habit: HabitEntity): Long
    @Update suspend fun updateHabit(habit: HabitEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLog(log: HabitLogEntity)
    @Query("DELETE FROM habit_logs WHERE habitId = :id") suspend fun deleteLogs(id: Long)
    @Query("DELETE FROM habits WHERE id = :id") suspend fun deleteHabit(id: Long)
}

@Database(entities = [HabitEntity::class, HabitLogEntity::class], version = 2, exportSchema = false)
abstract class SparkDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS habits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL, note TEXT NOT NULL, category TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO habits (id, title, note, category, createdAt)
                    SELECT id, title, description, category, CAST(strftime('%s','now') AS INTEGER) * 1000
                    FROM tasks""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS habit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    habitId INTEGER NOT NULL, date TEXT NOT NULL, isCompleted INTEGER NOT NULL,
                    FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_habit_logs_habitId_date ON habit_logs(habitId, date)")
                db.execSQL("""INSERT INTO habit_logs (id, habitId, date, isCompleted)
                    SELECT id, taskId, date, isCompleted FROM task_logs
                    WHERE taskId IN (SELECT id FROM habits)""")
                db.execSQL("DROP TABLE task_logs")
                db.execSQL("DROP TABLE tasks")
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val db by lazy { Room.databaseBuilder(applicationContext, SparkDatabase::class.java, "spark_tracker.db").addMigrations(SparkDatabase.MIGRATION_1_2).build() }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SparkTheme { SparkApp(db.habitDao(), db) } } }
}

@Composable
private fun SparkTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { MaterialTheme(colorScheme = darkColorScheme(primary = Lime, secondary = Cyan, background = Ink, surface = Panel, onPrimary = Ink, onBackground = White, onSurface = White), content = content) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkApp(dao: HabitDao, db: SparkDatabase) {
    var tab by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HabitEntity?>(null) }
    val habits by dao.observeHabits().collectAsState(initial = emptyList())
    val logs by remember(date) { dao.observeLogs(date.toString()) }.collectAsState(initial = emptyList())
    val allLogs by dao.observeAllLogs().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val done = logs.filter { it.isCompleted }.map { it.habitId }.toSet()
    val progress = if (habits.isEmpty()) 0f else done.intersect(habits.map { it.id }.toSet()).size.toFloat() / habits.size
    Scaffold(containerColor = Ink,
        topBar = { TopAppBar(title = { Column {
            Text("SPARK", color = Lime, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(listOf("عادت‌های روزانه", "مرور روزها", "گزارش عملکرد")[tab], color = Muted, fontSize = 12.sp)
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)) },
        bottomBar = { NavigationBar(containerColor = Panel) {
            listOf("خانه" to Icons.Default.Home, "تقویم" to Icons.Default.CalendarMonth, "آمار" to Icons.Default.Insights).forEachIndexed { i, item ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i; if (i == 0) date = LocalDate.now() }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Lime, selectedTextColor = Lime, indicatorColor = PanelAlt, unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        } },
        floatingActionButton = { if (tab == 0) FloatingActionButton(onClick = { editing = null; editorOpen = true }, containerColor = Lime, contentColor = Ink) { Icon(Icons.Default.Add, "افزودن عادت") } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> Dashboard(habits, done, progress,
                    onToggle = { h, wasDone -> scope.launch { dao.saveLog(HabitLogEntity(habitId = h.id, date = date.toString(), isCompleted = !wasDone)) } },
                    onEdit = { editing = it; editorOpen = true },
                    onDelete = { h -> scope.launch { db.withTransaction { dao.deleteLogs(h.id); dao.deleteHabit(h.id) } } })
                1 -> History(date, { date = it }, habits, done)
                else -> Statistics(habits, allLogs)
            }
        }
    }
    if (editorOpen) HabitEditor(editing, { editorOpen = false }) { title, note, category ->
        scope.launch {
            val habit = HabitEntity(id = editing?.id ?: 0, title = title.trim(), note = note.trim(), category = category.trim().ifBlank { "عمومی" }, createdAt = editing?.createdAt ?: System.currentTimeMillis())
            if (editing == null) dao.insertHabit(habit) else dao.updateHabit(habit)
        }
        editorOpen = false
    }
}

@Composable
private fun Dashboard(habits: List<HabitEntity>, done: Set<Long>, progress: Float, onToggle: (HabitEntity, Boolean) -> Unit, onEdit: (HabitEntity) -> Unit, onDelete: (HabitEntity) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(PanelAlt, style = Stroke(10.dp.toPx())); drawArc(Lime, -90f, 360f * progress, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)) }
                    Text("\${(progress * 100).toInt()}٪", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("پیشرفت امروز", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (habits.isEmpty()) "اولین عادتت را اضافه کن." else "\${done.size} از \${habits.size} عادت انجام شده", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (progress >= 1f && habits.isNotEmpty()) "همه کارها انجام شد!" else "پیوستگی مهم‌تر از کمال‌گرایی است.", color = Lime, fontSize = 11.sp)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("کل عادت‌ها", habits.size.toString(), Modifier.weight(1f))
            Metric("انجام‌شده", done.size.toString(), Modifier.weight(1f))
            Metric("باقی‌مانده", (habits.size - done.size).coerceAtLeast(0).toString(), Modifier.weight(1f))
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
        Icon(Icons.Default.CheckCircle, null, tint = Lime, modifier = Modifier.size(34.dp))
        Text("هنوز عادتی ثبت نشده", color = White, fontWeight = FontWeight.Bold)
        Text("با دکمه + اولین عادتت را بساز.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun HabitCard(h: HabitEntity, completed: Boolean, toggle: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (completed) Lime else Color.Transparent).clickable(onClick = toggle), contentAlignment = Alignment.Center) {
            if (completed) Icon(Icons.Default.Check, null, tint = Ink) else Canvas(Modifier.fillMaxSize()) { drawCircle(Muted, style = Stroke(1.5.dp.toPx())) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = toggle)) {
            Text(h.title, color = if (completed) Muted else White, fontWeight = FontWeight.SemiBold, textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None)
            Text(listOf(h.category, h.note).filter { it.isNotBlank() }.joinToString(" • "), color = Muted, fontSize = 11.sp, maxLines = 2)
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
        Text("\${done.size} از \${habits.size} عادت انجام شده", color = Lime, fontWeight = FontWeight.Bold)
        if (habits.isEmpty()) EmptyState() else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(habits, key = { it.id }) { h ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (h.id in done) Icons.Default.CheckCircle else Icons.Default.Circle, null, tint = if (h.id in done) Lime else Muted)
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
    val completed = days.sumOf { byDate[it.toString()]?.size ?: 0 }
    val possible = habits.sumOf { habit -> days.count { day -> !day.isBefore(Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()) } }
    val rate = if (possible == 0) 0 else (completed.toFloat() / possible * 100).toInt().coerceAtMost(100)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(18.dp)) {
            Text("عملکرد هفت روز اخیر", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("$rate٪", color = Lime, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("نسبت ثبت‌های موفق به تعداد عادت‌ها در این هفته", color = Muted, fontSize = 11.sp)
        } }
        item { Text("روند روزانه", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        item { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            days.forEach { day ->
                val count = byDate[day.toString()]?.size ?: 0
                val ratio = if (habits.isEmpty()) 0f else (count.toFloat() / habits.size).coerceIn(0f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.width(24.dp).height(100.dp), contentAlignment = Alignment.BottomCenter) { Box(Modifier.fillMaxWidth().fillMaxHeight(ratio).clip(RoundedCornerShape(8.dp)).background(Lime)) }
                    Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("fa")), color = Muted, fontSize = 10.sp)
                }
            }
        } }
        item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
            Text("جمع‌بندی", color = White, fontWeight = FontWeight.Bold)
            Text("تعداد عادت‌ها: \${habits.size}", color = Muted, fontSize = 13.sp)
            Text("ثبت‌های موفق در این هفته: $completed", color = Muted, fontSize = 13.sp)
            if (habits.isEmpty()) Text("برای شروع آمار، یک عادت اضافه کن.", color = Lime, fontSize = 12.sp)
        } }
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
        confirmButton = { TextButton(onClick = { if (title.isBlank()) error = true else save(title, note, category) }) { Text("ذخیره", color = Lime) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("انصراف", color = Muted) } })
}
