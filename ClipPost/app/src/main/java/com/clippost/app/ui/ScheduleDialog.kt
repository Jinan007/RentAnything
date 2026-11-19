package com.clippost.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.clippost.app.MIN_SCHEDULE_LEAD_MILLIS
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "Nov 25, 2026, 6:30 PM" in the phone's time zone. */
fun formatTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))

/** Two steps: pick a date, then pick a time. Calls onConfirm with the chosen moment in epoch milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    initialMillis: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val initial = initialMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
        ?: ZonedDateTime.now(zone).plusHours(1)

    var step by remember { mutableIntStateOf(0) }            // 0 = date, 1 = time
    var error by remember { mutableStateOf<String?>(null) }

    // The date picker works in UTC midnight; we combine its date with the chosen local time.
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false
    )

    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { step = 1 }, enabled = dateState.selectedDateMillis != null) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pick a time") },
            text = {
                Column {
                    TimeInput(state = timeState)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = dateState.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    val chosen = date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant().toEpochMilli()
                    if (chosen < System.currentTimeMillis() + MIN_SCHEDULE_LEAD_MILLIS) {
                        error = "Pick a time at least 15 minutes from now."
                    } else {
                        onConfirm(chosen)
                    }
                }) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Back") } }
        )
    }
}
