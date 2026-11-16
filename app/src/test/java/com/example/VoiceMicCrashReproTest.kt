package com.example

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AssistantToolExecutor
import com.example.data.ai.GeminiAssistantRole
import com.example.data.ai.GeminiAssistantService
import com.example.data.ai.GeminiChatModel
import com.example.data.ai.VoiceSpeechController
import com.example.data.local.VaultEntryEntity
import com.example.data.local.VaultItemType
import com.example.data.preferences.UserVaultPreferences
import com.example.ui.screens.AssistantMode
import com.example.ui.screens.GeminiAssistantScreen
import com.example.ui.screens.SanctuaryScreen
import com.example.ui.screens.TasksAndCalendarScreen
import com.example.ui.screens.VoiceTaskUploadModal
import com.example.ui.theme.PaperFlowTheme
import com.example.ui.viewmodel.BackgroundStatusState
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.LiveVoiceUiState
import com.example.ui.viewmodel.VoiceCaptureUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceMicCrashReproTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `clicking mic on sanctuary screen and voice modal does not crash when permissions denied`() {
        var modalOpen = false
        composeRule.setContent {
            PaperFlowTheme {
                Surface {
                    SanctuaryScreen(
                        entries = emptyList(),
                        isolatedVaultCount = 0,
                        preferences = UserVaultPreferences(),
                        backgroundStatus = BackgroundStatusState(),
                        onNavigateToTab = {},
                        onOpenVoiceCapture = { modalOpen = true },
                        onOpenNewEntryDialog = {},
                        onSelectEntry = {},
                        onToggleFavorite = {},
                        onTriggerBackgroundSyncAndAi = {},
                        onLockSanctuary = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_voice_upload_button").performClick()
        composeRule.waitForIdle()
        assertTrue(modalOpen)
    }

    @Test
    fun `opening voice modal with record audio granted does not crash`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR
        )

        composeRule.setContent {
            PaperFlowTheme {
                VoiceTaskUploadModal(
                    isProcessing = false,
                    onDismiss = {},
                    onSubmitTranscript = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("voice_modal_mic_orb").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `opening tasks screen and clicking mic hero card does not crash`() {
        var clicked = false
        composeRule.setContent {
            PaperFlowTheme {
                TasksAndCalendarScreen(
                    taskEntries = listOf(
                        VaultEntryEntity(
                            id = 1L,
                            userId = "u1",
                            itemType = VaultItemType.TASK.name,
                            title = "Meeting",
                            primaryContent = "I have a meeting on 19 this month",
                            websiteOrLocation = "2026-11-19 at 10:00",
                            folderOrAlbum = "Schedule"
                        )
                    ),
                    voiceCaptureState = VoiceCaptureUiState(),
                    onRequestVoiceCapture = { clicked = true },
                    onCreateManualTask = { _, _, _, _, _, _ -> },
                    onToggleTaskCompleted = {},
                    onSyncTaskToGoogleCalendar = {},
                    onDeleteTask = {},
                    onOpenAssistantLive = {},
                    onOpenSettings = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tasks_voice_upload_card").performClick()
        composeRule.waitForIdle()
        assertTrue(clicked)
    }

    @Test
    fun `opening voice modal with record audio denied does not crash`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR
        )

        composeRule.setContent {
            PaperFlowTheme {
                VoiceTaskUploadModal(
                    isProcessing = false,
                    onDismiss = {},
                    onSubmitTranscript = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("voice_modal_mic_orb").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `clicking live orb in assistant screen does not crash`() {
        composeRule.setContent {
            PaperFlowTheme {
                GeminiAssistantScreen(
                    initialMode = AssistantMode.LIVE,
                    chatState = ChatUiState(
                        selectedModel = GeminiChatModel.HYDROGEN,
                        selectedRole = GeminiAssistantRole.ORGANIZER
                    ),
                    liveState = LiveVoiceUiState(),
                    onSelectChatModel = {},
                    onSelectChatRole = {},
                    onSendChatMessage = {},
                    onClearChat = {},
                    onRequestVoiceModalForChat = {},
                    onStartLiveSession = {},
                    onStopLiveSession = {},
                    onSendLiveText = {},
                    onBackToHome = {},
                    onOpenSettings = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_voice_orb_button").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `voice speech controller handles missing permission and unavailable service safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val errors = mutableListOf<String>()
        val controller = VoiceSpeechController(
            context = context,
            onPartialText = {},
            onFinalText = {},
            onRmsChanged = {},
            onErrorMessage = { errors.add(it) }
        )
        controller.startListening()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(errors.isNotEmpty())
        controller.release()
    }

    @Test
    fun `live audio probe fails gracefully instead of throwing when permission denied`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).denyPermissions(android.Manifest.permission.RECORD_AUDIO)
        val service = GeminiAssistantService()
        val dummyExecutor = object : AssistantToolExecutor {
            override suspend fun onCreateTaskOrCalendarEvent(
                title: String,
                dueIsoDate: String,
                dueTime24h: String,
                durationMinutes: Int,
                notes: String,
                location: String,
                category: String,
                priorityHigh: Boolean,
                syncToCalendar: Boolean
            ) = "ok"

            override suspend fun onCreateOrganizedNote(
                title: String,
                content: String,
                folder: String,
                category: String
            ) = "ok"

            override suspend fun onListUpcomingTasks() = "none"
        }

        val result = service.connectAndStartLiveAudio(
            context = context,
            role = GeminiAssistantRole.ORGANIZER,
            toolExecutor = dummyExecutor,
            onTranscriptUpdate = { _, _ -> },
            onLiveToolEvent = {},
            onSessionEnded = {}
        )
        assertTrue(result.isFailure)
    }
}
