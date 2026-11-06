package com.spark.habittracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch

@Composable
fun TaskListScreen(
    tasks: List<TaskEntity>,
    onToggle: (TaskEntity, Boolean) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onArchive: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onDuplicate: (TaskEntity) -> Unit,
    onRestore: (TaskEntity) -> Unit,
    archivedTasks: List<TaskEntity>,
    productivityDao: ProductivityDao
) {
    var showArchived by remember { mutableStateOf(false) }
    var selectedSubtaskTaskId by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val sourceTasks = if (showArchived) archivedTasks else tasks
    val visible = sourceTasks.filter { task ->
        val matchesText = search.isBlank() ||
            task.title.contains(search, ignoreCase = true) ||
            task.description.contains(search, ignoreCase = true) ||
            task.tags.contains(search, ignoreCase = true)
        val matchesFilter = when (filter) {
            1 -> !task.completed
            2 -> task.completed
            3 -> taskIsOverdue(task, today)
            else -> true
        }
        matchesText && matchesFilter
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("کارها", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("جست‌وجوی عنوان، توضیح یا برچسب") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("همه", "باز", "انجام‌شده", "عقب‌افتاده").forEachIndexed { index, label ->
                FilterChip(
                    selected = filter == index,
                    onClick = { filter = index },
                    label = { Text(label) }
                )
            }
            FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text(if (showArchived) "نمایش فعال‌ها" else "بایگانی") })
        }
        if (visible.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.AddTask, contentDescription = null)
                Text(if (sourceTasks.isEmpty()) if (showArchived) "کار بایگانی‌شده‌ای وجود ندارد." else "هنوز کاری ثبت نشده." else "موردی با این فیلتر پیدا نشد.")
                Text("برای ثبت اولین کار از دکمه افزودن استفاده کن.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(visible, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { onToggle(task, !task.completed) },
                        onEdit = { onEdit(task) },
                        onArchive = { onArchive(task) },
                        onDelete = { onDelete(task) },
                        onDuplicate = { onDuplicate(task) },
                        onManageSubtasks = { selectedSubtaskTaskId = task.id },
                        onRestore = { onRestore(task) }
                    )
                }
            }
        }
    }
    selectedSubtaskTaskId?.let { taskId ->
        TaskSubtasksDialog(taskId, productivityDao, onDismiss = { selectedSubtaskTaskId = null })
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onManageSubtasks: () -> Unit,
    onRestore: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val overdue = taskIsOverdue(task, LocalDate.now())
    Row(
        Modifier.fillMaxWidth().padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (task.completed) Icons.Default.CheckCircle else Icons.Default.Circle,
                contentDescription = if (task.completed) "بازگرداندن کار" else "تکمیل کار",
                tint = if (task.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 10.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
            )
            val details = listOfNotNull(
                task.category,
                taskPriorityLabel(task.priority),
                task.dueDate?.let { "مهلت: $it" },
                task.startAt?.let { "شروع: " + formatTaskTime(it) },
                task.endAt?.let { "پایان: " + formatTaskTime(it) }
            ).joinToString(" • ")
            Text(details, style = MaterialTheme.typography.bodySmall)
            if (task.description.isNotBlank()) {
                Text(task.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            if (overdue) Text("عقب‌افتاده", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            if (task.tags.isNotBlank()) Text(task.tags, style = MaterialTheme.typography.labelSmall)
        }
        Box {
            TextButton(onClick = { menuOpen = true }) { Text("•••") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("ویرایش") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("زیرکارها") },
                    onClick = { menuOpen = false; onManageSubtasks() }
                )
                DropdownMenuItem(
                    text = { Text("کپی کار") },
                    onClick = { menuOpen = false; onDuplicate() }
                )
                DropdownMenuItem(
                    text = { Text(if (task.archived) "بازیابی از بایگانی" else "بایگانی") },
                    leadingIcon = { Icon(Icons.Default.Archive, null) },
                    onClick = { menuOpen = false; if (task.archived) onRestore() else onArchive() }
                )
                DropdownMenuItem(
                    text = { Text("حذف دائمی") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; confirmDelete = true }
                )
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف دائمی کار؟") },
        text = { Text("این عملیات قابل بازگشت نیست و زیرکارها و تاریخچهٔ وابسته نیز حذف می‌شوند.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } }
    )
}


@Composable
private fun TaskSubtasksDialog(taskId: Long, dao: ProductivityDao, onDismiss: () -> Unit) {
    val subtasks by dao.observeSubtasks(taskId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("زیرکارهای این کار") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("عنوان زیرکار") }, singleLine = true)
                subtasks.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = item.isCompleted,
                            onCheckedChange = { checked -> scope.launch { dao.setSubtaskCompleted(item.id, checked) } }
                        )
                        Text(item.title, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { dao.deleteSubtask(item.id) } }) { Text("حذف") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) scope.launch {
                    dao.saveSubtask(TaskSubtaskEntity(taskId = taskId, title = title.trim(), position = subtasks.size))
                    title = ""
                }
            }) { Text("افزودن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

@Composable
fun TaskEditorDialog(
    initial: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit
) {
    val language = LocalSparkLanguage.current
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "عمومی") }
    var dueDate by remember(initial) { mutableStateOf(initial?.dueDate.orEmpty()) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    val parsedDueDate = remember(dueDate) { runCatching { LocalDate.parse(dueDate) }.getOrNull() }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = parsedDueDate?.toEpochDay()?.times(86_400_000L)
    )
    var startAt by remember(initial) { mutableStateOf(initial?.startAt?.let { formatTaskTime(it) }.orEmpty()) }
    var endAt by remember(initial) { mutableStateOf(initial?.endAt?.let { formatTaskTime(it) }.orEmpty()) }
    var reminderAt by remember(initial) { mutableStateOf(initial?.reminderAt?.let { formatTaskTime(it) }.orEmpty()) }
    var recurrence by remember(initial) {
        mutableStateOf(initial?.recurrenceRule?.substringBefore(":") ?: "WEEKLY")
    }
    var taskKind by remember(initial) {
        mutableStateOf(
            when {
                initial?.recurrenceRule == null -> "ONE_OFF"
                initial.repeatRemainingCount != null -> "LIMITED"
                else -> "PERMANENT"
            }
        )
    }
    var repeatTotalCountText by remember(initial) {
        mutableStateOf(
            ((initial?.repeatRemainingCount ?: 2) + if (initial?.repeatRemainingCount != null) 1 else 0)
                .coerceIn(if (initial?.repeatRemainingCount == 0) 1 else 2, 1000).toString()
        )
    }
    var reminderEnabled by remember(initial) { mutableStateOf(initial?.reminderAt != null) }
    val weekdays = listOf(
        "MONDAY" to localizedText(language, "دوشنبه", "Monday", "الاثنين"),
        "TUESDAY" to localizedText(language, "سه‌شنبه", "Tuesday", "الثلاثاء"),
        "WEDNESDAY" to localizedText(language, "چهارشنبه", "Wednesday", "الأربعاء"),
        "THURSDAY" to localizedText(language, "پنجشنبه", "Thursday", "الخميس"),
        "FRIDAY" to localizedText(language, "جمعه", "Friday", "الجمعة"),
        "SATURDAY" to localizedText(language, "شنبه", "Saturday", "السبت"),
        "SUNDAY" to localizedText(language, "یکشنبه", "Sunday", "الأحد")
    )
    var weeklyDays by remember(initial) {
        mutableStateOf(
            initial?.recurrenceRule?.substringAfter("WEEKLY:", "")
                ?.takeIf { it.isNotBlank() }
                ?.split(",")?.map { it.trim().uppercase() }?.toSet()
                ?: if (initial?.recurrenceRule == "WEEKLY") {
                    runCatching { LocalDate.parse(initial.dueDate).dayOfWeek.name }.getOrNull()?.let { setOf(it) } ?: emptySet()
                } else emptySet()
        )
    }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }
    var priority by remember(initial) { mutableIntStateOf(initial?.priority ?: 1) }
    var showAdvanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (showDueDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dueDate = LocalDate.ofEpochDay(millis / 86_400_000L).toString()
                        error = null
                    }
                    showDueDatePicker = false
                }) { Text(localizedText(language, "تأیید", "Confirm", "تأكيد")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        dueDate = LocalDate.now().toString()
                        error = null
                        showDueDatePicker = false
                    }) { Text(localizedText(language, "امروز", "Today", "اليوم")) }
                    TextButton(onClick = { showDueDatePicker = false }) {
                        Text(localizedText(language, "بستن", "Close", "إغلاق"))
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(localizedText(language, if (initial == null || initial.id == 0L) "کار جدید" else "ویرایش کار",
                if (initial == null || initial.id == 0L) "New task" else "Edit task",
                if (initial == null || initial.id == 0L) "عمل جديد" else "تعديل العمل"))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    localizedText(language, "یک کار را ساده ثبت کن؛ جزئیات اضافه لازم نیست.", "Add a task simply; extra details are optional.", "أضف عملاً ببساطة؛ التفاصيل الإضافية اختيارية."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title, onValueChange = { title = it; error = null },
                    label = { Text(localizedText(language, "عنوان کار", "Task title", "عنوان العمل")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category, onValueChange = { category = it },
                    label = { Text(localizedText(language, "دسته‌بندی", "Category", "التصنيف")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(localizedText(language, "تاریخ انجام", "Due date", "تاريخ الإنجاز")) },
                    placeholder = { Text(localizedText(language, "برای امروز یا یک تاریخ دیگر انتخاب کن", "Choose today or another date", "اختر اليوم أو تاريخاً آخر")) },
                    trailingIcon = {
                        TextButton(onClick = { showDueDatePicker = true }) {
                            Text(localizedText(language, "انتخاب", "Choose", "اختيار"))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clickable { showDueDatePicker = true }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dueDate == LocalDate.now().toString(),
                        onClick = { dueDate = LocalDate.now().toString(); error = null },
                        label = { Text(localizedText(language, "فقط امروز", "Today", "اليوم")) }
                    )
                    if (dueDate.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick = { dueDate = ""; error = null },
                            label = { Text(localizedText(language, "بدون تاریخ", "No date", "بلا تاريخ")) }
                        )
                    }
                }

                Text(
                    localizedText(language, "این کار چه مدلی است؟", "How should this task repeat?", "كيف تريد تكرار هذا العمل؟"),
                    style = MaterialTheme.typography.titleSmall
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(
                        selected = taskKind == "ONE_OFF",
                        onClick = { taskKind = "ONE_OFF"; error = null },
                        label = { Text(localizedText(language, "فقط یک‌بار", "One time only", "مرة واحدة فقط")) }
                    )
                    FilterChip(
                        selected = taskKind == "LIMITED",
                        onClick = { taskKind = "LIMITED"; error = null },
                        label = { Text(localizedText(language, "موقت؛ تعداد مشخص", "Temporary; limited repeats", "مؤقت؛ عدد مرات محدد")) }
                    )
                    FilterChip(
                        selected = taskKind == "PERMANENT",
                        onClick = { taskKind = "PERMANENT"; error = null },
                        label = { Text(localizedText(language, "دائمی؛ تا زمان توقف", "Ongoing; until stopped", "مستمر؛ حتى الإيقاف")) }
                    )
                }
                if (taskKind == "LIMITED") {
                    OutlinedTextField(
                        value = repeatTotalCountText,
                        onValueChange = { value ->
                            repeatTotalCountText = value.filter(Char::isDigit).take(4)
                            error = null
                        },
                        label = { Text(localizedText(language, "چند بار در مجموع؟", "Total number of occurrences", "كم مرة بالمجموع؟")) },
                        supportingText = { Text(localizedText(language, "شامل همین نوبت؛ حداقل ۲ بار برای کار جدید", "Including this first one; at least 2 for a new task", "بما فيها المرة الأولى؛ مرتان على الأقل للعمل الجديد")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (taskKind != "ONE_OFF") {
                    Text(
                        localizedText(language, "برنامه تکرار", "Repeat schedule", "جدول التكرار"),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            "WEEKLY" to localizedText(language, "روزهای انتخابی", "Selected days", "أيام محددة"),
                            "DAILY" to localizedText(language, "هر روز", "Every day", "كل يوم"),
                            "WEEKDAYS" to localizedText(language, "روزهای کاری", "Weekdays", "أيام العمل"),
                            "MONTHLY" to localizedText(language, "ماهانه", "Monthly", "شهرياً")
                        ).forEach { (rule, label) ->
                            FilterChip(selected = recurrence == rule, onClick = { recurrence = rule }, label = { Text(label) })
                        }
                    }
                    if (recurrence == "WEEKLY") {
                        Text(localizedText(language, "کدام روزها؟", "On which days?", "في أي أيام؟"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        weekdays.chunked(4).forEach { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                group.forEach { (key, label) ->
                                    FilterChip(
                                        selected = key in weeklyDays,
                                        onClick = { weeklyDays = if (key in weeklyDays) weeklyDays - key else weeklyDays + key },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it; if (!it) reminderAt = "" })
                    Text(localizedText(language, "یادآوری لازم دارم", "Set a reminder", "أريد تذكيراً"))
                }
                if (reminderEnabled) {
                    OutlinedTextField(
                        value = reminderAt,
                        onValueChange = { reminderAt = it; error = null },
                        label = { Text(localizedText(language, "زمان یادآوری (YYYY-MM-DDTHH:mm)", "Reminder time (YYYY-MM-DDTHH:mm)", "وقت التذكير (YYYY-MM-DDTHH:mm)")) },
                        placeholder = { Text(localizedText(language, "خالی = بدون ساعت مشخص", "Leave blank for no fixed time", "اتركه فارغاً لوقت غير محدد")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(
                        if (showAdvanced) localizedText(language, "بستن جزئیات بیشتر", "Hide extra details", "إخفاء التفاصيل الإضافية")
                        else localizedText(language, "جزئیات بیشتر (اختیاری)", "More details (optional)", "تفاصيل إضافية (اختياري)")
                    )
                }
                if (showAdvanced) {
                    OutlinedTextField(
                        value = description, onValueChange = { description = it },
                        label = { Text(localizedText(language, "توضیحات", "Description", "الوصف")) },
                        minLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = startAt, onValueChange = { startAt = it; error = null },
                        label = { Text(localizedText(language, "زمان شروع (YYYY-MM-DDTHH:mm)", "Start (YYYY-MM-DDTHH:mm)", "البداية (YYYY-MM-DDTHH:mm)")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endAt, onValueChange = { endAt = it; error = null },
                        label = { Text(localizedText(language, "زمان پایان (YYYY-MM-DDTHH:mm)", "End (YYYY-MM-DDTHH:mm)", "النهاية (YYYY-MM-DDTHH:mm)")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tags, onValueChange = { tags = it },
                        label = { Text(localizedText(language, "برچسب‌ها با ویرگول جدا شوند", "Comma-separated tags", "وسوم مفصولة بفواصل")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Text(localizedText(language, "اولویت", "Priority", "الأولوية"))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            localizedText(language, "کم", "Low", "منخفضة"),
                            localizedText(language, "عادی", "Normal", "عادية"),
                            localizedText(language, "زیاد", "High", "مرتفعة"),
                            localizedText(language, "فوری", "Urgent", "عاجلة")
                        ).forEachIndexed { index, label ->
                            FilterChip(selected = priority == index, onClick = { priority = index }, label = { Text(label) })
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank()) {
                    error = localizedText(language, "عنوان کار نمی‌تواند خالی باشد.", "Task title cannot be empty.", "لا يمكن أن يكون عنوان العمل فارغاً.")
                } else {
                    val normalizedDate = dueDate.trim().ifBlank { null }
                    try {
                        normalizedDate?.let(LocalDate::parse)
                        val normalizedStart = startAt.trim().ifBlank { null }?.let(::parseTaskTime)
                        val normalizedEnd = endAt.trim().ifBlank { null }?.let(::parseTaskTime)
                        val normalizedReminder = reminderAt.trim().ifBlank { null }?.let(::parseTaskTime)
                        require(normalizedReminder == null || normalizedReminder > System.currentTimeMillis() || normalizedReminder == initial?.reminderAt) {
                            localizedText(language, "زمان یادآوری باید در آینده باشد.", "Reminder must be in the future.", "يجب أن يكون التذكير في المستقبل.")
                        }
                        require(normalizedStart == null || normalizedEnd == null || normalizedEnd > normalizedStart) {
                            localizedText(language, "زمان پایان باید بعد از شروع باشد.", "End time must be after start time.", "يجب أن يكون وقت النهاية بعد البداية.")
                        }
                        val recurring = taskKind != "ONE_OFF"
                        require(!recurring || normalizedDate != null) {
                            localizedText(language, "برای کار تکرارشونده تاریخ شروع لازم است.", "A start date is required for recurring tasks.", "يلزم تاريخ بدء للعمل المتكرر.")
                        }
                        require(!recurring || recurrence != "WEEKLY" || weeklyDays.isNotEmpty()) {
                            localizedText(language, "حداقل یک روز انتخاب کن.", "Select at least one day.", "اختر يوماً واحداً على الأقل.")
                        }
                        val repeatTotalCount = repeatTotalCountText.toIntOrNull()
                        require(
                            taskKind != "LIMITED" ||
                                (repeatTotalCount != null && repeatTotalCount in (if (initial?.repeatRemainingCount == 0) 1..1000 else 2..1000))
                        ) {
                            localizedText(language, "تعداد تکرار معتبر نیست.", "Enter a valid repeat count.", "أدخل عدداً صالحاً للتكرار.")
                        }
                        val rule = if (!recurring) null else when (recurrence) {
                            "WEEKLY" -> "WEEKLY:" + weekdays.filter { it.first in weeklyDays }.joinToString(",") { it.first }
                            else -> recurrence
                        }
                        onSave(
                            TaskEntity(
                                id = initial?.id ?: 0,
                                title = title.trim(),
                                description = description.trim(),
                                category = category.trim().ifBlank { "عمومی" },
                                dueDate = normalizedDate,
                                startAt = normalizedStart,
                                endAt = normalizedEnd,
                                reminderAt = if (reminderEnabled) normalizedReminder else null,
                                recurrenceRule = rule,
                                recurrenceParentId = initial?.recurrenceParentId,
                                priority = priority,
                                tags = tags,
                                completed = initial?.completed ?: false,
                                completedAt = initial?.completedAt,
                                archived = initial?.archived ?: false,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                repeatRemainingCount = if (taskKind == "LIMITED") (repeatTotalCount!! - 1) else null
                            )
                        )
                    } catch (exception: IllegalArgumentException) {
                        error = exception.message ?: localizedText(language, "اطلاعات واردشده معتبر نیست.", "The entered details are invalid.", "البيانات المدخلة غير صالحة.")
                    } catch (_: Exception) {
                        error = localizedText(language, "تاریخ یا زمان نامعتبر است.", "Invalid date or time.", "التاريخ أو الوقت غير صالح.")
                    }
                }
            }) { Text(localizedText(language, "ذخیره", "Save", "حفظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText(language, "انصراف", "Cancel", "إلغاء")) } }
    )
}

@Composable
fun TaskGuideDialog(language: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(localizedText(language, "راهنمای سریع بخش کارها", "Quick guide to Tasks", "دليل سريع لقسم المهام"))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    localizedText(
                        language,
                        "اینجا کارهایی را ثبت کن که باید انجام بدهی؛ لازم نیست همه‌چیز را پیچیده کنی.",
                        "Add things you need to do here. Keep it simple; extra details are optional.",
                        "أضف هنا الأشياء التي تحتاج إلى إنجازها. اجعل الأمر بسيطاً؛ التفاصيل الإضافية اختيارية."
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(localizedText(language, "• یک‌باره: خرید ۲ کیلو گوجه برای امروز.", "• One time: buy 2 kg of tomatoes today.", "• مرة واحدة: شراء كيلوغرامين من الطماطم اليوم."))
                Text(localizedText(language, "• موقت: تمرین در سه نوبت؛ تعداد و روزها را خودت تعیین کن.", "• Limited: repeat an activity three times; choose the count and days.", "• مؤقت: كرر نشاطاً ثلاث مرات؛ اختر العدد والأيام."))
                Text(localizedText(language, "• دائمی: کاری که هر هفته یا هر روز ادامه دارد، تا وقتی متوقفش کنی.", "• Ongoing: something that repeats weekly or daily until you stop it.", "• مستمر: عمل يتكرر أسبوعياً أو يومياً حتى توقفه."))
                Text(localizedText(language, "یادآوری و ساعت هم اختیاری هستند.", "Reminders and times are optional.", "التذكير والوقت اختياريان."))
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(localizedText(language, "فهمیدم", "Got it", "فهمت"))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(localizedText(language, "رد کردن راهنما", "Skip guide", "تخطي الدليل"))
            }
        }
    )
}

private fun parseTaskTime(value: String): Long =
    LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun formatTaskTime(value: Long): String =
    java.time.Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
        .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
