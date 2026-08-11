package com.spark.habittracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ActionParameters
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.time.DayOfWeek
import java.time.LocalDate

class RoomConverters {
    @TypeConverter
    fun fromDayOfWeekList(value: List<DayOfWeek>): String =
        value.joinToString(",") { it.name }

    @TypeConverter
    fun toDayOfWeekList(value: String): List<DayOfWeek> =
        if (value.isEmpty()) emptyList() else value.split(",").map { DayOfWeek.valueOf(it) }
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val scheduledDays: List<DayOfWeek>,
    val reminderTime: String,
    val category: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "task_logs", indices = [Index(value = ["taskId", "date"], unique = true)])
data class TaskLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val date: String,
    val isCompleted: Boolean,
    val completionTimestamp: Long = System.currentTimeMillis()
)

data class TaskWithStatus(
    val task: TaskEntity,
    val isCompletedToday: Boolean
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isEnabled = 1")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Query("SELECT * FROM task_logs WHERE date = :date")
    fun getLogsForDate(date: String): Flow<List<TaskLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLog(log: TaskLogEntity)

    @Query("SELECT COUNT(*) FROM task_logs WHERE date = :date AND isCompleted = 1")
    fun getCompletedCountForDate(date: String): Flow<Int>
}

@Database(entities = [TaskEntity::class, TaskLogEntity::class], version = 1, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class SparkDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}

class TaskRepository(private val taskDao: TaskDao) {
    val todayDateStr: String get() = LocalDate.now().toString()

    fun getTodayTasksWithStatus(): Flow<List<TaskWithStatus>> {
        val todayOfWeek = LocalDate.now().dayOfWeek
        return combine(taskDao.getAllTasks(), taskDao.getLogsForDate(todayDateStr)) { tasks, logs ->
            tasks.filter { it.scheduledDays.contains(todayOfWeek) }
                .map { task ->
                    val isDone = logs.find { it.taskId == task.id }?.isCompleted ?: false
                    TaskWithStatus(task, isDone)
                }
        }
    }

    suspend fun toggleTaskCompletion(taskId: Long, currentStatus: Boolean) {
        val newLog = TaskLogEntity(
            taskId = taskId,
            date = todayDateStr,
            isCompleted = !currentStatus
        )
        taskDao.insertOrUpdateLog(newLog)
    }

    suspend fun addTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }
}

class DashboardViewModel(private val repository: TaskRepository) : ViewModel() {
    val todayTasks: StateFlow<List<TaskWithStatus>> =
        repository.getTodayTasksWithStatus()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completionPercentage: StateFlow<Float> = todayTasks.map { list ->
        if (list.isEmpty()) 0f else (list.count { it.isCompletedToday }.toFloat() / list.size.toFloat())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.75f)

    fun onTaskCheckedChange(taskWithStatus: TaskWithStatus) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(
                taskWithStatus.task.id,
                taskWithStatus.isCompletedToday
            )
        }
    }

    fun addNewTask(title: String, desc: String, category: String, time: String) {
        viewModelScope.launch {
            repository.addTask(
                TaskEntity(
                    title = title,
                    description = desc,
                    scheduledDays = DayOfWeek.values().toList(),
                    reminderTime = time,
                    category = category
                )
            )
        }
    }
}

val appModule = module {
    single {
        Room.databaseBuilder(get(), SparkDatabase::class.java, "spark_tracker.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<SparkDatabase>().taskDao() }
    single { TaskRepository(get()) }
    viewModel { DashboardViewModel(get()) }
}

val DarkBackground = Color(0xFF121318)
val DarkSurface = Color(0xFF1C1E24)
val DarkSurfaceBorder = Color(0xFF252525)
val NeonLime = Color(0xFFC3F400)
val NeonCyan = Color(0xFF00FBFB)
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF9E9E9E)

@Composable
fun SparkTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        background = DarkBackground,
        surface = DarkSurface,
        primary = NeonLime,
        secondary = NeonCyan,
        onBackground = TextWhite,
        onSurface = TextWhite
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startKoin {
            androidContext(this@MainActivity)
            modules(appModule)
        }
        createNotificationChannel(this)
        setContent {
            SparkTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                title = {
                    Text(
                        text = "داشبورد SPARK",
                        color = NeonLime,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = TextWhite)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = TextWhite)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 8.dp
            ) {
                val items = listOf("داشبورد" to Icons.Default.Home, "تقویم" to Icons.Default.DateRange, "آمار" to Icons.Default.BarChart)
                items.forEachIndexed { index, pair ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                pair.second,
                                contentDescription = pair.first,
                                tint = if (isSelected) NeonCyan else TextMuted
                            )
                        },
                        label = {
                            Text(
                                pair.first,
                                color = if (isSelected) NeonCyan else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = NeonLime,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(28.dp))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> DashboardContent()
                1 -> CalendarScreen()
                2 -> StatisticsScreen()
            }
            if (showAddTaskDialog) {
                AddTaskDialog(onDismiss = { showAddTaskDialog = false })
            }
        }
    }
}

@Composable
fun DashboardContent(viewModel: DashboardViewModel = org.koin.androidx.compose.koinViewModel()) {
    val tasks by viewModel.todayTasks.collectAsState()
    val progressPercent by viewModel.completionPercentage.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurface)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp)
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progressPercent,
                        animationSpec = tween(1000)
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = DarkSurfaceBorder,
                            style = Stroke(width = 10.dp.toPx())
                        )
                        drawArc(
                            color = NeonLime,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "${(progressPercent * 100).toInt()}%",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = "پیشرفت امروز شما",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "عملکرد بسیار عالی تا این لحظه!",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonLime.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+۱۲٪ نسبت به دیروز",
                            color = NeonLime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyan, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricColumn("زمان فعال", "۲.۵ ساعت")
                    MetricColumn("وظایف انجام شده", "${tasks.count { it.isCompletedToday }}/${tasks.size}")
                    MetricColumn("بازده", "۸۵٪")
                }
            }
        }
        item {
            Text(
                text = "وظایف امروز",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (tasks.isEmpty()) {
            item {
                Text(
                    text = "هیچ وظیفه‌ای برای امروز برنامه‌ریزی نشده است.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            items(tasks, key = { it.task.id }) { item ->
                TaskItemRow(
                    taskWithStatus = item,
                    onCheckedChange = { viewModel.onTaskCheckedChange(item) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = NeonCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TaskItemRow(
    taskWithStatus: TaskWithStatus,
    onCheckedChange: () -> Unit
) {
    val isCompleted = taskWithStatus.isCompletedToday
    val borderColor by animateColorAsState(if (isCompleted) NeonLime else DarkSurfaceBorder)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) NeonLime else Color.Transparent)
                    .border(2.dp, if (isCompleted) NeonLime else TextMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = taskWithStatus.task.title,
                    color = if (isCompleted) TextMuted else TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    text = "${taskWithStatus.task.category} • ${taskWithStatus.task.reminderTime}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun CalendarScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("نمای تقویم ماهانه و سالانه", color = TextWhite)
    }
}

@Composable
fun StatisticsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("نمودارهای پیشرفته و آمار عملکرد", color = TextWhite)
    }
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    viewModel: DashboardViewModel = org.koin.androidx.compose.koinViewModel()
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("عمومی") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("افزودن عادت جدید", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان وظیفه") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonLime)
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("دسته بندی") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonLime)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotEmpty()) {
                    viewModel.addNewTask(title, "", category, "08:00")
                    onDismiss()
                }
            }) {
                Text("ذخیره", color = NeonLime)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = TextMuted)
            }
        }
    )
}

class SparkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceContent()
        }
    }

    @Composable
    private fun GlanceContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(DarkSurface)
                .padding(12.dp)
        ) {
            Text(
                "SPARK وظایف امروز",
                style = TextStyle(color = androidx.glance.unit.ColorProvider(NeonLime))
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(actionRunCallback<ToggleWidgetTaskCallback>())
            ) {
                Text(
                    "تمرین ورزش صبحگاهی ✓",
                    style = TextStyle(color = androidx.glance.unit.ColorProvider(TextWhite))
                )
            }
        }
    }
}

class ToggleWidgetTaskCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
    }
}

class SparkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SparkWidget()
}

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "SPARK_NOTIFS",
            "یادآوری عادت‌ها",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
        }
    }
}
