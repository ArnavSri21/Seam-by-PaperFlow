package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.GeminiAssistantRole
import com.example.data.ai.GeminiChatModel
import com.example.data.calendar.GoogleCalendarSyncHelper
import com.example.ui.components.SeamGlassBackdrop
import com.example.ui.components.SeamGlassBadge
import com.example.ui.components.SeamGlassSearchField
import com.example.ui.components.SeamTopHeader
import com.example.ui.components.seamGlassSurface
import com.example.ui.theme.SeamErrorCoral
import com.example.ui.theme.SeamLavenderAccent
import com.example.ui.theme.SeamMutedGrey
import com.example.ui.theme.SeamSilver
import com.example.ui.theme.SeamSuccessSage
import com.example.ui.theme.SeamWarmIvory
import com.example.ui.viewmodel.ChatUiMessage
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.LiveVoiceUiState

enum class AssistantMode {
    CHAT,
    LIVE
}

@Composable
fun GeminiAssistantScreen(
    initialMode: AssistantMode = AssistantMode.CHAT,
    chatState: ChatUiState,
    liveState: LiveVoiceUiState,
    reducedMotion: Boolean = false,
    onSelectChatModel: (GeminiChatModel) -> Unit,
    onSelectChatRole: (GeminiAssistantRole) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onRequestVoiceModalForChat: () -> Unit,
    onStartLiveSession: () -> Unit,
    onStopLiveSession: () -> Unit,
    onSendLiveText: (String) -> Unit,
    onBackToHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var currentMode by remember(initialMode) { mutableStateOf(initialMode) }

    Box(modifier = Modifier.fillMaxSize()) {
        SeamGlassBackdrop(reducedMotion = reducedMotion)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SeamTopHeader(
                statusLabel = if (currentMode == AssistantMode.LIVE) {
                    if (liveState.isActive) "gemini-3.8-live Active" else "Live Audio"
                } else {
                    chatState.selectedModel.displayName
                },
                isStatusActive = liveState.isActive || chatState.isGenerating,
                reducedMotion = reducedMotion,
                showBackButton = true,
                onBackClick = onBackToHome,
                onSettingsClick = onOpenSettings
            )

            // Minimalist Frosted Glass Segmented Mode Pill: Multi-Turn Chat vs Live Voice (gemini-3.8-live)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .seamGlassSurface(shape = RoundedCornerShape(24.dp), elevated = true)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModePillSegment(
                    label = "AI Chat",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    selected = currentMode == AssistantMode.CHAT,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_mode_chat"),
                    onClick = { currentMode = AssistantMode.CHAT }
                )
                ModePillSegment(
                    label = "Live Voice",
                    icon = Icons.Outlined.GraphicEq,
                    selected = currentMode == AssistantMode.LIVE,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_mode_live"),
                    onClick = { currentMode = AssistantMode.LIVE }
                )
            }

            if (currentMode == AssistantMode.CHAT) {
                MultiTurnGeminiChatPane(
                    chatState = chatState,
                    onSelectModel = onSelectChatModel,
                    onSelectRole = onSelectChatRole,
                    onSendMessage = onSendChatMessage,
                    onClearChat = onClearChat,
                    onMicClick = onRequestVoiceModalForChat,
                    modifier = Modifier.weight(1f)
                )
            } else {
                GeminiLiveVoicePane(
                    liveState = liveState,
                    selectedRole = chatState.selectedRole,
                    reducedMotion = reducedMotion,
                    onSelectRole = onSelectChatRole,
                    onStartLive = onStartLiveSession,
                    onStopLive = onStopLiveSession,
                    onSendLiveText = onSendLiveText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ModePillSegment(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(Color(0x669C98B8), Color(0x44595276)))
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.36f), RoundedCornerShape(20.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) SeamWarmIvory else SeamMutedGrey,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) SeamWarmIvory else SeamMutedGrey
        )
    }
}

@Composable
private fun MultiTurnGeminiChatPane(
    chatState: ChatUiState,
    onSelectModel: (GeminiChatModel) -> Unit,
    onSelectRole: (GeminiAssistantRole) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Model selector chips across all 4 Gemini models
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GeminiChatModel.entries.forEach { model ->
                FilterChip(
                    selected = chatState.selectedModel == model,
                    onClick = { onSelectModel(model) },
                    label = { Text(model.displayName, fontSize = 12.sp) },
                    modifier = Modifier.testTag("chat_model_${model.name.lowercase()}")
                )
            }
            if (chatState.messages.isNotEmpty()) {
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = "Clear conversation",
                        tint = SeamMutedGrey
                    )
                }
            }
        }

        // System instruction Role selector pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GeminiAssistantRole.entries.forEach { role ->
                val active = chatState.selectedRole == role
                SeamGlassBadge(
                    text = "Role: ${role.label}",
                    tint = if (active) SeamWarmIvory else SeamMutedGrey,
                    borderTint = if (active) SeamLavenderAccent else Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .clickable { onSelectRole(role) }
                        .testTag("chat_role_${role.name.lowercase()}")
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_messages_list"),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (chatState.messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .seamGlassSurface(shape = RoundedCornerShape(22.dp), elevated = true)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${chatState.selectedRole.label} · ${chatState.selectedModel.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = SeamWarmIvory
                        )
                        Text(
                            text = "${chatState.selectedModel.capabilitySummary}. Ask questions or speak naturally to add tasks directly to your To-Do list and Google Calendar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamMutedGrey
                        )
                        val suggestions = listOf(
                            "I have a meeting on 19 this month at 2pm",
                            "Plan my deep work priorities for this week",
                            "What is on my upcoming schedule?"
                        )
                        suggestions.forEach { suggestion ->
                            SeamGlassBadge(
                                text = suggestion,
                                tint = SeamLavenderAccent,
                                modifier = Modifier.clickable { onSendMessage(suggestion) }
                            )
                        }
                    }
                }
            } else {
                items(chatState.messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
            }

            if (chatState.isGenerating) {
                item {
                    Row(
                        modifier = Modifier
                            .seamGlassSurface(shape = RoundedCornerShape(18.dp), elevated = false)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = SeamLavenderAccent,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "${chatState.selectedModel.displayName} thinking…",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeamSilver
                        )
                    }
                }
            }
        }

        // Composer Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(48.dp)
                    .seamGlassSurface(shape = CircleShape, elevated = true)
                    .testTag("chat_voice_shortcut_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice task capture",
                    tint = SeamLavenderAccent
                )
            }

            SeamGlassSearchField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = "Ask ${chatState.selectedModel.displayName}…",
                leadingIcon = Icons.Outlined.AutoAwesome,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val txt = inputText
                            inputText = ""
                            onSendMessage(txt)
                        }
                    }
                ),
                trailingContent = {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val txt = inputText
                                inputText = ""
                                onSendMessage(txt)
                            }
                        },
                        enabled = inputText.isNotBlank() && !chatState.isGenerating,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (inputText.isNotBlank()) SeamWarmIvory else SeamMutedGrey
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field")
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .seamGlassSurface(
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (message.isUser) 20.dp else 6.dp,
                        bottomEnd = if (message.isUser) 6.dp else 20.dp
                    ),
                    elevated = message.isUser,
                    accentBorder = if (message.isUser) SeamLavenderAccent.copy(alpha = 0.45f) else null
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!message.isUser && message.modelLabel.isNotBlank()) {
                Text(
                    text = message.modelLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = SeamLavenderAccent
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = SeamWarmIvory
            )
        }
    }
}

@Composable
private fun GeminiLiveVoicePane(
    liveState: LiveVoiceUiState,
    selectedRole: GeminiAssistantRole,
    reducedMotion: Boolean,
    onSelectRole: (GeminiAssistantRole) -> Unit,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit,
    onSendLiveText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var liveNudgeText by remember { mutableStateOf("") }
    var permissionDeniedHint by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            GoogleCalendarSyncHelper.hasAudioPermission(appContext)
        if (audioOk) {
            permissionDeniedHint = null
            onStartLive()
        } else {
            permissionDeniedHint = "Microphone permission is required for Live Voice. Enable it in Settings or use AI Chat."
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "live_api_orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_orb_pulse"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Role pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GeminiAssistantRole.entries.forEach { role ->
                val active = selectedRole == role
                SeamGlassBadge(
                    text = role.label,
                    tint = if (active) SeamWarmIvory else SeamMutedGrey,
                    borderTint = if (active) SeamLavenderAccent else Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.clickable(enabled = !liveState.isActive) { onSelectRole(role) }
                )
            }
        }

        // Animated Live API Voice Orb (`gemini-3.8-live`)
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(136.dp)
                .scale(if (liveState.isActive && !reducedMotion) pulse else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = when {
                            liveState.isActive -> listOf(Color(0xCCA696EB), Color(0x66403766), Color(0x4414141F))
                            liveState.isConnecting -> listOf(Color(0x887B729E), Color(0x441A1A28))
                            else -> listOf(Color(0x663A384F), Color(0x44151520))
                        }
                    )
                )
                .border(
                    width = 1.8.dp,
                    color = if (liveState.isActive) SeamWarmIvory.copy(alpha = 0.72f) else SeamLavenderAccent.copy(alpha = 0.45f),
                    shape = CircleShape
                )
                .clickable {
                    when {
                        liveState.isActive -> onStopLive()
                        liveState.isConnecting -> Unit
                        GoogleCalendarSyncHelper.hasAudioPermission(appContext) -> {
                            permissionDeniedHint = null
                            onStartLive()
                        }
                        else -> {
                            runCatching {
                                permLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            }.onFailure {
                                permissionDeniedHint = "Unable to request microphone permission — check Android App Settings."
                            }
                        }
                    }
                }
                .testTag("live_voice_orb_button"),
            contentAlignment = Alignment.Center
        ) {
            when {
                liveState.isConnecting -> {
                    CircularProgressIndicator(color = SeamWarmIvory, strokeWidth = 2.5.dp)
                }
                liveState.isActive -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "End live voice session",
                            tint = SeamWarmIvory,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "End Live",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeamWarmIvory
                        )
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Start gemini-3.8-live voice conversation",
                            tint = SeamWarmIvory,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start Live",
                            style = MaterialTheme.typography.labelMedium,
                            color = SeamWarmIvory,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Text(
            text = permissionDeniedHint ?: liveState.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (permissionDeniedHint != null || liveState.errorMessage != null) SeamErrorCoral else SeamSilver
        )

        if (liveState.liveActionLogs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                liveState.liveActionLogs.forEach { logText ->
                    SeamGlassBadge(
                        text = logText,
                        tint = SeamSuccessSage,
                        borderTint = SeamSuccessSage.copy(alpha = 0.45f)
                    )
                }
            }
        }

        // Live real-time audio transcription stream
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .seamGlassSurface(shape = RoundedCornerShape(22.dp), elevated = false)
                .padding(14.dp)
                .testTag("live_transcript_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (liveState.transcripts.isEmpty()) {
                item {
                    Text(
                        text = "Live transcripts & real-time calendar/task updates from gemini-3.8-live appear here as you speak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SeamMutedGrey
                    )
                }
            } else {
                items(liveState.transcripts, key = { it.id }) { line ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (line.isUser) "You" else "Gemini Live",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (line.isUser) SeamLavenderAccent else SeamSuccessSage
                        )
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SeamWarmIvory
                        )
                    }
                }
            }
        }

        if (liveState.isActive) {
            SeamGlassSearchField(
                value = liveNudgeText,
                onValueChange = { liveNudgeText = it },
                placeholder = "Send text into Live session…",
                leadingIcon = Icons.Outlined.AutoAwesome,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (liveNudgeText.isNotBlank()) {
                            onSendLiveText(liveNudgeText)
                            liveNudgeText = ""
                        }
                    }
                ),
                trailingContent = {
                    IconButton(
                        onClick = {
                            if (liveNudgeText.isNotBlank()) {
                                onSendLiveText(liveNudgeText)
                                liveNudgeText = ""
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send text to Live session",
                            tint = SeamWarmIvory
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }
    }
}
