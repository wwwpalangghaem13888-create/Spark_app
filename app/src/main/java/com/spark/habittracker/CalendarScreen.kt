package com.spark.habittracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class CalendarMode { DAY, WEEK, MONTH }

@Composable
fun SparkCalendarScreen(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    habits: List<HabitEntity>,
    tasks: List<TaskEntity>,
    logs: List<HabitLogEntity>,
    onToggleHabit: (HabitEntity, Boolean) -> Unit,
    onToggleTask: (TaskEntity, Boolean) -> Unit
) {
    var modeIndex by remember { mutableIntStateOf(2) }
    val mode = CalendarMode.entries[modeIndex]
    val locale = Locale("fa")
    val monthLabel = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }
    val today = LocalDate.now()
    val doneIds = logs.filter { it.date == selectedDate.toString() && it.isCompleted }
        .map { it.habitId }.toSet()
    val dayHabits = habits.filter { habit ->
        habit.isEnabled &&
            !selectedDate.isBefore(Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()) &&
            selectedDate.dayOfWeek.name in habit.scheduledDays.split(",")
    }
    val dayTasks = tasks.filter { it.dueDate == selectedDate.toString() }
    val dates = when (mode) {
        CalendarMode.DAY -> listOf(selectedDate)
        CalendarMode.WEEK -> {
            val start = selectedDate.minusDays(((selectedDate.dayOfWeek.value + 1) % 7).toLong())
            (0L..6L).map(start::plusDays)
        }
        CalendarMode.MONTH -> {
            val month = YearMonth.from(selectedDate)
            val first = month.atDay(1)
            val offset = (first.dayOfWeek.value + 1) % 7
            buildList<LocalDate?> {
                repeat(offset) { add(null) }
                (1..month.lengthOfMonth()).forEach { add(month.atDay(it)) }
                while (size % 7 != 0) add(null)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("تقویم Spark", style = MaterialTheme.typography.headlineSmall)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                onDateChange(when (mode) {
                    CalendarMode.DAY -> selectedDate.minusDays(1)
                    CalendarMode.WEEK -> selectedDate.minusWeeks(1)
                    CalendarMode.MONTH -> selectedDate.minusMonths(1)
                })
            }) { Icon(Icons.Default.ChevronRight, contentDescription = "بازه قبل") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (mode == CalendarMode.MONTH) monthLabel else selectedDate.toString(), style = MaterialTheme.typography.titleMedium)
                Text(if (selectedDate == today) "امروز" else selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = {
                onDateChange(when (mode) {
                    CalendarMode.DAY -> selectedDate.plusDays(1)
                    CalendarMode.WEEK -> selectedDate.plusWeeks(1)
                    CalendarMode.MONTH -> selectedDate.plusMonths(1)
                })
            }) { Icon(Icons.Default.ChevronLeft, contentDescription = "بازه بعد") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("روز", "هفته", "ماه").forEachIndexed { index, label ->
                FilterChip(selected = modeIndex == index, onClick = { modeIndex = index }, label = { Text(label) })
            }
        }
        if (mode != CalendarMode.DAY) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                    Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                }
            }
            dates.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.weight(1f).size(38.dp))
                        } else {
                            val habitCount = habits.count { habit ->
                                habit.isEnabled &&
                                    !date.isBefore(Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()) &&
                                    date.dayOfWeek.name in habit.scheduledDays.split(",")
                            }
                            val taskCount = tasks.count { it.dueDate == date.toString() }
                            val isSelected = date == selectedDate
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .clickable { onDateChange(date) }.padding(vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (habitCount + taskCount == 0) " " else "•".repeat(minOf(3, habitCount + taskCount)),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
        Text("برنامه ${selectedDate}", style = MaterialTheme.typography.titleMedium)
        if (dayHabits.isEmpty() && dayTasks.isEmpty()) {
            Text("برای این روز برنامه‌ای ثبت نشده است.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayHabits.size + dayTasks.size) { index ->
                    if (index < dayHabits.size) {
                        val habit = dayHabits[index]
                        val completed = habit.id in doneIds
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { onToggleHabit(habit, !completed) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (completed) Icons.Default.CheckCircle else Icons.Default.Circle, null, tint = MaterialTheme.colorScheme.primary)
                            Text(habit.title, Modifier.weight(1f).padding(start = 10.dp))
                            Text("عادت", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        val task = dayTasks[index - dayHabits.size]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { onToggleTask(task, !task.completed) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (task.completed) Icons.Default.CheckCircle else Icons.Default.Circle, null, tint = MaterialTheme.colorScheme.primary)
                            Text(task.title, Modifier.weight(1f).padding(start = 10.dp))
                            Text("وظیفه", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
