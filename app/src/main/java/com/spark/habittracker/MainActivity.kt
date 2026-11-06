package com.spark.habittracker
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.os.Build
import android.content.pm.PackageManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
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

private fun sparkTabTitles(language: String) = listOf(
    localizedText(language, "عادت‌های روزانه", "Habits", "العادات"),
    localizedText(language, "کارها", "Tasks", "المهام"),
    localizedText(language, "مرور روزها", "Calendar", "التقويم"),
    localizedText(language, "گزارش عملکرد", "Statistics", "الإحصاءات"),
    localizedText(language, "سطح و دستاوردها", "Progress", "التقدم"),
    localizedText(language, "اهداف", "Goals", "الأهداف"),
    localizedText(language, "تمرکز", "Focus", "التركيز"),
    localizedText(language, "پشتیبان‌گیری", "Backup", "النسخ الاحتياطي")
)

private fun sparkNavigationLabels(language: String) = listOf(
    localizedText(language, "خانه", "Home", "الرئيسية"),
    localizedText(language, "کارها", "Tasks", "المهام"),
    localizedText(language, "تقویم", "Calendar", "التقويم"),
    localizedText(language, "آمار", "Stats", "الإحصاءات"),
    localizedText(language, "پروفایل", "Profile", "الملف الشخصي"),
    localizedText(language, "اهداف", "Goals", "الأهداف"),
    localizedText(language, "تمرکز", "Focus", "التركيز"),
    localizedText(language, "پشتیبان", "Backup", "النسخ الاحتياطي")
)

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val selectedLanguage = newBase.getSharedPreferences("spark_locale", Context.MODE_PRIVATE)
            .getString("language", "fa") ?: "fa"
        val locale = Locale.forLanguageTag(selectedLanguage)
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

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
            SparkTheme(settings ?: UserSettingsEntity()) {
                var splashVisible by remember { mutableStateOf(true) }
                val splashProgress by animateFloatAsState(
                    targetValue = if (splashVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                    label = "splashFade"
                )
                val logoScale by animateFloatAsState(
                    targetValue = if (splashVisible) 1f else 0.88f,
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                    label = "logoScale"
                )
                LaunchedEffect(Unit) {
                    delay(1900)
                    splashVisible = false
                }
                Box(Modifier.fillMaxSize()) {
                    SparkApp(container, sparkViewModel)
                    if (splashVisible || splashProgress > 0.01f) {
                        Box(
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                alpha = splashProgress
                            }.background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF21113D), Color(0xFF090716), Color.Black),
                                    radius = 1050f
                                )
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(R.drawable.spark_logo),
                                    contentDescription = "Spark logo",
                                    modifier = Modifier.size(128.dp * logoScale)
                                )
                                Spacer(Modifier.height(18.dp))
                                Text(
                                    "SPARK",
                                    color = Color(0xFFF1E7FF),
                                    fontSize = 27.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 6.sp
                                )
                                Spacer(Modifier.height(9.dp))
                                Text(
                                    "BIGGER DREAMS  ✦  BRIGHTER YOU",
                                    color = Color(0xFFC49A5A).copy(alpha = splashProgress),
                                    fontSize = 9.sp,
                                    letterSpacing = 1.6.sp
                                )
                                Spacer(Modifier.height(34.dp))
                                Box(Modifier.width(42.dp).height(2.dp).clip(CircleShape).background(
                                    Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFD6B8FF), Color.Transparent))
                                ))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SparkTheme(settings: UserSettingsEntity, content: @Composable () -> Unit) {
    val accent = when (settings.accent) {
        "cyan" -> Color(0xFF42DCE8)
        "lime" -> Color(0xFFC5F36A)
        "pink" -> Color(0xFFFF75C8)
        else -> runCatching {
            if (settings.accent.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                Color(AndroidColor.parseColor(settings.accent))
            } else Color(0xFF9A63FF)
        }.getOrDefault(Color(0xFF9A63FF))
    }
    val background = when (settings.theme) {
        "dark" -> Color(0xFF020304)
        "midnight" -> Color(0xFF050A16)
        "neon-blue" -> Color(0xFF030B12)
        "neon-green" -> Color(0xFF050D07)
        "neon-pink" -> Color(0xFF10040C)
        "neon-red" -> Color(0xFF100305)
        "minimal" -> Color(0xFF101820)
        "light" -> Color(0xFFF3F4F8)
        else -> Color(0xFF090716)
    }
    val surface = when (settings.theme) {
        "dark" -> Color(0xFF0A0C0F)
        "midnight" -> Color(0xFF0D1425)
        "neon-blue" -> Color(0xFF071723)
        "neon-green" -> Color(0xFF0B170E)
        "neon-pink" -> Color(0xFF1C0A15)
        "neon-red" -> Color(0xFF1C080B)
        "minimal" -> Color(0xFF1B2731)
        "light" -> Color(0xFFFFFFFF)
        else -> Color(0xFF151127)
    }
    val light = settings.theme == "light"
    val secondary = when (settings.theme) {
        "neon-blue" -> Color(0xFF36CFFF)
        "neon-green" -> Color(0xFF9BFF55)
        "neon-pink" -> Color(0xFFFF63C7)
        "neon-red" -> Color(0xFFFF5268)
        else -> Color(0xFF168A95)
    }
    val palette = SparkPalette(
        background, surface, surface.copy(alpha = 0.92f), accent,
        accent.copy(alpha = 0.75f), secondary,
        if (light) Color(0xFF626575) else Color(0xFFA9A0C2),
        if (light) Color(0xFF171923) else Color(0xFFF8F5FF)
    )
    CompositionLocalProvider(
        LocalSparkPalette provides palette,
        LocalSparkLanguage provides settings.language,
        LocalLayoutDirection provides if (settings.language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl
    ) {
        val colors = if (light) lightColorScheme(
            primary = accent, secondary = palette.secondary, background = background,
            surface = surface, onPrimary = Color.White, onBackground = palette.foreground, onSurface = palette.foreground
        ) else darkColorScheme(
            primary = accent, secondary = palette.secondary, background = background,
            surface = surface, onPrimary = Color.Black, onBackground = palette.foreground, onSurface = palette.foreground
        )
        MaterialTheme(colorScheme = colors, content = content)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkApp(container: SparkAppContainer, viewModel: SparkViewModel) {
    val context = LocalContext.current
    val taskGuidePrefs = remember(context) {
        context.getSharedPreferences("spark_task_guide", Context.MODE_PRIVATE)
    }
    val appGuidePrefs = remember(context) {
        context.getSharedPreferences("spark_app_guide", Context.MODE_PRIVATE)
    }
    var showAppGuide by remember { mutableStateOf(false) }
    var showTaskGuide by remember { mutableStateOf(false) }
    val db = container.database
    val habitRepository = container.habitRepository
    val productivityRepository = container.productivityRepository
    var destination by remember { mutableStateOf(SparkDestination.Dashboard) }
    var moreNavigationExpanded by remember { mutableStateOf(false) }
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
        if (!appGuidePrefs.getBoolean("seen_v1", false)) showAppGuide = true
    }
    LaunchedEffect(tab) {
        if (tab == 1 && !taskGuidePrefs.getBoolean("seen_v1", false)) {
            showTaskGuide = true
        }
    }
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
                Text(sparkTabTitles(settings.language)[tab], color = Muted, fontSize = 12.sp)
            }
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)) },
        bottomBar = {
            Box {
                NavigationBar(containerColor = Panel) {
                    val navigationIcons = listOf(Icons.Default.Home, Icons.Default.Checklist, Icons.Default.CalendarMonth, Icons.Default.Insights)
                    val labels = sparkNavigationLabels(settings.language)
                    (0..3).forEach { i ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { destination = SparkDestination.fromTab(i); if (i == 0) date = LocalDate.now() },
                            icon = { Icon(navigationIcons[i], labels[i]) },
                            label = { Text(labels[i], maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, selectedTextColor = Violet,
                                indicatorColor = PanelAlt, unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                    NavigationBarItem(
                        selected = tab >= 4,
                        onClick = { moreNavigationExpanded = true },
                        icon = { Icon(Icons.Default.MoreVert, localizedText(settings.language, "بیشتر", "More", "المزيد")) },
                        label = { Text(localizedText(settings.language, "بیشتر", "More", "المزيد")) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, selectedTextColor = Violet,
                            indicatorColor = PanelAlt, unselectedIconColor = Muted, unselectedTextColor = Muted)
                    )
                }
                DropdownMenu(expanded = moreNavigationExpanded, onDismissRequest = { moreNavigationExpanded = false },
                    modifier = Modifier.align(Alignment.TopEnd)) {
                    val labels = sparkNavigationLabels(settings.language)
                    (4..7).forEach { i ->
                        DropdownMenuItem(text = { Text(labels[i]) }, onClick = {
                            destination = SparkDestination.fromTab(i)
                            moreNavigationExpanded = false
                        }, leadingIcon = {
                            Icon(listOf(Icons.Default.EmojiEvents, Icons.Default.Flag, Icons.Default.Timer, Icons.Default.Folder)[i - 4], null)
                        })
                    }
                }
            }
        },
        floatingActionButton = { if (tab == 0 || tab == 1 || tab == 2) FloatingActionButton(onClick = {
            when (tab) {
                1 -> { editingTask = null; taskEditorOpen = true }
                2 -> { editingTask = TaskEntity(title = "", dueDate = date.toString()); taskEditorOpen = true }
                else -> { editing = null; editorOpen = true }
            }
        }, containerColor = Violet, contentColor = Ink) { Icon(Icons.Default.Add, if (tab == 0) "افزودن عادت" else "افزودن کار") } }
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
                4 -> ProgressHub(todayHabits, allLogs, todayLogs, tasks, taskHistory, settings) { updated ->
                    scope.launch {
                        habitRepository.saveSettings(updated)
                        if (updated.language != settings.language) {
                            context.getSharedPreferences("spark_locale", Context.MODE_PRIVATE)
                                .edit().putString("language", updated.language).apply()
                            (context as? Activity)?.recreate()
                        }
                    }
                }
                5 -> GoalsScreen(productivityRepository, tasks, habits)
                6 -> FocusScreen(productivityRepository)
                else -> BackupScreen(db)
            }
        }
    }
    if (showAppGuide) {
        AppGuideDialog(language = settings.language) {
            appGuidePrefs.edit().putBoolean("seen_v1", true).apply()
            showAppGuide = false
        }
    }
    if (showTaskGuide) {
        TaskGuideDialog(language = settings.language) {
            taskGuidePrefs.edit().putBoolean("seen_v1", true).apply()
            showTaskGuide = false
        }
    }
    if (editorOpen) HabitEditor(editing, { editorOpen = false }) { title, note, category, scheduledDays, reminderTime, reminderMode ->
        scope.launch {
            val habit = HabitEntity(
                id = editing?.id ?: 0, title = title.trim(), note = note.trim(),
                category = category.trim().ifBlank { "عمومی" },
                createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                scheduledDays = scheduledDays,
                reminderTime = reminderTime,
                isEnabled = editing?.isEnabled ?: true,
                reminderMode = reminderMode
            )
            val habitId = habitRepository.save(habit)
            db.productivityDao().deleteRemindersForOwner("HABIT", habitId)
            nextHabitReminder(habit, System.currentTimeMillis())?.let { triggerAt ->
                val reminder = ReminderEntity(ownerType = "HABIT", ownerId = habitId, triggerAt = triggerAt)
                val reminderId = db.productivityDao().insertReminder(reminder)
                ReminderScheduler.schedule(
                    context,
                    reminder.copy(id = reminderId),
                    alarmStyle = habit.reminderMode == "ALARM"
                )
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
    val language = LocalSparkLanguage.current
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
                        Text(localizedText(language, "امروز", "Today", "اليوم"), color = Muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(localizedText(language, "پیشرفت امروز", "Today's progress", "إنجاز اليوم"), color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (habits.isEmpty()) localizedText(language, "اولین عادتت را اضافه کن.", "Add your first habit.", "أضف عادتك الأولى.") else localizedText(language, "$completedCount از ${habits.size} عادت انجام شده", "$completedCount of ${habits.size} habits completed", "أُنجزت $completedCount من ${habits.size} عادات"), color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(9.dp))
                    Text(if (habits.isNotEmpty() && completedCount == habits.size) localizedText(language, "همه عادت‌های امروز انجام شد!", "All today's habits are done!", "اكتملت عادات اليوم!") else localizedText(language, "قدم‌های کوچک، تغییرهای بزرگ.", "Small steps make big changes.", "خطوات صغيرة تصنع فرقاً كبيراً."), color = VioletLight, fontSize = 11.sp)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric(localizedText(language, "کل عادت‌ها", "Total habits", "إجمالي العادات"), habits.size.toString(), Modifier.weight(1f))
            Metric(localizedText(language, "انجام‌شده", "Completed", "مكتملة"), completedCount.toString(), Modifier.weight(1f))
            Metric(localizedText(language, "باقی‌مانده", "Remaining", "متبقية"), (habits.size - completedCount).coerceAtLeast(0).toString(), Modifier.weight(1f))
        } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(localizedText(language, "کارهای امروز", "Today's tasks", "مهام اليوم"), tasksDueToday.size.toString(), Modifier.weight(1f))
                Metric(localizedText(language, "تکمیل امروز", "Done today", "أُنجزت اليوم"), completedTasksToday.toString(), Modifier.weight(1f))
                Metric(localizedText(language, "تمرکز", "Focus", "التركيز"), focusMinutesToday.toString() + localizedText(language, " دقیقه", " min", " د"), Modifier.weight(1f))
            }
        }
        item { Text(localizedText(language, "کارهای پیش رو", "Upcoming tasks", "المهام القادمة"), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        if (tasksDueToday.isEmpty()) item { Text(localizedText(language, "برای امروز کاری زمان‌بندی نشده است.", "Nothing is scheduled for today.", "لا توجد مهام مجدولة لليوم."), color = Muted, fontSize = 12.sp) }
        else items(tasksDueToday.take(3), key = { "task-" + it.id }) { task ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (task.completed) Icons.Default.CheckCircle else Icons.Default.Circle, null, tint = if (task.completed) Cyan else Muted)
                Text(task.title, color = White, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(if (task.completed) "انجام شد" else taskPriorityLabel(task.priority), color = Muted, fontSize = 10.sp)
            }
        }
        item { Text(localizedText(language, "فهرست امروز", "Today's habits", "عادات اليوم"), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
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
    val language = LocalSparkLanguage.current
    val today = LocalDate.now()
    var periodDays by remember { mutableIntStateOf(7) }
    val report = remember(habits, logs, tasks, taskHistory, focusSessions, periodDays, today) {
        buildAnalyticsReport(habits, logs, tasks, taskHistory, focusSessions, today.minusDays(periodDays.toLong() - 1), today)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(localizedText(language, "مرکز تحلیل Spark", "Spark analytics", "تحليلات Spark"), color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(localizedText(language, "گزارش‌ها از سوابق ذخیره‌شده ساخته می‌شوند.", "Reports are based on saved activity.", "تعتمد التقارير على النشاط المحفوظ."), color = Muted, fontSize = 11.sp)
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
                MetricCard("کارهای تکمیل‌شده", report.completedTasks.toString(), Modifier.weight(1f))
                MetricCard("زمان تمرکز", (report.focusSeconds / 60).toString() + " دقیقه", Modifier.weight(1f))
                MetricCard("کارهای باز", tasks.count { !it.completed && !it.archived }.toString(), Modifier.weight(1f))
            }
        }
        item {
            val dailyTaskRates = report.days.takeLast(14).map { day ->
                val due = tasks.count { it.dueDate == day.date.toString() }
                if (due == 0) 0f else (day.completedTasks.toFloat() / due).coerceIn(0f, 1f)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("درصد انجام کارها", color = White, fontWeight = FontWeight.Bold)
                Text("بر اساس کارهایی که تاریخ انجامشان در بازهٔ انتخابی است", color = Muted, fontSize = 10.sp)
                SparkLineChart(dailyTaskRates, Modifier.fillMaxWidth().height(150.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(report.startDate.toString(), color = Muted, fontSize = 9.sp)
                    Text(report.endDate.toString(), color = Muted, fontSize = 9.sp)
                }
            }
        }
        item {
            val completedNow = tasks.count { it.completed }
            val openNow = tasks.count { !it.completed && !it.archived }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("کارهای انجام‌شده و باقی‌مانده", color = White, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    SparkPieChart(completedNow, openNow, Modifier.size(116.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(Violet))
                            Spacer(Modifier.width(7.dp))
                            Text("انجام‌شده: $completedNow", color = White, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(Cyan))
                            Spacer(Modifier.width(7.dp))
                            Text("باقی‌مانده: $openNow", color = White, fontSize = 12.sp)
                        }
                    }
                }
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
                Text("کارهای فعال: " + tasks.count { !it.completed && !it.archived }, color = Muted, fontSize = 12.sp)
                Text("کارهای تکمیل‌شده در بازه: " + report.completedTasks, color = Muted, fontSize = 12.sp)
                if (report.days.all { it.completedHabits + it.completedTasks == 0 && it.focusSeconds == 0L }) {
                    Text("در این بازه هنوز فعالیتی ثبت نشده است؛ نبود داده با عملکرد صفر یکسان نیست.", color = Violet, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SparkLineChart(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val left = 8f
        val right = size.width - 8f
        val top = 12f
        val bottom = size.height - 14f
        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            drawLine(Color.White.copy(alpha = 0.10f), androidx.compose.ui.geometry.Offset(left, y),
                androidx.compose.ui.geometry.Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        if (values.size == 1) {
            drawCircle(Violet, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2, top + (bottom-top) * (1f-values[0])))
            return@Canvas
        }
        val points = values.mapIndexed { index, value ->
            androidx.compose.ui.geometry.Offset(
                left + (right-left) * index / (values.size-1).toFloat(),
                bottom - (bottom-top) * value.coerceIn(0f,1f)
            )
        }
        points.zipWithNext().forEach { (a,b) ->
            drawLine(Violet, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        points.forEach { drawCircle(VioletLight, radius = 3.dp.toPx(), center = it) }
    }
}

@Composable
private fun SparkPieChart(completed: Int, remaining: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val total = completed + remaining
        if (total <= 0) {
            drawArc(Color.White.copy(alpha = 0.16f), 0f, 360f, true)
            return@Canvas
        }
        val doneSweep = 360f * completed / total.toFloat()
        drawArc(Violet, -90f, doneSweep, true)
        if (remaining > 0) drawArc(Cyan, -90f + doneSweep, 360f - doneSweep, true)
        drawCircle(Panel, radius = size.minDimension * 0.27f)
    }
}

@Composable
private fun CustomAccentDialog(initialAccent: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val initialHsv = remember(initialAccent) {
        FloatArray(3).also { hsv ->
            val initialColor = runCatching { AndroidColor.parseColor(initialAccent) }
                .getOrDefault(AndroidColor.rgb(139, 69, 255))
            AndroidColor.colorToHSV(initialColor, hsv)
        }
    }
    var hue by remember(initialAccent) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialAccent) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(initialAccent) { mutableFloatStateOf(initialHsv[2]) }
    var hexText by remember(initialAccent) {
        mutableStateOf(
            initialAccent.takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }
                ?: String.format("#%06X", 0xFFFFFF and Color.hsv(initialHsv[0], initialHsv[1], initialHsv[2]).toArgb())
        )
    }
    val preview = Color.hsv(hue, saturation, brightness)
    val generatedHex = String.format("#%06X", 0xFFFFFF and preview.toArgb())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText(LocalSparkLanguage.current, "استودیو ساخت رنگ", "Color studio", "استوديو الألوان"), color = White) },
        containerColor = Panel,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(preview)
                )
                Text(localizedText(LocalSparkLanguage.current, "فام رنگ", "Hue", "درجة اللون"), color = White)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(localizedText(LocalSparkLanguage.current, "اشباع", "Saturation", "التشبع"), color = White)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                Text(localizedText(LocalSparkLanguage.current, "روشنایی", "Brightness", "السطوع"), color = White)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0f..1f)
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { value ->
                        hexText = value.uppercase(Locale.ROOT)
                        if (hexText.matches(Regex("^#[0-9A-F]{6}$"))) {
                            runCatching { AndroidColor.parseColor(hexText) }.getOrNull()?.let { parsed ->
                                val hsv = FloatArray(3)
                                AndroidColor.colorToHSV(parsed, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                brightness = hsv[2]
                            }
                        }
                    },
                    label = { Text(localizedText(LocalSparkLanguage.current, "کد رنگ HEX", "HEX color", "رمز اللون HEX")) },
                    supportingText = { Text("نمونه: #7C4DFF") },
                    singleLine = true
                )
                Text("پیش‌نمایش: $generatedHex", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(generatedHex) }) { Text(localizedText(LocalSparkLanguage.current, "ذخیره رنگ", "Save color", "حفظ اللون"), color = Violet) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = Muted) }
        }
    )
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
    val language = LocalSparkLanguage.current
    val context = LocalContext.current
    var showColorStudio by remember { mutableStateOf(false) }
    var selectedSoundMode by remember { mutableStateOf(ReminderScheduler.soundMode(context)) }
    var selectedSoundUri by remember { mutableStateOf(ReminderScheduler.customSoundUri(context)) }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ReminderScheduler.setSound(context, "CUSTOM", uri)
            selectedSoundMode = "CUSTOM"
            selectedSoundUri = uri
        }
    }
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
                Text("هر تکمیل یکتای عادت یا کار در روز = ۱۰ XP. امتیازها از سوابق ذخیره‌شده محاسبه می‌شوند.", color = Muted, fontSize = 10.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Text("چالش امروز", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(if (challengeTarget == 0) "برای شروع، یک عادت یا کار ثبت کن." else "تکمیل $challengeTarget فعالیت امروز", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = challengeProgress, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Cyan, trackColor = PanelAlt)
                Text(if (challengeTarget == 0) "بدون فعالیت برنامه‌ریزی‌شده" else "$challengeDone از $challengeTarget", color = Cyan, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Text("چالش هفتگی", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("هدف: ۱۰ تکمیل یکتای عادت یا کار در هفت روز اخیر", color = Muted, fontSize = 11.sp)
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
            Text("Powered by Ghaem", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(localizedText(language, "ظاهر برنامه", "Appearance", "المظهر"), color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(localizedText(language, "تم", "Theme", "السمة"), color = Muted, fontSize = 12.sp)
                val themeOptions = listOf(
                    "neon" to localizedText(language, "نئون بنفش", "Purple neon", "نيون بنفسجي"),
                    "dark" to localizedText(language, "مشکی عمیق", "Deep black", "أسود داكن"),
                    "midnight" to localizedText(language, "نیمه‌شب", "Midnight", "منتصف الليل"),
                    "neon-blue" to localizedText(language, "نئون آبی", "Blue neon", "نيون أزرق"),
                    "neon-green" to localizedText(language, "نئون سبز", "Green neon", "نيون أخضر"),
                    "neon-pink" to localizedText(language, "نئون صورتی", "Pink neon", "نيون وردي"),
                    "neon-red" to localizedText(language, "نئون قرمز", "Red neon", "نيون أحمر"),
                    "minimal" to localizedText(language, "مینیمال", "Minimal", "بسيط"),
                    "light" to localizedText(language, "روشن", "Light", "فاتح")
                )
                themeOptions.chunked(3).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        group.forEach { (key, label) ->
                            FilterChip(
                                selected = settings.theme == key,
                                onClick = {
                                    val themeAccent = when (key) {
                                        "neon-blue" -> "cyan"
                                        "neon-green" -> "lime"
                                        "neon-pink", "neon-red" -> "pink"
                                        else -> settings.accent
                                    }
                                    onSettingsChange(settings.copy(theme = key, accent = themeAccent))
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                Text(localizedText(language, "رنگ تأکیدی", "Accent color", "لون التمييز"), color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("violet" to localizedText(language, "بنفش", "Violet", "بنفسجي"), "cyan" to localizedText(language, "فیروزه‌ای", "Cyan", "سماوي"), "lime" to localizedText(language, "سبز", "Lime", "ليموني"), "pink" to localizedText(language, "صورتی", "Pink", "وردي")).forEach { (key, label) ->
                        FilterChip(selected = settings.accent == key, onClick = { onSettingsChange(settings.copy(accent = key)) }, label = { Text(label) })
                    }
                }
                OutlinedButton(onClick = { showColorStudio = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Palette, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(localizedText(language, "استودیو رنگ دلخواه", "Custom color studio", "استوديو الألوان"))
                }

                Text(localizedText(language, "زبان برنامه", "App language", "لغة التطبيق"), color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("fa" to "فارسی", "en" to "English", "ar" to "العربية").forEach { (code, label) ->
                        FilterChip(
                            selected = settings.language == code,
                            onClick = { onSettingsChange(settings.copy(language = code)) },
                            label = { Text(label) }
                        )
                    }
                }

                Text(localizedText(language, "صدای یادآوری", "Reminder sound", "صوت التذكير"), color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(
                        selected = selectedSoundMode == "DEFAULT",
                        onClick = {
                            ReminderScheduler.setSound(context, "DEFAULT")
                            selectedSoundMode = "DEFAULT"
                            selectedSoundUri = null
                        },
                        label = { Text(localizedText(language, "صدای دستگاه", "Device sound", "صوت الجهاز")) }
                    )
                    FilterChip(
                        selected = selectedSoundMode == "SILENT",
                        onClick = {
                            ReminderScheduler.setSound(context, "SILENT")
                            selectedSoundMode = "SILENT"
                            selectedSoundUri = null
                        },
                        label = { Text(localizedText(language, "بی‌صدا", "Silent", "صامت")) }
                    )
                }
                OutlinedButton(onClick = { soundPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedSoundMode == "CUSTOM") localizedText(language, "تغییر فایل صدای انتخاب‌شده", "Change selected audio", "تغيير الصوت المحدد") else localizedText(language, "انتخاب صدا از حافظه", "Choose audio from storage", "اختر صوتاً من الذاكرة"))
                }
                if (selectedSoundMode == "CUSTOM") {
                    Text(
                        selectedSoundUri?.lastPathSegment ?: "فایل صوتی سفارشی انتخاب شده",
                        color = Muted,
                        fontSize = 10.sp
                    )
                    TextButton(onClick = {
                        selectedSoundUri?.let { uri ->
                            runCatching { RingtoneManager.getRingtone(context, uri)?.play() }
                        }
                    }) { Text("پخش نمونه صدا") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(localizedText(language, "کاهش انیمیشن‌ها", "Reduce motion", "تقليل الحركة"), color = White)
                        Text(localizedText(language, "برای حرکت کمتر و مصرف پایین‌تر", "Less motion and lower visual activity", "حركة أقل وتأثير بصري أخف"), color = Muted, fontSize = 10.sp)
                    }
                    Switch(checked = settings.reducedMotion, onCheckedChange = { onSettingsChange(settings.copy(reducedMotion = it)) })
                }
            }
        }
    }
    if (showColorStudio) {
        CustomAccentDialog(
            initialAccent = settings.accent,
            onDismiss = { showColorStudio = false },
            onSave = { colorHex ->
                onSettingsChange(settings.copy(accent = colorHex))
                showColorStudio = false
            }
        )
    }
}

private fun tintNumberPickerText(picker: NumberPicker, color: Int) {
    for (index in 0 until picker.childCount) {
        (picker.getChildAt(index) as? TextView)?.setTextColor(color)
    }
}

@Composable
private fun HabitEditor(
    initial: HabitEntity?,
    dismiss: () -> Unit,
    save: (String, String, String, String, String, String) -> Unit
) {
    val language = LocalSparkLanguage.current
    val wheelTextColor = White.toArgb()
    val useLightWheelTheme = wheelTextColor == Color(0xFF171923).toArgb()
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "عمومی") }
    val days = listOf(
        "MONDAY" to localizedText(language, "دوشنبه", "Monday", "الاثنين"), "TUESDAY" to localizedText(language, "سه‌شنبه", "Tuesday", "الثلاثاء"), "WEDNESDAY" to localizedText(language, "چهارشنبه", "Wednesday", "الأربعاء"),
        "THURSDAY" to localizedText(language, "پنجشنبه", "Thursday", "الخميس"), "FRIDAY" to localizedText(language, "جمعه", "Friday", "الجمعة"), "SATURDAY" to localizedText(language, "شنبه", "Saturday", "السبت"), "SUNDAY" to localizedText(language, "یکشنبه", "Sunday", "الأحد")
    )
    var selectedDays by remember(initial) {
        mutableStateOf(
            initial?.scheduledDays?.split(",")?.map { it.trim().uppercase(Locale.ROOT) }
                ?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        )
    }
    var reminderEnabled by remember(initial) {
        mutableStateOf(initial?.reminderTime?.isNotBlank() == true)
    }
    var reminderMode by remember(initial) {
        mutableStateOf(initial?.reminderMode ?: "NOTIFICATION")
    }
    var selectedTime by remember(initial) {
        mutableStateOf(initial?.reminderTime?.takeIf { it.isNotBlank() })
    }
    val initialTime = remember(initial) {
        runCatching { java.time.LocalTime.parse(initial?.reminderTime.orEmpty()) }
            .getOrNull()
    }
    var draftHour by remember(initial) { mutableIntStateOf(initialTime?.hour ?: 0) }
    var draftMinute by remember(initial) { mutableIntStateOf(initialTime?.minute ?: 0) }
    var showTimePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = Panel,
        title = { Text(localizedText(language, if (initial == null) "عادت جدید" else "ویرایش عادت", if (initial == null) "New habit" else "Edit habit", if (initial == null) "عادة جديدة" else "تعديل العادة"), color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it; error = null }, label = { Text(localizedText(language, "نام عادت", "Habit name", "اسم العادة")) }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text(localizedText(language, "دسته‌بندی", "Category", "التصنيف")) }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text(localizedText(language, "توضیحات (اختیاری)", "Description (optional)", "الوصف (اختياري)")) }, minLines = 2)

                Text(localizedText(language, "روزهای تکرار عادت", "Repeat days", "أيام التكرار"), color = White, fontWeight = FontWeight.SemiBold)
                Text(
                    localizedText(language, "عادت برای کارهایی است که مرتب انجام می‌دهی. برای خرید یا کار یک‌باره، از بخش «کارها» استفاده کن.", "Habits are for routines. Use Tasks for one-off items such as shopping.", "العادات للأنشطة المتكررة. استخدم المهام للأعمال لمرة واحدة مثل التسوق."),
                    color = Muted, fontSize = 11.sp
                )
                days.chunked(4).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        group.forEach { (key, label) ->
                            val selected = key in selectedDays
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedDays = if (selected) selectedDays - key else selectedDays + key
                                    error = null
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = Muted,
                                    selectedContainerColor = Color(0xFF1976D2),
                                    selectedLabelColor = Color.White
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF1976D2) else Muted.copy(alpha = 0.65f)
                                )
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(localizedText(language, "یادآوری ساعت‌دار", "Timed reminder", "تذكير بوقت محدد"), color = White)
                        Text(localizedText(language, "اختیاری است؛ اگر ساعت انتخاب نکنی، یادآوری تنظیم نمی‌شود.", "Optional. No reminder is scheduled without a selected time.", "اختياري. لن يتم ضبط تذكير دون اختيار وقت."), color = Muted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = {
                            reminderEnabled = it
                            if (!it) selectedTime = null
                        }
                    )
                }
                if (reminderEnabled) {
                    Text(localizedText(language, "روش یادآوری", "Reminder style", "طريقة التذكير"), color = White, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip(
                            selected = reminderMode == "NOTIFICATION",
                            onClick = { reminderMode = "NOTIFICATION" },
                            label = { Text(localizedText(language, "اعلان", "Notification", "إشعار")) }
                        )
                        FilterChip(
                            selected = reminderMode == "ALARM",
                            onClick = { reminderMode = "ALARM" },
                            label = { Text(localizedText(language, "زنگ هشدار", "Ringing alarm", "منبه رنان")) }
                        )
                    }
                    Text(
                        localizedText(language, "حالت زنگ هشدار تا وقتی خودت متوقفش نکنی ادامه دارد.", "Alarm mode keeps ringing until you stop it.", "يستمر المنبه بالرنين حتى توقفه."),
                        color = Muted, fontSize = 10.sp
                    )
                    OutlinedButton(
                        onClick = {
                            val parts = selectedTime?.split(":")
                            draftHour = parts?.getOrNull(0)?.toIntOrNull() ?: 0
                            draftMinute = parts?.getOrNull(1)?.toIntOrNull() ?: 0
                            showTimePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Muted.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF42A5F5))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            selectedTime ?: localizedText(language, "انتخاب ساعت", "Choose time", "اختر الوقت"),
                            color = if (selectedTime == null) Muted else White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(localizedText(language, "تنظیم", "Set time", "ضبط الوقت"), color = Color(0xFF64B5F6), fontSize = 12.sp)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) error = localizedText(language, "عنوان نمی‌تواند خالی باشد.", "A title is required.", "العنوان مطلوب.")
                else if (selectedDays.isEmpty()) error = localizedText(language, "حداقل یک روز برای تکرار عادت انتخاب کن.", "Select at least one repeat day.", "اختر يوماً واحداً على الأقل للتكرار.")
                else {
                    save(
                        title, note, category,
                        days.filter { it.first in selectedDays }.joinToString(",") { it.first },
                        if (reminderEnabled) selectedTime.orEmpty() else "",
                        if (reminderEnabled) reminderMode else "NOTIFICATION"
                    )
                }
            }) { Text(localizedText(language, "ذخیره", "Save", "حفظ"), color = Violet) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(localizedText(language, "انصراف", "Cancel", "إلغاء"), color = Muted) } }
    )

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Panel,
            title = { Text(localizedText(language, "انتخاب ساعت یادآوری", "Choose reminder time", "اختر وقت التذكير"), color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text(localizedText(language, "ساعت", "Hour", "الساعة"), color = Muted)
                        Text(localizedText(language, "دقیقه", "Minute", "الدقيقة"), color = Muted)
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        factory = { context ->
                            val wheelContext = ContextThemeWrapper(
                                context,
                                if (useLightWheelTheme) android.R.style.Theme_Material_Light
                                else android.R.style.Theme_Material
                            )
                            LinearLayout(wheelContext).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER
                                addView(
                                    NumberPicker(wheelContext).apply {
                                        minValue = 0
                                        maxValue = 23
                                        wrapSelectorWheel = true
                                        setFormatter { value -> String.format(Locale.ROOT, "%02d", value) }
                                        setOnValueChangedListener { _, _, newValue -> draftHour = newValue }
                                        post { tintNumberPickerText(this, wheelTextColor) }
                                    },
                                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                                )
                                addView(
                                    NumberPicker(wheelContext).apply {
                                        minValue = 0
                                        maxValue = 59
                                        wrapSelectorWheel = true
                                        setFormatter { value -> String.format(Locale.ROOT, "%02d", value) }
                                        setOnValueChangedListener { _, _, newValue -> draftMinute = newValue }
                                        post { tintNumberPickerText(this, wheelTextColor) }
                                    },
                                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                                )
                            }
                        },
                        update = { wheels ->
                            val hourWheel = wheels.getChildAt(0) as NumberPicker
                            val minuteWheel = wheels.getChildAt(1) as NumberPicker
                            tintNumberPickerText(hourWheel, wheelTextColor)
                            tintNumberPickerText(minuteWheel, wheelTextColor)
                            if (hourWheel.value != draftHour) hourWheel.value = draftHour
                            if (minuteWheel.value != draftMinute) minuteWheel.value = draftMinute
                        }
                    )
                    Text(
                        String.format(Locale.ROOT, "%02d:%02d", draftHour, draftMinute),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF64B5F6),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = String.format(Locale.ROOT, "%02d:%02d", draftHour, draftMinute)
                    showTimePicker = false
                }) { Text(localizedText(language, "تأیید", "Confirm", "تأكيد"), color = Color(0xFF64B5F6)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(localizedText(language, "انصراف", "Cancel", "إلغاء"), color = Muted) }
            }
        )
    }
}
