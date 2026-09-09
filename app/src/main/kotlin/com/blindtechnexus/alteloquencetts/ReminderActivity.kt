package com.blindtechnexus.alteloquencetts

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import java.util.Date

/**
 * Activity for managing reminders.
 */
class ReminderActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ReminderScreenContent(
                onBack = { finish() }
            )
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Start the productivity companion service
        ProductivityCompanionService.startService(this)
        // Start the reminder service
        ReminderService.startService(this)
    }
    
    override fun onPause() {
        super.onPause()
        // Optionally stop services if needed
    }
}

/**
 * ViewModel for managing reminders.
 */
class ReminderViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    
    private val repository = ReminderRepository(application)
    val reminders = repository.getAllReminders()
    
    suspend fun addReminder(title: String, message: String, time: Date, repeatType: ReminderRepeatType) {
        repository.addReminder(title, message, time, repeatType)
    }
    
    suspend fun deleteReminder(id: Long) {
        repository.deleteReminder(id)
    }
    
    suspend fun snoozeReminder(id: Long, minutes: Int) {
        repository.snoozeReminder(id, minutes)
    }
}

/**
 * Composable function for the reminder screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreenContent(onBack: () -> Unit) {
    val viewModel: ReminderViewModel = viewModel()
    val reminders by viewModel.reminders.collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedReminderId by remember { mutableStateOf(-1L) }
    
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.reminder_title),
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_reminder)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (reminders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.reminder_no_reminders),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues
                ) {
                    items(reminders) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onDelete = { 
                                selectedReminderId = reminder.id
                                showDeleteDialog = true 
                            },
                            onSnooze = { viewModel.snoozeReminder(reminder.id, 10) }
                        )
                    }
                }
            }
        }
        
        // Add Reminder Dialog
        if (showAddDialog) {
            AddReminderDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, message, date, repeatType ->
                    LaunchedEffect(Unit) {
                        viewModel.addReminder(title, message, date, repeatType)
                        showAddDialog = false
                    }
                }
            )
        }
        
        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(text = stringResource(R.string.reminder_delete)) },
                text = { Text(text = stringResource(R.string.reminder_delete)) },
                confirmButton = {
                    TextButton(onClick = {
                        if (selectedReminderId != -1L) {
                            LaunchedEffect(Unit) {
                                viewModel.deleteReminder(selectedReminderId)
                                showDeleteDialog = false
                            }
                        }
                    }) {
                        Text(text = stringResource(R.string.reminder_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

/**
 * Reminder card composable.
 */
@Composable
fun ReminderCard(
    reminder: ReminderEntity,
    onDelete: () -> Unit,
    onSnooze: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = reminder.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formatDateTime(reminder.reminderTime),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.reminder_repeat) + ": " + reminder.repeatType.name,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Row {
                    IconButton(onClick = onSnooze) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.reminder_snooze)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.reminder_delete)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Add reminder dialog.
 */
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Date, ReminderRepeatType) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance().time) }
    var selectedRepeatType by remember { mutableStateOf(ReminderRepeatType.ONCE) }
    
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.add_reminder)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(R.string.reminder_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(text = stringResource(R.string.reminder_message)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        val year = calendar.get(Calendar.YEAR)
                        val month = calendar.get(Calendar.MONTH)
                        val day = calendar.get(Calendar.DAY_OF_MONTH)
                        
                        val datePicker = DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                calendar.set(y, m, d)
                                selectedDate = calendar.time
                            },
                            year, month, day
                        )
                        datePicker.show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.reminder_time) + ": " + formatDateTime(selectedDate))
                }
                
                Text(
                    text = stringResource(R.string.reminder_repeat) + ":",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RepeatTypeOption(
                        text = stringResource(R.string.reminder_once),
                        selected = selectedRepeatType == ReminderRepeatType.ONCE,
                        onSelected = { selectedRepeatType = ReminderRepeatType.ONCE }
                    )
                    RepeatTypeOption(
                        text = stringResource(R.string.reminder_daily),
                        selected = selectedRepeatType == ReminderRepeatType.DAILY,
                        onSelected = { selectedRepeatType = ReminderRepeatType.DAILY }
                    )
                    RepeatTypeOption(
                        text = stringResource(R.string.reminder_weekly),
                        selected = selectedRepeatType == ReminderRepeatType.WEEKLY,
                        onSelected = { selectedRepeatType = ReminderRepeatType.WEEKLY }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(title, message, selectedDate, selectedRepeatType)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Repeat type option radio button.
 */
@Composable
fun RepeatTypeOption(
    text: String,
    selected: Boolean,
    onSelected: () -> Unit
) {
    TextButton(
        onClick = onSelected,
        modifier = Modifier.height(48.dp)
    ) {
        Text(
            text = text,
            style = if (selected) {
                MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary)
            } else {
                MaterialTheme.typography.bodyMedium
            }
        )
    }
}

/**
 * Format date and time for display.
 */
fun formatDateTime(date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date
    
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) + 1
    val year = calendar.get(Calendar.YEAR)
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    
    return "$year-$month-$day $hour:$minute"
}