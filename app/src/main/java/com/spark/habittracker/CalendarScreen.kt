package com.spark.habittracker

import android.icu.text.SimpleDateFormat as IcuSimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.ULocale
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
import java.util.Date
import android.icu.util.TimeZone

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
    val language = LocalSparkLanguage.current
    var modeIndex by remember { mutableIntStateOf(2) }
    val mode = CalendarMode.entries[modeIndex]
    val locale = when (language) {
        "en" -> Locale.US
        "ar" -> Locale.forLanguageTag("ar")
        else -> Locale("fa")
    }
    fun dateLabel(date: LocalDate, pattern: String): String =
        if (language == "fa") persianDateLabel(date, pattern)
        else date.format(DateTimeFormatter.ofPattern(pattern, locale))
    val monthLabel = remember(selectedDate, language) { dateLabel(selectedDate, "MMMM yyyy") }
    val today = LocalDate.now()
    val doneIds = logs.filter { it.date == selectedDate.toString() && it.isCompleted }
        .map { it.habitId }.toSet()
    val dayHabits = habits.filter { habit ->
        habit.isScheduledOn(selectedDate)
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
        Text(localizedText(language, "تقویم Spark", "Spark Calendar", "تقويم Spark"), style = MaterialTheme.typography.headlineSmall)
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
            }) { Icon(Icons.Default.ChevronRight, contentDescription = localizedText(language, "بازه قبل", "Previous period", "الفترة السابقة")) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (mode == CalendarMode.MONTH) monthLabel else dateLabel(selectedDate, "d MMMM yyyy"), style = MaterialTheme.typography.titleMedium)
                Text(if (selectedDate == today) localizedText(language, "امروز", "Today", "اليوم") else selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = {
                onDateChange(when (mode) {
                    CalendarMode.DAY -> selectedDate.plusDays(1)
                    CalendarMode.WEEK -> selectedDate.plusWeeks(1)
                    CalendarMode.MONTH -> selectedDate.plusMonths(1)
                })
            }) { Icon(Icons.Default.ChevronLeft, contentDescription = localizedText(language, "بازه بعد", "Next period", "الفترة التالية")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(localizedText(language, "روز", "Day", "يوم"), localizedText(language, "هفته", "Week", "أسبوع"), localizedText(language, "ماه", "Month", "شهر")).forEachIndexed { index, label ->
                FilterChip(selected = modeIndex == index, onClick = { modeIndex = index }, label = { Text(label) })
            }
        }
        if (mode != CalendarMode.DAY) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (when (language) { "en" -> listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"); "ar" -> listOf("سب", "أح", "اث", "ثل", "أر", "خم", "جم"); else -> listOf("ش", "ی", "د", "س", "چ", "پ", "ج") }).forEach {
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
                                habit.isScheduledOn(date)
                            }
                            val taskCount = tasks.count { it.dueDate == date.toString() }
                            val isSelected = date == selectedDate
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .clickable { onDateChange(date) }.padding(vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(persianDateLabel(date, "d"), style = MaterialTheme.typography.bodyMedium)
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
        Text(localizedText(language, "برنامه ", "Schedule: ", "الجدول: ") + dateLabel(selectedDate, "d MMMM yyyy"), style = MaterialTheme.typography.titleMedium)
        if (dayHabits.isEmpty() && dayTasks.isEmpty()) {
            Text(localizedText(language, "برای این روز برنامه‌ای ثبت نشده است.", "Nothing is scheduled for this day.", "لا يوجد شيء مجدول لهذا اليوم."), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayHabits.size + dayTasks.size) { index ->
                    if (index < dayHabits.size) {
                        val habit = dayHabits[index]
                        val completed = habit.id in doneIds
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable(enabled = !selectedDate.isAfter(today)) { onToggleHabit(habit, !completed) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (completed) Icons.Default.CheckCircle else Icons.Default.Circle, null, tint = MaterialTheme.colorScheme.primary)
                            Text(habit.title, Modifier.weight(1f).padding(start = 10.dp))
                            Text(localizedText(language, "عادت", "Habit", "عادة"), style = MaterialTheme.typography.labelSmall)
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
                            Text(localizedText(language, "کار", "Task", "مهمة"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}


private fun persianDateLabel(date: LocalDate, pattern: String): String {
    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val persianLocale = ULocale("fa_IR@calendar=persian")
    val calendar = Calendar.getInstance(TimeZone.getDefault(), persianLocale)
    calendar.timeInMillis = millis
    val formatter = IcuSimpleDateFormat(pattern, persianLocale)
    formatter.calendar = calendar
    return formatter.format(Date(millis))
}
