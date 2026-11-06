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
import java.time.format.DateTimeParseException

@Composable
fun TaskListScreen(
    tasks: List<TaskEntity>,
    onToggle: (TaskEntity, Boolean) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onArchive: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val visible = tasks.filter { task ->
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
                        onDelete = { onDelete(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
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
                    text = { Text("بایگانی") },
                    leadingIcon = { Icon(Icons.Default.Archive, null) },
                    onClick = { menuOpen = false; onArchive() }
                )
                DropdownMenuItem(
                    text = { Text("حذف دائمی") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
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
                        onSave(
                            TaskEntity(
                                id = initial?.id ?: 0,
                                title = title,
                                description = description,
                                category = category,
                                dueDate = normalizedDate,
                                priority = priority,
                                tags = tags,
                                completed = initial?.completed ?: false,
                                completedAt = initial?.completedAt,
                                archived = initial?.archived ?: false,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis()
                            )
                        )
                    } catch (_: DateTimeParseException) {
                        error = "تاریخ باید با قالب YYYY-MM-DD باشد."
                    }
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
