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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        Text("مدیریت وظایف", style = MaterialTheme.typography.headlineSmall)
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
                Text(if (tasks.isEmpty()) "هنوز وظیفه‌ای ثبت نشده." else "موردی با این فیلتر پیدا نشد.")
                Text("برای ثبت اولین وظیفه از دکمه افزودن استفاده کن.")
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
                contentDescription = if (task.completed) "بازگرداندن وظیفه" else "تکمیل وظیفه",
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
                task.dueDate?.let { "مهلت: $it" }
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
                    text = { Text("تکثیر وظیفه") },
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
        title = { Text("حذف دائمی وظیفه؟") },
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
        title = { Text("زیرکارهای وظیفه") },
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
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "عمومی") }
    var dueDate by remember(initial) { mutableStateOf(initial?.dueDate.orEmpty()) }
    var startAt by remember(initial) { mutableStateOf(initial?.startAt?.let { formatTaskTime(it) }.orEmpty()) }
    var endAt by remember(initial) { mutableStateOf(initial?.endAt?.let { formatTaskTime(it) }.orEmpty()) }
    var reminderAt by remember(initial) { mutableStateOf(initial?.reminderAt?.let { formatTaskTime(it) }.orEmpty()) }
    var recurrence by remember(initial) { mutableStateOf(initial?.recurrenceRule) }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }
    var priority by remember(initial) { mutableIntStateOf(initial?.priority ?: 1) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "وظیفه جدید" else "ویرایش وظیفه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it; error = null }, label = { Text("عنوان") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("توضیحات") }, minLines = 2)
                OutlinedTextField(category, { category = it }, label = { Text("دسته‌بندی") }, singleLine = true)
                OutlinedTextField(dueDate, { dueDate = it; error = null }, label = { Text("مهلت (YYYY-MM-DD، اختیاری)") }, singleLine = true)
                OutlinedTextField(startAt, { startAt = it; error = null }, label = { Text("زمان شروع (yyyy-MM-ddTHH:mm، اختیاری)") }, singleLine = true)
                OutlinedTextField(endAt, { endAt = it; error = null }, label = { Text("زمان پایان (yyyy-MM-ddTHH:mm، اختیاری)") }, singleLine = true)
                OutlinedTextField(reminderAt, { reminderAt = it; error = null }, label = { Text("زمان یادآوری (yyyy-MM-ddTHH:mm، اختیاری)") }, singleLine = true)
                Text("تکرار")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(null to "هیچ‌کدام", "DAILY" to "روزانه", "WEEKLY" to "هفتگی", "WEEKDAYS" to "روزهای کاری", "MONTHLY" to "ماهانه").forEach { (rule, label) ->
                        FilterChip(selected = recurrence == rule, onClick = { recurrence = rule }, label = { Text(label) })
                    }
                }
                OutlinedTextField(tags, { tags = it }, label = { Text("برچسب‌ها با ویرگول جدا شوند") }, singleLine = true)
                Text("اولویت")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("کم", "عادی", "زیاد", "فوری").forEachIndexed { index, label ->
                        FilterChip(selected = priority == index, onClick = { priority = index }, label = { Text(label) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank()) {
                    error = "عنوان وظیفه نمی‌تواند خالی باشد."
                } else {
                    val normalizedDate = dueDate.trim().ifBlank { null }
                    try {
                        normalizedDate?.let(LocalDate::parse)
                        val normalizedStart = startAt.trim().ifBlank { null }?.let(::parseTaskTime)
                        val normalizedEnd = endAt.trim().ifBlank { null }?.let(::parseTaskTime)
                        val normalizedReminder = reminderAt.trim().ifBlank { null }?.let(::parseTaskTime)
                        require(normalizedReminder == null || normalizedReminder > System.currentTimeMillis()) { "زمان یادآوری باید در آینده باشد." }
                        require(normalizedStart == null || normalizedEnd == null || normalizedEnd > normalizedStart) {
                            "زمان پایان باید بعد از زمان شروع باشد."
                        }
                        require(recurrence == null || normalizedDate != null) {
                            "برای وظیفه تکرارشونده باید مهلت تعیین شود."
                        }
                        onSave(
                            TaskEntity(
                                id = initial?.id ?: 0,
                                title = title,
                                description = description,
                                category = category,
                                dueDate = normalizedDate,
                                startAt = normalizedStart,
                                endAt = normalizedEnd,
                                reminderAt = normalizedReminder,
                                recurrenceRule = recurrence,
                                recurrenceParentId = initial?.recurrenceParentId,
                                priority = priority,
                                tags = tags,
                                completed = initial?.completed ?: false,
                                completedAt = initial?.completedAt,
                                archived = initial?.archived ?: false,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis()
                            )
                        )
                    } catch (_: Exception) {
                        error = "تاریخ یا زمان نامعتبر است؛ قالب‌های نمایش‌داده‌شده را بررسی کن."
                    }
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}


private fun parseTaskTime(value: String): Long =
    LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun formatTaskTime(value: Long): String =
    java.time.Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
        .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
