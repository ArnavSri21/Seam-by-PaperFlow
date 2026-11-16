package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.VoiceSpeechController
import com.example.data.calendar.GoogleCalendarSyncHelper
import com.example.data.local.VaultEntryEntity
import com.example.ui.components.SeamGlassBackdrop
import com.example.ui.components.SeamGlassBadge
import com.example.ui.components.SeamTopHeader
import com.example.ui.components.seamGlassSurface
import com.example.ui.components.seamPressableGlassCard
import com.example.ui.theme.SeamErrorCoral
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamNearBlack
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamSuccessSage
import com.example.ui.theme.SeamWarmIvory
import com.example.ui.viewmodel.VoiceCaptureUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TaskFilterMode(val label: String) {
    PENDING("Upcoming"),
    SYNCED("Calendar"),
    DONE("Completed"),
    ALL("All")
}

@Composable
fun TasksAndCalendarScreen(
    taskEntries: List<VaultEntryEntity>,
    voiceCaptureState: VoiceCaptureUiState,
    reducedMotion: Boolean = false,
    onRequestVoiceCapture: () -> Unit,
    onCreateManualTask: (title: String, isoDate: String, time24h: String, notes: String, priorityHigh: Boolean, syncToCalendar: Boolean) -> Unit,
    onToggleTaskCompleted: (VaultEntryEntity) -> Unit,
    onSyncTaskToGoogleCalendar: (VaultEntryEntity) -> Unit,
    onDeleteTask: (VaultEntryEntity) -> Unit,
    onOpenAssistantLive: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var filterMode by remember { mutableStateOf(TaskFilterMode.PENDING) }
    var showManualDialog by remember { mutableStateOf(false) }

    val sortedTasks = remember(taskEntries, filterMode) {
        val base = when (filterMode) {
            TaskFilterMode.PENDING -> taskEntries.filter { !it.isTaskCompleted }
            TaskFilterMode.SYNCED -> taskEntries.filter { it.isSyncedToGoogleCalendar() || it.taskDueAtMillis() != null }
            TaskFilterMode.DONE -> taskEntries.filter { it.isTaskCompleted }
            TaskFilterMode.ALL -> taskEntries
        }
        base.sortedWith(
            compareBy<VaultEntryEntity> { it.isTaskCompleted }
                .thenBy { it.taskDueAtMillis() ?: Long.MAX_VALUE }
                .thenByDescending { it.updatedAtMillis }
        )
    }

    val pendingCount = remember(taskEntries) { taskEntries.count { !it.isTaskCompleted } }
    val syncedCount = remember(taskEntries) { taskEntries.count { it.isSyncedToGoogleCalendar() } }

    if (showManualDialog) {
        ManualTaskCreateDialog(
            onDismiss = { showManualDialog = false },
            onSave = { title, isoDate, time24h, notes, priority, syncCal ->
                onCreateManualTask(title, isoDate, time24h, notes, priority, syncCal)
                showManualDialog = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SeamGlassBackdrop(reducedMotion = reducedMotion)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("tasks_calendar_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SeamTopHeader(
                    statusLabel = if (syncedCount > 0) "$syncedCount in Calendar" else "To-Do & Schedule",
                    isStatusActive = voiceCaptureState.isProcessing,
                    reducedMotion = reducedMotion,
                    onSettingsClick = onOpenSettings
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Tasks & Schedule",
                        style = MaterialTheme.typography.displayLarge,
                        color = SeamWarmIvory
                    )
                    Text(
                        text = if (pendingCount > 0) {
                            "$pendingCount active · Speak naturally to schedule & sync to Google Calendar"
                        } else {
                            "Speak naturally like “Meeting on 19 this month” to schedule instantly"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SeamMutedGrey
                    )
                }
            }

            // Primary 1-Tap Voice Upload Hero Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seamPressableGlassCard(
                            reducedMotion = reducedMotion,
                            shape = RoundedCornerShape(24.dp),
                            elevated = true,
                            accentBorder = SeamLavenderAccent.copy(alpha = 0.55f),
                            onClick = onRequestVoiceCapture
                        )
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                        .testTag("tasks_voice_upload_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF5D5388), Color(0xFF393457))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (voiceCaptureState.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = SeamWarmIvory,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Speak task or schedule",
                                tint = SeamWarmIvory,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (voiceCaptureState.isProcessing) "Gemini is organizing…" else "Speak to add & schedule",
                            style = MaterialTheme.typography.titleMedium,
                            color = SeamWarmIvory,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Try: “I have a meeting on 19 this month”",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamSilver
                        )
                    }

                    SeamGlassBadge(
                        text = "Live",
                        tint = SeamWarmIvory,
                        borderTint = SeamLavenderAccent.copy(alpha = 0.55f),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(onClick = onOpenAssistantLive)
                            .testTag("tasks_open_live_badge")
                    )
                }
            }

            // Filter Pills & Manual Add
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TaskFilterMode.entries.forEach { mode ->
                        FilterChip(
                            selected = filterMode == mode,
                            onClick = { filterMode = mode },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("task_filter_${mode.name.lowercase()}")
                        )
                    }

                    OutlinedButton(
                        onClick = { showManualDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("task_manual_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add manual task",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }

            if (sortedTasks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(shape = RoundedCornerShape(22.dp), elevated = true)
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = SeamLavenderAccent,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No ${filterMode.label.lowercase()} tasks yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "Tap the Voice button to dictate meetings, reminders, or notes. Gemini organizes them and adds events to Google Calendar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamMutedGrey
                        )
                    }
                }
            } else {
                items(sortedTasks, key = { it.id }) { task ->
                    TaskScheduleGlassCard(
                        task = task,
                        reducedMotion = reducedMotion,
                        onToggleComplete = { onToggleTaskCompleted(task) },
                        onCalendarClick = {
                            val eventId = task.taskCalendarEventId()
                            if (eventId != null && task.isSyncedToGoogleCalendar()) {
                                runCatching {
                                    context.startActivity(GoogleCalendarSyncHelper.buildOpenCalendarEventIntent(eventId))
                                }
                            } else {
                                onSyncTaskToGoogleCalendar(task)
                            }
                        },
                        onExportCalendarIntent = {
                            val start = task.taskDueAtMillis() ?: (System.currentTimeMillis() + 3_600_000L)
                            runCatching {
                                context.startActivity(
                                    GoogleCalendarSyncHelper.buildCreateCalendarEventIntent(
                                        title = task.title,
                                        description = task.primaryContent,
                                        startMillis = start,
                                        durationMinutes = task.taskDurationMinutes(),
                                        location = task.websiteOrLocation
                                    )
                                )
                            }
                        },
                        onDelete = { onDeleteTask(task) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun TaskScheduleGlassCard(
    task: VaultEntryEntity,
    reducedMotion: Boolean,
    onToggleComplete: () -> Unit,
    onCalendarClick: () -> Unit,
    onExportCalendarIntent: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = task.isTaskCompleted
    val isSynced = task.isSyncedToGoogleCalendar()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .seamPressableGlassCard(
                reducedMotion = reducedMotion,
                shape = RoundedCornerShape(22.dp),
                elevated = !isDone,
                accentBorder = if (isSynced) SeamSuccessSage.copy(alpha = 0.45f) else null,
                onClick = onToggleComplete
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag("task_card_${task.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(
            onClick = onToggleComplete,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("task_toggle_${task.id}")
        ) {
            Icon(
                imageVector = if (isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isDone) "Mark task pending" else "Mark task done",
                tint = if (isDone) SeamSuccessSage else SeamLavenderAccent,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isDone) SeamMutedGrey else SeamWarmIvory,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (task.isFavorite) {
                    SeamGlassBadge(text = "Priority", tint = SeamErrorCoral, borderTint = SeamErrorCoral.copy(alpha = 0.4f))
                }
            }

            if (task.websiteOrLocation.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = SeamLavenderAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = task.websiteOrLocation,
                        style = MaterialTheme.typography.bodySmall,
                        color = SeamSilver,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (task.primaryContent.isNotBlank() && !task.primaryContent.equals(task.title, ignoreCase = true)) {
                Text(
                    text = task.primaryContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = SeamMutedGrey,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeamGlassBadge(
                    text = if (isSynced) "Google Calendar ✓" else "Add to Calendar",
                    tint = if (isSynced) SeamSuccessSage else SeamLavenderAccent,
                    borderTint = if (isSynced) SeamSuccessSage.copy(alpha = 0.45f) else SeamLavenderAccent.copy(alpha = 0.35f),
                    modifier = Modifier
                        .clickable {
                            if (isSynced) onCalendarClick() else onSyncOrIntent(onCalendarClick, onExportCalendarIntent)
                        }
                        .testTag("task_calendar_badge_${task.id}")
                )
                if (task.category.isNotBlank()) {
                    SeamGlassBadge(text = task.category, tint = SeamMutedGrey)
                }
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("task_delete_${task.id}")
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Delete task",
                tint = SeamMutedGrey,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private inline fun onSyncOrIntent(primary: () -> Unit, fallback: () -> Unit) {
    primary()
}

/**
 * Clean Minimalist Frosted-Glass Voice Upload Modal:
 * Requests RECORD_AUDIO (+ READ_CALENDAR / WRITE_CALENDAR) gracefully, starts Android
 * speech recognition immediately, displays live voice transcript + animated orb, and lets users
 * speak or type natural language like "I have a meeting on 19 this month".
 */
@Composable
fun VoiceTaskUploadModal(
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onSubmitTranscript: (String) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var draftText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("Tap the mic to speak, or type below…") }
    var audioLevel by remember { mutableFloatStateOf(0f) }

    var hasAudioPerm by remember {
        mutableStateOf(GoogleCalendarSyncHelper.hasAudioPermission(appContext))
    }
    var hasCalPerm by remember {
        mutableStateOf(GoogleCalendarSyncHelper.hasCalendarPermissions(appContext))
    }

    val controller = remember(appContext) {
        VoiceSpeechController(
            context = appContext,
            onPartialText = { partial ->
                draftText = partial
                voiceStatus = "Listening…"
            },
            onFinalText = { finalText ->
                draftText = finalText
                isListening = false
                voiceStatus = "Captured! Adding with Gemini…"
                onSubmitTranscript(finalText)
            },
            onRmsChanged = { rms -> audioLevel = rms },
            onErrorMessage = { err ->
                isListening = false
                voiceStatus = err
            }
        )
    }

    val canUseSystemVoicePopup = remember(controller) { controller.canLaunchSystemSpeechActivity() }

    val systemVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                draftText = spoken
                voiceStatus = "Captured! Adding with Gemini…"
                onSubmitTranscript(spoken)
            } else {
                voiceStatus = "No speech captured — try again or type below."
            }
        }
    }

    fun startSpeechOrFallback(launchPopupIfInlineMissing: Boolean) {
        if (controller.isAvailable()) {
            isListening = true
            voiceStatus = "Listening… speak naturally"
            controller.startListening()
        } else if (launchPopupIfInlineMissing) {
            val popupIntent = controller.buildSystemVoiceIntent()
            if (popupIntent != null) {
                runCatching {
                    systemVoiceLauncher.launch(popupIntent)
                }.onFailure {
                    isListening = false
                    voiceStatus = "Voice input unavailable here — type your task or pick a sample below."
                }
            } else {
                isListening = false
                voiceStatus = "Speech service unavailable on this device — type or tap a sample below."
            }
        } else {
            isListening = false
            voiceStatus = if (canUseSystemVoicePopup) {
                "Tap the mic orb for System Voice, or type below."
            } else {
                "Speech service unavailable on this device — type or tap a sample below."
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAudioPerm = results[Manifest.permission.RECORD_AUDIO] == true ||
            GoogleCalendarSyncHelper.hasAudioPermission(appContext)
        hasCalPerm = (results[Manifest.permission.WRITE_CALENDAR] == true &&
            results[Manifest.permission.READ_CALENDAR] == true) ||
            GoogleCalendarSyncHelper.hasCalendarPermissions(appContext)
        if (hasAudioPerm) {
            startSpeechOrFallback(launchPopupIfInlineMissing = false)
        } else {
            isListening = false
            voiceStatus = "Microphone permission declined — you can type your task below."
        }
    }

    LaunchedEffect(Unit) {
        // Allow AlertDialog window & focus to settle cleanly before requesting permissions or binding speech service
        delay(160L)
        val needed = buildList {
            if (!GoogleCalendarSyncHelper.hasAudioPermission(appContext)) add(Manifest.permission.RECORD_AUDIO)
            if (!GoogleCalendarSyncHelper.hasCalendarPermissions(appContext)) {
                add(Manifest.permission.READ_CALENDAR)
                add(Manifest.permission.WRITE_CALENDAR)
            }
        }
        if (needed.isNotEmpty()) {
            runCatching {
                permLauncher.launch(needed.toTypedArray())
            }.onFailure {
                voiceStatus = "Tap the mic orb to grant microphone access or type below."
            }
        } else {
            startSpeechOrFallback(launchPopupIfInlineMissing = false)
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    AlertDialog(
        onDismissRequest = {
            controller.stopListening()
            onDismiss()
        },
        containerColor = Color(0xF5141520),
        titleContentColor = SeamWarmIvory,
        textContentColor = SeamSilver,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Voice Organizer",
                    style = MaterialTheme.typography.headlineSmall
                )
                SeamGlassBadge(
                    text = if (hasCalPerm) "Google Cal On" else "To-Do + Export",
                    tint = if (hasCalPerm) SeamSuccessSage else SeamLavenderAccent
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .scale(if (isListening) (pulse + audioLevel * 0.14f) else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (isListening) {
                                    listOf(Color(0xAA8C7FD6), Color(0x66403864))
                                } else {
                                    listOf(Color(0x7736344A), Color(0x551B1B26))
                                }
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isListening) SeamLavenderAccent else Color.White.copy(alpha = 0.30f),
                            shape = CircleShape
                        )
                        .clickable {
                            if (isListening) {
                                controller.stopListening()
                                isListening = false
                                voiceStatus = "Paused listening."
                            } else if (GoogleCalendarSyncHelper.hasAudioPermission(appContext) || hasAudioPerm) {
                                startSpeechOrFallback(launchPopupIfInlineMissing = true)
                            } else {
                                runCatching {
                                    permLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.RECORD_AUDIO,
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                }.onFailure {
                                    voiceStatus = "Could not open permission prompt — type your task below."
                                }
                            }
                        }
                        .testTag("voice_modal_mic_orb"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = SeamWarmIvory,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Start listening",
                            tint = SeamWarmIvory,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = voiceStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = SeamSilver
                )

                if (canUseSystemVoicePopup && !controller.isAvailable()) {
                    SeamGlassBadge(
                        text = "Open Google System Voice",
                        tint = SeamLavenderAccent,
                        borderTint = SeamLavenderAccent.copy(alpha = 0.45f),
                        modifier = Modifier
                            .clickable {
                                controller.buildSystemVoiceIntent()?.let { intent ->
                                    runCatching { systemVoiceLauncher.launch(intent) }
                                }
                            }
                            .testTag("voice_modal_system_mic_button")
                    )
                }

                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    label = { Text("Spoken or typed schedule / task") },
                    placeholder = { Text("e.g. I have a meeting on 19 this month") },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_modal_text_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val examples = listOf(
                        "I have a meeting on 19 this month",
                        "Dentist tomorrow at 3 pm",
                        "Finish report Friday morning"
                    )
                    examples.forEach { sample ->
                        SeamGlassBadge(
                            text = sample,
                            tint = SeamSilver,
                            modifier = Modifier.clickable { draftText = sample }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    controller.stopListening()
                    if (draftText.isNotBlank()) {
                        onSubmitTranscript(draftText.trim())
                    }
                },
                enabled = draftText.isNotBlank() && !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SeamWarmIvory,
                    contentColor = SeamNearBlack
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("voice_modal_confirm_button")
            ) {
                Icon(imageVector = Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Organize & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                controller.stopListening()
                onDismiss()
            }) {
                Text("Close", color = SeamMutedGrey)
            }
        }
    )
}

@Composable
private fun ManualTaskCreateDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, isoDate: String, time24h: String, notes: String, priorityHigh: Boolean, syncToCalendar: Boolean) -> Unit
) {
    val defaultDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var title by remember { mutableStateOf("") }
    var isoDate by remember { mutableStateOf(defaultDate) }
    var time24h by remember { mutableStateOf("10:00") }
    var notes by remember { mutableStateOf("") }
    var priorityHigh by remember { mutableStateOf(false) }
    var syncToCalendar by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF5141520),
        titleContentColor = SeamWarmIvory,
        textContentColor = SeamSilver,
        shape = RoundedCornerShape(28.dp),
        title = { Text("New Task or Event", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task or meeting title") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = isoDate,
                        onValueChange = { isoDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = time24h,
                        onValueChange = { time24h = it },
                        label = { Text("Time (HH:mm)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.8f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sync to Google Calendar", style = MaterialTheme.typography.bodyMedium, color = SeamWarmIvory)
                    Switch(checked = syncToCalendar, onCheckedChange = { syncToCalendar = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("High priority", style = MaterialTheme.typography.bodyMedium, color = SeamWarmIvory)
                    Switch(checked = priorityHigh, onCheckedChange = { priorityHigh = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, isoDate, time24h, notes, priorityHigh, syncToCalendar) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SeamWarmIvory, contentColor = SeamNearBlack),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SeamMutedGrey)
            }
        }
    )
}
