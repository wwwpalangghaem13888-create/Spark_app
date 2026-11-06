package com.spark.habittracker
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
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

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as SparkApplication).container }
    private val db by lazy { container.database }
    private val sparkViewModel by lazy {
        ViewModelProvider(this, SparkViewModel.Factory(container))[SparkViewModel::class.java]
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        setContent {
            val settings by container.habitRepository.observeSettings().collectAsState(initial = null)
            SparkTheme(settings ?: UserSettingsEntity()) { SparkApp(container, sparkViewModel) }
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
        "light" -> Color(0xFFF3F4F8)
        else -> Color(0xFF090716)
    }
    val surface = when (settings.theme) {
        "minimal" -> Color(0xFF1B2731)
        "dark" -> Color(0xFF20232A)
        "light" -> Color(0xFFFFFFFF)
        else -> Color(0xFF151127)
    }
    val light = settings.theme == "light"
    val palette = SparkPalette(background, surface, surface.copy(alpha = 0.78f), accent, accent.copy(alpha = 0.75f), Color(0xFF168A95), if (light) Color(0xFF626575) else Color(0xFFA9A0C2), if (light) Color(0xFF171923) else Color(0xFFF8F5FF))
    CompositionLocalProvider(LocalSparkPalette provides palette, LocalLayoutDirection provides LayoutDirection.Rtl) {
        val colors = if (light) lightColorScheme(primary = accent, secondary = palette.secondary, background = background, surface = surface, onPrimary = Color.White, onBackground = palette.foreground, onSurface = palette.foreground)
            else darkColorScheme(primary = accent, secondary = palette.secondary, background = background, surface = surface, onPrimary = Color.Black, onBackground = palette.foreground, onSurface = palette.foreground)
        MaterialTheme(colorScheme = colors, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkApp(container: SparkAppContainer, viewModel: SparkViewModel) {
    val context = LocalContext.current
    val db = container.database
    val habitRepository = container.habitRepository
    val productivityRepository = container.productivityRepository
    var destination by remember { mutableStateOf(SparkDestination.Dashboard) }
    val tab = destination.tabIndex
    var date by remember { mutableStateOf(LocalDate.now()) }
    var currentDay by remember { mutableStateOf(LocalDate.now()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HabitEntity?>(null) }
    var taskEditorOpen by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    val taskState by viewModel.tasks.collectAsState()
    val tasks = (taskState as? SparkLoadState.Data)?.value.orEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.errors.collect { error -> snackbarHostState.showSnackbar(error.userMessage()) }
    }
    val archivedTasks by db.taskDao().observeArchivedTasks().collectAsState(initial = emptyList())
    val focusSessions by productivityRepository.observeFocusSessions().collectAsState(initial = emptyList())
    val taskHistory by productivityRepository.observeTaskHistory().collectAsState(initial = emptyList())
    val habits by habitRepository.observeHabits().collectAsState(initial = emptyList())
    val logs by remember(date) { habitRepository.observeLogs(date) }.collectAsState(initial = emptyList())
    val todayLogs by remember(currentDay) { habitRepository.observeLogs(currentDay) }.collectAsState(initial = emptyList())
    val allLogs by habitRepository.observeAllLogs().collectAsState(initial = emptyList())
    val savedSettings by habitRepository.observeSettings().collectAsState(initial = null)
    val settings = savedSettings ?: UserSettingsEntity()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            val zone = ZoneId.systemDefault()
            val nextMidnight = currentDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            delay((nextMidnight - System.currentTimeMillis()).coerceAtLeast(1_000L))
            currentDay = LocalDate.now(zone)
        }
    }
    LaunchedEffect(currentDay, tab) {
        if (tab == 0) date = currentDay
    }
    LaunchedEffect(savedSettings) { if (savedSettings == null) habitRepository.saveSettings(UserSettingsEntity()) }
    val done = logs.filter { it.isCompleted }.map { it.habitId }.toSet()
    val streaksByHabit = remember(habits, allLogs, currentDay) {
        habits.associate { habit ->
            val completedDates = allLogs.asSequence()
                .filter { it.habitId == habit.id && it.isCompleted }
                .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
                .toSet()
            habit.id to calculateHabitStreak(habit, completedDates, currentDay)
        }
    }
    val scheduledHabits = habits.filter { it.isScheduledOn(date) }
    val todayHabits = habits.filter { it.isScheduledOn(currentDay) }
    val completedCount = done.intersect(scheduledHabits.map { it.id }.toSet()).size
    val progress = calculateCompletionRate(completedCount, scheduledHabits.size)
    Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.spark_logo), contentDescription = "لوگوی Spark", modifier = Modifier.size(42.dp))
            Column {
                Text("SPARK", color = VioletLight, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(listOf("عادت‌های روزانه", "مدیریت وظایف", "مرور روزها", "گزارش عملکرد", "سطح و دستاوردها", "اهداف", "تمرکز", "پشتیبان‌گیری")[tab], color = Muted, fontSize = 12.sp)
            }
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)) },
        bottomBar = { NavigationBar(containerColor = Panel) {
            listOf("خانه" to Icons.Default.Home, "وظایف" to Icons.Default.Checklist, "تقویم" to Icons.Default.CalendarMonth, "آمار" to Icons.Default.Insights, "پروفایل" to Icons.Default.EmojiEvents, "اهداف" to Icons.Default.Flag, "تمرکز" to Icons.Default.Timer, "پشتیبان" to Icons.Default.Folder).forEachIndexed { i, item ->
                NavigationBarItem(selected = tab == i, onClick = { destination = SparkDestination.fromTab(i); if (i == 0) date = LocalDate.now() }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, selectedTextColor = Violet, indicatorColor = PanelAlt, unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        } },
        floatingActionButton = { if (tab == 0 || tab == 1 || tab == 2) FloatingActionButton(onClick = {
            when (tab) {
                1 -> { editingTask = null; taskEditorOpen = true }
                2 -> { editingTask = TaskEntity(title = "", dueDate = date.toString()); taskEditorOpen = true }
                else -> { editing = null; editorOpen = true }
            }
        }, containerColor = Violet, contentColor = Ink) { Icon(Icons.Default.Add, if (tab == 0) "افزودن عادت" else "افزودن وظیفه") } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> Dashboard(scheduledHabits, done, progress, streaksByHabit, settings.reducedMotion, tasks, taskHistory, focusSessions,
                    onToggle = { h, wasDone -> scope.launch { habitRepository.record(HabitLogEntity(habitId = h.id, date = date.toString(), isCompleted = !wasDone)) } },
                    onEdit = { editing = it; editorOpen = true },
                    onDelete = { h -> scope.launch {
                        habitRepository.delete(h.id)
                        db.productivityDao().deleteRemindersForOwner("HABIT", h.id)
                    } })
                1 -> TaskListScreen(
                    tasks = tasks,
                    onToggle = { task, completed -> scope.launch {
                        recordTaskCompletionAndSyncReminder(context, container.taskRepository, productivityRepository, task.id, completed)
                    } },
                    onEdit = { editingTask = it; taskEditorOpen = true },
                    onArchive = { task -> scope.launch {
                        if (viewModel.archive(task.id)) {
                            db.productivityDao().disableRemindersForOwner("TASK", task.id)
                            SparkWidgetProvider.refreshAll(context)
                        }
                    } },
                    onDelete = { task -> scope.launch {
                        if (viewModel.deletePermanently(task.id)) {
                            db.productivityDao().deleteRemindersForOwner("TASK", task.id)
                            SparkWidgetProvider.refreshAll(context)
                        }
                    } },
                    onDuplicate = { task -> scope.launch { if (viewModel.duplicate(task)) SparkWidgetProvider.refreshAll(context) } },
                    onRestore = { task -> scope.launch {
                        if (viewModel.restore(task.id)) {
                        taskReminderTriggerAt(task, System.currentTimeMillis())?.let { triggerAt ->
                            db.productivityDao().deleteRemindersForOwner("TASK", task.id)
                            val reminder = ReminderEntity(ownerType = "TASK", ownerId = task.id, triggerAt = triggerAt)
                            val reminderId = db.productivityDao().insertReminder(reminder)
                            ReminderScheduler.schedule(context, reminder.copy(id = reminderId))
                        }
                        SparkWidgetProvider.refreshAll(context)
                        }
                    } },
                    archivedTasks = archivedTasks,
                    productivityDao = db.productivityDao()
                )
                2 -> SparkCalendarScreen(
                    selectedDate = date,
                    onDateChange = { date = it },
                    habits = habits,
                    tasks = tasks,
                    logs = allLogs,
                    onToggleHabit = { habit, completed ->
                        scope.launch {
                            habitRepository.record(HabitLogEntity(habitId = habit.id, date = date.toString(), isCompleted = completed))
                        }
                    },
                    onToggleTask = { task, completed ->
                        scope.launch {
                            recordTaskCompletionAndSyncReminder(context, container.taskRepository, productivityRepository, task.id, completed)
                        }
                    }
                )
                3 -> Statistics(habits, allLogs, tasks + archivedTasks, taskHistory, focusSessions)
                4 -> ProgressHub(todayHabits, allLogs, todayLogs, tasks, taskHistory, settings) { updated -> scope.launch { habitRepository.saveSettings(updated) } }
                5 -> GoalsScreen(productivityRepository, tasks, habits)
                6 -> FocusScreen(productivityRepository)
                else -> BackupScreen(db)
            }
        }
    }
    if (editorOpen) HabitEditor(editing, { editorOpen = false }) { title, note, category, scheduledDays, reminderTime ->
        scope.launch {
            val habit = HabitEntity(
                id = editing?.id ?: 0, title = title.trim(), note = note.trim(),
                category = category.trim().ifBlank { "عمومی" },
                createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                scheduledDays = scheduledDays,
                reminderTime = reminderTime,
                isEnabled = editing?.isEnabled ?: true
            )
            val habitId = habitRepository.save(habit)
            db.productivityDao().deleteRemindersForOwner("HABIT", habitId)
            nextHabitReminder(habit, System.currentTimeMillis())?.let { triggerAt ->
                val reminder = ReminderEntity(ownerType = "HABIT", ownerId = habitId, triggerAt = triggerAt)
                val reminderId = db.productivityDao().insertReminder(reminder)
                ReminderScheduler.schedule(context, reminder.copy(id = reminderId))
            }
        }
        editorOpen = false
    }
    if (taskEditorOpen) TaskEditorDialog(
        initial = editingTask,
        onDismiss = { taskEditorOpen = false; editingTask = null },
        onSave = { task ->
            scope.launch {
                val taskId = viewModel.saveTask(task)
                if (taskId != null) {
                    db.productivityDao().deleteRemindersForOwner("TASK", taskId)
                    taskReminderTriggerAt(task, System.currentTimeMillis())?.let { triggerAt ->
                        val reminder = ReminderEntity(ownerType = "TASK", ownerId = taskId, triggerAt = triggerAt)
                        val reminderId = db.productivityDao().insertReminder(reminder)
                        ReminderScheduler.schedule(context, reminder.copy(id = reminderId))
                    }
                    SparkWidgetProvider.refreshAll(context)
                    taskEditorOpen = false
                    editingTask = null
                }
            }
        }
    )
}


private suspend fun recordTaskCompletionAndSyncReminder(
    context: android.content.Context,
    taskRepository: TaskRepository,
    productivityRepository: ProductivityRepository,
    taskId: Long,
    completed: Boolean
) {
    val now = System.currentTimeMillis()
    val changed = productivityRepository.recordTaskCompletion(taskId, completed, now, LocalDate.now())
    if (changed && !completed) {
        // Reopening a task recreates its one-shot reminder only if its time is still in the future.
        productivityRepository.deleteRemindersForOwner("TASK", taskId)
        taskRepository.findById(taskId)?.let { taskReminderTriggerAt(it, now) }?.let { triggerAt ->
            val reminder = ReminderEntity(ownerType = "TASK", ownerId = taskId, triggerAt = triggerAt)
            val reminderId = productivityRepository.insertReminder(reminder)
            ReminderScheduler.schedule(context, reminder.copy(id = reminderId))
        }
    }
    SparkWidgetProvider.refreshAll(context)
}

@Composable
private fun Dashboard(habits: List<HabitEntity>, done: Set<Long>, progress: Float, streaks: Map<Long, Int>, reducedMotion: Boolean, tasks: List<TaskEntity>, taskHistory: List<TaskCompletionEntity>, focusSessions: List<FocusSessionEntity>, onToggle: (HabitEntity, Boolean) -> Unit, onEdit: (HabitEntity) -> Unit, onDelete: (HabitEntity) -> Unit) {
    val today = LocalDate.now()
    val tasksDueToday = tasks.filter { it.dueDate == today.toString() }
    val completedTasksToday = countUniqueTaskRewards(taskHistory, today) -
        countUniqueTaskRewards(taskHistory, today.minusDays(1))
    val focusMinutesToday = focusSessions.filter { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() == today }
        .sumOf { it.actualDurationSeconds.coerceAtLeast(0) } / 60
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
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("وظایف امروز", tasksDueToday.size.toString(), Modifier.weight(1f))
                Metric("تکمیل امروز", completedTasksToday.toString(), Modifier.weight(1f))
                Metric("تمرکز", focusMinutesToday.toString() + " دقیقه", Modifier.weight(1f))
            }
        }
        item { Text("وظایف نزدیک", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        if (tasksDueToday.isEmpty()) item { Text("برای امروز وظیفهٔ زمان‌بندی‌شده‌ای وجود ندارد.", color = Muted, fontSize = 12.sp) }
        else items(tasksDueToday.take(3), key = { "task-" + it.id }) { task ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (task.completed) Icons.Default.CheckCircle else Icons.Default.Circle, null, tint = if (task.completed) Cyan else Muted)
                Text(task.title, color = White, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(if (task.completed) "انجام شد" else taskPriorityLabel(task.priority), color = Muted, fontSize = 10.sp)
            }
        }
        item { Text("فهرست امروز", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        if (habits.isEmpty()) item { EmptyState() } else items(habits, key = { it.id }) { h -> HabitCard(h, h.id in done, streaks[h.id] ?: 0, { onToggle(h, h.id in done) }, { onEdit(h) }, { onDelete(h) }) }
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
private fun HabitCard(h: HabitEntity, completed: Boolean, streak: Int, toggle: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
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
            if (streak > 0) Text("🔥 $streak روز زنجیره", color = Cyan, fontSize = 10.sp)
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
private fun Statistics(
    habits: List<HabitEntity>,
    logs: List<HabitLogEntity>,
    tasks: List<TaskEntity>,
    taskHistory: List<TaskCompletionEntity>,
    focusSessions: List<FocusSessionEntity>
) {
    val today = LocalDate.now()
    var periodDays by remember { mutableIntStateOf(7) }
    val report = remember(habits, logs, tasks, taskHistory, focusSessions, periodDays, today) {
        buildAnalyticsReport(habits, logs, tasks, taskHistory, focusSessions, today.minusDays(periodDays.toLong() - 1), today)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("مرکز تحلیل Spark", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("گزارش‌ها از سوابق ذخیره‌شده ساخته می‌شوند.", color = Muted, fontSize = 11.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(7 to "۷ روز", 30 to "۳۰ روز", 90 to "۹۰ روز", 365 to "یک سال").forEach { (days, label) ->
                    FilterChip(selected = periodDays == days, onClick = { periodDays = days }, label = { Text(label) })
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(18.dp)) {
                Text("پایبندی به عادت‌ها", color = White, fontWeight = FontWeight.Bold)
                Text(if (report.scheduledHabitOpportunities == 0) "دادهٔ کافی وجود ندارد" else (report.habitCompletionRate * 100).roundToInt().toString() + "٪",
                    color = Violet, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text(report.completedHabits.toString() + " تکمیل از " + report.scheduledHabitOpportunities + " فرصت برنامه‌ریزی‌شده",
                    color = Muted, fontSize = 11.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("تکمیل وظایف", report.completedTasks.toString(), Modifier.weight(1f))
                MetricCard("زمان تمرکز", (report.focusSeconds / 60).toString() + " دقیقه", Modifier.weight(1f))
                MetricCard("وظایف باز", tasks.count { !it.completed && !it.archived }.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text("روند روزانه", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                report.days.takeLast(14).forEach { day ->
                    val total = day.completedHabits + day.completedTasks
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day.date.dayOfMonth.toString() + "/" + day.date.monthValue, color = Muted, modifier = Modifier.width(42.dp), fontSize = 10.sp)
                        Box(Modifier.weight(1f).height(9.dp).clip(CircleShape).background(PanelAlt)) {
                            val ratio = (total / 10f).coerceIn(0f, 1f)
                            Box(Modifier.fillMaxWidth(ratio).fillMaxSize().clip(CircleShape).background(Violet))
                        }
                        Text(total.toString(), color = White, modifier = Modifier.width(24.dp), fontSize = 10.sp)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Text("جمع‌بندی دوره", color = White, fontWeight = FontWeight.Bold)
                Text("روز پربازده: " + (report.bestDay?.toString() ?: "دادهٔ کافی نیست"), color = Muted, fontSize = 12.sp)
                val bestCategory = report.categoryTaskCounts.maxByOrNull { it.value }
                Text("دستهٔ پرتکرار: " + (bestCategory?.key ?: "دادهٔ کافی نیست"), color = Muted, fontSize = 12.sp)
                val periodSessions = focusSessions.count { session ->
                    session.completed && session.endedAt != null &&
                        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                            .let { it >= report.startDate && it <= report.endDate }
                }
                Text("جلسات تمرکز در این دوره: " + periodSessions, color = Muted, fontSize = 12.sp)
                Text("وظایف فعال: " + tasks.count { !it.completed && !it.archived }, color = Muted, fontSize = 12.sp)
                Text("وظایف تکمیل‌شده در بازه: " + report.completedTasks, color = Muted, fontSize = 12.sp)
                if (report.days.all { it.completedHabits + it.completedTasks == 0 && it.focusSeconds == 0L }) {
                    Text("در این بازه هنوز فعالیتی ثبت نشده است؛ نبود داده با عملکرد صفر یکسان نیست.", color = Violet, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp)) {
        Text(title, color = Muted, fontSize = 11.sp)
        Text(value, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun ProgressHub(habits: List<HabitEntity>, allLogs: List<HabitLogEntity>, todayLogs: List<HabitLogEntity>, tasks: List<TaskEntity>, taskHistory: List<TaskCompletionEntity>, settings: UserSettingsEntity, onSettingsChange: (UserSettingsEntity) -> Unit) {
    val today = LocalDate.now()
    val completedLogs = completedHabitLogsThroughDate(allLogs, today)
    val taskRewards = countUniqueTaskRewards(taskHistory, today)
    val totalActivities = completedLogs.size + taskRewards
    val xp = calculateExperience(totalActivities)
    val level = calculateLevel(xp)
    val xpInLevel = xp % 100
    val activeCount = habits.count { it.isEnabled }
    val todayDone = todayLogs.count { it.isCompleted && habits.any { h -> h.id == it.habitId && h.isEnabled } }
    val todayTaskIds = tasks.asSequence()
        .filter { it.dueDate == today.toString() }
        .map { it.id }
        .toSet()
    val todayTaskDone = taskHistory.asSequence()
        .filter { it.completed && it.activityDate == today.toString() && it.taskId in todayTaskIds }
        .map { it.taskId }
        .distinct()
        .count()
    val todayTaskTarget = todayTaskIds.size
    val challengeTarget = minOf(3, activeCount + todayTaskTarget)
    val challengeDone = todayDone + todayTaskDone
    val challengeProgress = if (challengeTarget == 0) 0f else (challengeDone.toFloat() / challengeTarget).coerceIn(0f, 1f)
    val weekStart = today.minusDays(6)
    val weeklyHabitDone = completedLogs.count { runCatching { val d = LocalDate.parse(it.date); d >= weekStart && d <= today }.getOrDefault(false) }
    val weeklyTaskDone = countUniqueTaskRewards(taskHistory, today) - countUniqueTaskRewards(taskHistory, weekStart.minusDays(1))
    val weeklyDone = weeklyHabitDone + weeklyTaskDone
    val weeklyTarget = 10
    val weeklyProgress = (weeklyDone.toFloat() / weeklyTarget).coerceIn(0f, 1f)
    val milestones = listOf(1 to "اولین جرقه", 10 to "ده قدم", 30 to "پیوستگی", 100 to "صد ثبت موفق")
    val recentStart = today.minusDays(6)
    val previousStart = today.minusDays(13)
    val weekDone = completedLogs.count { runCatching { val d = LocalDate.parse(it.date); d >= recentStart && d <= today }.getOrDefault(false) }
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
                Text("هر تکمیل یکتای عادت یا وظیفه در روز = ۱۰ XP. امتیازها از سوابق ذخیره‌شده محاسبه می‌شوند.", color = Muted, fontSize = 10.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Text("چالش امروز", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(if (challengeTarget == 0) "برای شروع، یک وظیفه یا عادت زمان‌بندی کن." else "تکمیل $challengeTarget فعالیت امروز", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = challengeProgress, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Cyan, trackColor = PanelAlt)
                Text(if (challengeTarget == 0) "بدون فعالیت برنامه‌ریزی‌شده" else "$challengeDone از $challengeTarget", color = Cyan, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Text("چالش هفتگی", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("هدف: ۱۰ تکمیل یکتای عادت یا وظیفه در هفت روز اخیر", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(9.dp))
                LinearProgressIndicator(progress = weeklyProgress, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Violet, trackColor = PanelAlt)
                Text("$weeklyDone از $weeklyTarget فعالیت", color = Cyan, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Text("دستاوردها", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                milestones.forEach { (required, name) ->
                    val unlocked = totalActivities >= required
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock, null, tint = if (unlocked) Cyan else Muted)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = if (unlocked) White else Muted, fontWeight = FontWeight.SemiBold)
                            Text("$required ثبت موفق موردنیاز", color = Muted, fontSize = 10.sp)
                        }
                        Text(if (unlocked) "باز شد" else "${totalActivities}/$required", color = if (unlocked) Cyan else Muted, fontSize = 11.sp)
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
                    listOf("neon" to "نئون", "dark" to "تیره", "minimal" to "مینیمال", "light" to "روشن").forEach { (key, label) ->
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
private fun HabitEditor(
    initial: HabitEntity?,
    dismiss: () -> Unit,
    save: (String, String, String, String, String) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "عمومی") }
    var reminderTime by remember(initial) { mutableStateOf(initial?.reminderTime ?: "08:00") }
    val days = listOf(
        "MONDAY" to "دوشنبه", "TUESDAY" to "سه‌شنبه", "WEDNESDAY" to "چهارشنبه",
        "THURSDAY" to "پنجشنبه", "FRIDAY" to "جمعه", "SATURDAY" to "شنبه", "SUNDAY" to "یکشنبه"
    )
    var selectedDays by remember(initial) {
        mutableStateOf(initial?.scheduledDays?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: days.map { it.first }.toSet())
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, containerColor = Panel,
        title = { Text(if (initial == null) "عادت جدید" else "ویرایش عادت", color = White) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(title, { title = it; error = null }, label = { Text("عنوان عادت") }, singleLine = true)
            OutlinedTextField(category, { category = it }, label = { Text("دسته‌بندی") }, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") }, minLines = 2)
            Text("روزهای برنامه")
            days.chunked(4).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.forEach { (key, label) ->
                        FilterChip(selected = key in selectedDays,
                            onClick = { selectedDays = if (key in selectedDays) selectedDays - key else selectedDays + key },
                            label = { Text(label) })
                    }
                }
            }
            OutlinedTextField(reminderTime, { reminderTime = it; error = null }, label = { Text("ساعت یادآوری (HH:mm)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        } },
        confirmButton = { TextButton(onClick = {
            val validTime = runCatching { java.time.LocalTime.parse(reminderTime) }.isSuccess
            if (title.isBlank()) error = "عنوان نمی‌تواند خالی باشد."
            else if (selectedDays.isEmpty()) error = "حداقل یک روز انتخاب کن."
            else if (!validTime) error = "ساعت باید با قالب HH:mm وارد شود."
            else save(title, note, category, days.filter { it.first in selectedDays }.joinToString(",") { it.first }, reminderTime)
        }) { Text("ذخیره", color = Violet) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("انصراف", color = Muted) } })
}
