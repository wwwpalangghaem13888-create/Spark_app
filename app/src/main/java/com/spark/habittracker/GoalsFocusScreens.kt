package com.spark.habittracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

@Composable
fun GoalsScreen(dao: ProductivityDao, tasks: List<TaskEntity>, habits: List<HabitEntity>, modifier: Modifier = Modifier) {
    val goals by dao.observeGoals().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("5") }
    var targetDate by remember { mutableStateOf("") }
    var selectedTaskIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedHabitIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("اهداف من", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("پیشرفت فقط از فعالیت‌های ثبت‌شده محاسبه می‌شود.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { title = ""; description = ""; target = "5"; targetDate = ""; selectedTaskIds = emptySet(); selectedHabitIds = emptySet(); error = null; editorOpen = true }) { Text("هدف جدید") }
            }
        }
        if (goals.isEmpty()) item {
            Card {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("هنوز هدفی ثبت نشده است.")
                    Text("یک هدف قابل‌اندازه‌گیری بساز و وظایف مرتبط را به آن وصل کن.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(goals, key = { it.id }) { goal ->
            val linkedIds by produceState(initialValue = emptyList<Long>(), goal.id) { value = dao.linkedTaskIds(goal.id) }
            val linkedHabitIds by produceState(initialValue = emptyList<Long>(), goal.id) { value = dao.linkedHabitIds(goal.id) }
            val linkedTasks = tasks.filter { it.id in linkedIds }
            val endDate = goal.targetDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
            val startMillis = LocalDate.parse(goal.startDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = endDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            val completed by produceState(initialValue = 0, goal.id, tasks, linkedHabitIds) {
                value = dao.completedLinkedTasks(goal.id, startMillis, endMillis) +
                    dao.completedLinkedHabits(goal.id, goal.startDate, endDate.toString())
            }
            val progress = goalProgressRatio(completed, goal.targetValue)
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(goal.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (goal.description.isNotBlank()) Text(goal.description, style = MaterialTheme.typography.bodySmall)
                    Text(completed.toString() + " از " + goal.targetValue.toInt() + " فعالیت مرتبط", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(8.dp))
                    Text((progress * 100).toInt().toString() + "٪ • " + linkedTasks.size + " وظیفه و " + linkedHabitIds.size + " عادت متصل", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { scope.launch { dao.archiveGoal(goal.id, System.currentTimeMillis()) } }) { Text("بایگانی هدف") }
                }
            }
        }
    }

    if (editorOpen) AlertDialog(
        onDismissRequest = { editorOpen = false },
        title = { Text("ساخت هدف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it; error = null }, label = { Text("عنوان هدف") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("توضیحات") })
                OutlinedTextField(target, { target = it; error = null }, label = { Text("تعداد فعالیت موردنیاز") }, singleLine = true)
                Text("وظایف مرتبط")
                tasks.filter { !it.archived }.forEach { task ->
                    FilterChip(selected = task.id in selectedTaskIds,
                        onClick = { selectedTaskIds = if (task.id in selectedTaskIds) selectedTaskIds - task.id else selectedTaskIds + task.id },
                        label = { Text(task.title) })
                }
                Text("عادت‌های مرتبط")
                habits.filter { it.isEnabled }.forEach { habit ->
                    FilterChip(selected = habit.id in selectedHabitIds,
                        onClick = { selectedHabitIds = if (habit.id in selectedHabitIds) selectedHabitIds - habit.id else selectedHabitIds + habit.id },
                        label = { Text(habit.title) })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedTarget = target.toDoubleOrNull()
                if (title.isBlank()) error = "عنوان هدف الزامی است."
                else if (parsedTarget == null || !parsedTarget.isFinite() || parsedTarget <= 0.0) error = "هدف باید عددی بزرگ‌تر از صفر باشد."
                else scope.launch {
                    val parsedDate = targetDate.trim().ifBlank { null }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    if (targetDate.isNotBlank() && parsedDate == null) {
                        error = "تاریخ هدف باید با قالب yyyy-MM-dd باشد."
                        return@launch
                    }
                    if (parsedDate != null && parsedDate.isBefore(LocalDate.now())) {
                        error = "تاریخ هدف نمی‌تواند قبل از امروز باشد."
                        return@launch
                    }
                    val id = dao.insertGoal(GoalEntity(title = title.trim(), description = description.trim(),
                        targetValue = parsedTarget, startDate = LocalDate.now().toString(), targetDate = parsedDate?.toString()))
                    selectedTaskIds.forEach { dao.linkTaskToGoal(GoalTaskLinkEntity(id, it)) }
                    selectedHabitIds.forEach { dao.linkHabitToGoal(GoalHabitLinkEntity(id, it)) }
                    editorOpen = false
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("انصراف") } }
    )
}

@Composable
fun FocusScreen(dao: ProductivityDao, modifier: Modifier = Modifier) {
    val session by dao.observeActiveFocusSession().collectAsState(initial = null)
    val activeSession = session
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedMinutes by remember { mutableLongStateOf(25L) }
    var selectedMode by remember { mutableStateOf("FOCUS") }
    var customMinutes by remember { mutableStateOf("25") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session?.id, session?.pausedAt) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val remaining = activeSession?.let { focusRemainingSeconds(it, now) }
    val elapsed = activeSession?.let { focusElapsedSeconds(it, now) } ?: 0L
    val displaySeconds = if (activeSession?.mode == "STOPWATCH") elapsed else (remaining ?: 0L)
    val minutes = displaySeconds / 60
    val seconds = displaySeconds % 60
    val progress = activeSession?.let {
        if (it.mode == "STOPWATCH" || it.plannedDurationSeconds <= 0) 0f
        else (1f - (remaining ?: 0L).toFloat() / it.plannedDurationSeconds).coerceIn(0f, 1f)
    } ?: 0f

    Column(modifier = modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Spark Focus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("جلسه با زمان واقعی ذخیره می‌شود؛ بستن برنامه تایمر را صفر نمی‌کند.", style = MaterialTheme.typography.bodySmall)
        if (activeSession == null) {
            Text("نوع جلسه")
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("FOCUS" to "تمرکز", "SHORT_BREAK" to "استراحت کوتاه", "LONG_BREAK" to "استراحت بلند", "STOPWATCH" to "کرنومتر").forEach { (key, label) ->
                    FilterChip(selected = selectedMode == key, onClick = { selectedMode = key }, label = { Text(label) })
                }
            }
            if (selectedMode != "STOPWATCH") {
                Text("مدت جلسه")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (selectedMode == "FOCUS") listOf(25L, 50L, 90L) else listOf(5L, 15L)).forEach { value ->
                        FilterChip(selected = selectedMinutes == value, onClick = { selectedMinutes = value; customMinutes = value.toString() },
                            label = { Text(value.toString() + " دقیقه") })
                    }
                }
                OutlinedTextField(customMinutes, {
                    customMinutes = it.filter(Char::isDigit)
                    customMinutes.toLongOrNull()?.takeIf { n -> n in 1L..180L }?.let { n -> selectedMinutes = n }
                }, label = { Text("مدت دلخواه (۱ تا ۱۸۰ دقیقه)") }, singleLine = true)
            }
            Button(onClick = { scope.launch {
                val start = System.currentTimeMillis()
                val planned = if (selectedMode == "STOPWATCH") 31_536_000L else selectedMinutes * 60
                val id = dao.insertFocusSession(FocusSessionEntity(mode = selectedMode, startedAt = start, plannedDurationSeconds = planned))
                if (selectedMode != "STOPWATCH") {
                    val reminder = ReminderEntity(ownerType = "FOCUS", ownerId = id, triggerAt = start + planned * 1000)
                    val reminderId = dao.insertReminder(reminder)
                    ReminderScheduler.schedule(context, reminder.copy(id = reminderId))
                }
            } }) { Text(if (selectedMode == "STOPWATCH") "شروع کرنومتر" else "شروع جلسه") }
        } else {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format(Locale.ROOT, "%02d:%02d", minutes, seconds), fontSize = 52.sp, fontWeight = FontWeight.Black)
                    Text(when {
                        activeSession.mode == "STOPWATCH" -> "کرنومتر"
                        activeSession.mode == "SHORT_BREAK" -> "استراحت کوتاه"
                        activeSession.mode == "LONG_BREAK" -> "استراحت بلند"
                        activeSession.pausedAt != null -> "متوقف‌شده"
                        else -> "در حال تمرکز"
                    }, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (activeSession.mode != "STOPWATCH") LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activeSession.pausedAt == null) Button(onClick = { scope.launch {
                    dao.updateFocusPause(activeSession.id, now, activeSession.accumulatedPausedSeconds)
                    dao.disableRemindersForOwner("FOCUS", activeSession.id)
                } }) { Text("مکث") }
                else Button(onClick = { scope.launch {
                    val pausedSeconds = ((now - (activeSession.pausedAt ?: now)) / 1000).coerceAtLeast(0)
                    dao.updateFocusPause(activeSession.id, null, activeSession.accumulatedPausedSeconds + pausedSeconds)
                    val left = if (activeSession.mode == "STOPWATCH") 0L else remaining ?: 0L
                    if (left > 0) {
                        dao.deleteRemindersForOwner("FOCUS", activeSession.id)
                        val reminder = ReminderEntity(ownerType = "FOCUS", ownerId = activeSession.id, triggerAt = now + left * 1000)
                        val reminderId = dao.insertReminder(reminder)
                        ReminderScheduler.schedule(context, reminder.copy(id = reminderId))
                    }
                } }) { Text("ادامه") }
                TextButton(onClick = { scope.launch {
                    val actual = if (activeSession.mode == "STOPWATCH") elapsed else (activeSession.plannedDurationSeconds - (remaining ?: 0L)).coerceAtLeast(0)
                    dao.finishFocusSession(activeSession.id, actual, activeSession.mode == "STOPWATCH" || remaining == 0L, now)
                    dao.disableRemindersForOwner("FOCUS", activeSession.id)
                } }) { Text("پایان جلسه") }
            }
            if (activeSession.mode != "STOPWATCH" && remaining == 0L) Text("زمان جلسه تمام شد.", color = MaterialTheme.colorScheme.primary)
        }
    }
}
