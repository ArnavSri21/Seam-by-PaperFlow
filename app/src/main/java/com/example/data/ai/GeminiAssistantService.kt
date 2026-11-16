package com.example.data.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "GeminiAssistantService"

enum class GeminiChatModel(
    val modelId: String,
    val displayName: String,
    val capabilitySummary: String
) {
    HYDROGEN(
        modelId = "gemini-4-hydrogen",
        displayName = "Gemini 4 Hydrogen",
        capabilitySummary = "Balanced everyday tasks & scheduling"
    ),
    CARBON(
        modelId = "gemini-4-carbon",
        displayName = "Gemini 4 Carbon",
        capabilitySummary = "Deep reasoning & structured planning"
    ),
    ARGON(
        modelId = "gemini-4-argon",
        displayName = "Gemini 4 Argon",
        capabilitySummary = "Complex interactive multi-step workflows"
    ),
    FLASH_LITE(
        modelId = "gemini-3.1-flash-lite",
        displayName = "3.1 Flash Lite",
        capabilitySummary = "Ultra-fast low-latency responses"
    )
}

enum class GeminiAssistantRole(
    val label: String,
    val subtitle: String,
    val systemInstructionText: String
) {
    ORGANIZER(
        label = "Organizer",
        subtitle = "Tasks, voice schedules & Google Calendar",
        systemInstructionText = "You are Seam's intelligent Task & Schedule Organizer inside a minimalist personal workspace. " +
            "When the user speaks or types about meetings, deadlines, reminders, or errands (for example 'I have a meeting on 19 this month'), " +
            "immediately invoke the add_task_or_calendar_event tool using today's local date to resolve relative dates cleanly. " +
            "When the user wants to store general thoughts or ideas, invoke save_organized_note. Keep replies calm, concise, and clear."
    ),
    DEEP_PLANNER(
        label = "Strategist",
        subtitle = "Deep reasoning & milestone decomposition",
        systemInstructionText = "You are a thoughtful Deep Reasoning Strategist inside Seam. " +
            "Analyze complex goals, weigh trade-offs, break ambitions into concrete time-blocked milestones, " +
            "and use add_task_or_calendar_event to put scheduled steps onto the user's To-Do list and Google Calendar when requested."
    ),
    INTERACTIVE_COACH(
        label = "Workflow",
        subtitle = "Complex interactive checklists & execution",
        systemInstructionText = "You are an Interactive Execution Coach inside Seam. " +
            "Help the user navigate multi-step routines, travel itineraries, or meetings interactively, organizing tasks clearly and updating their schedule on demand."
    ),
    CURATOR(
        label = "Curator",
        subtitle = "Mindful notes & calm organization",
        systemInstructionText = "You are the Mindful Curator of Seam by PaperFlow. " +
            "Summarize scattered ideas into serene, structured notes and capture key action items gracefully using your tools."
    )
}

enum class ExtractedItemKind {
    TASK,
    SCHEDULE,
    NOTE
}

data class ExtractedActionItem(
    val title: String,
    val kind: ExtractedItemKind,
    val details: String,
    val dueIsoDate: String,
    val dueTime24h: String,
    val dueAtMillis: Long?,
    val durationMinutes: Int,
    val location: String,
    val category: String,
    val folderOrBucket: String,
    val priorityHigh: Boolean,
    val syncToCalendar: Boolean
)

data class VoiceParseOutcome(
    val rawInput: String,
    val items: List<ExtractedActionItem>,
    val assistantSummary: String,
    val usedCloudGemini: Boolean
)

data class LiveTranscriptLine(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

interface AssistantToolExecutor {
    suspend fun onCreateTaskOrCalendarEvent(
        title: String,
        dueIsoDate: String,
        dueTime24h: String,
        durationMinutes: Int,
        notes: String,
        location: String,
        category: String,
        priorityHigh: Boolean,
        syncToCalendar: Boolean
    ): String

    suspend fun onCreateOrganizedNote(
        title: String,
        content: String,
        folder: String,
        category: String
    ): String

    suspend fun onListUpcomingTasks(): String
}

class GeminiAssistantService {

    companion object {
        const val LIVE_MODEL_ID = "gemini-3.8-live"
    }

    private val voiceExtractionSchema = Schema.obj(
        properties = mapOf(
            "assistantSummary" to Schema.string(
                description = "Calm 1-sentence confirmation summarizing what was extracted and organized."
            ),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Short, clear task/event/note title (2 to 6 words)"),
                        "kind" to Schema.enumeration(
                            values = listOf("SCHEDULE", "TASK", "NOTE"),
                            description = "SCHEDULE if it has a date/time/meeting, TASK if an actionable to-do, NOTE if general information to remember."
                        ),
                        "details" to Schema.string(description = "Organized description or clean note body"),
                        "dueIsoDate" to Schema.string(description = "Resolved YYYY-MM-DD date relative to today's date, or empty string if undated note"),
                        "dueTime24h" to Schema.string(description = "24-hour HH:mm time (default 10:00 if a date exists without a specific clock time), or empty if undated"),
                        "durationMinutes" to Schema.integer(description = "Duration in minutes (default 60)"),
                        "location" to Schema.string(description = "Meeting place, link, or context if mentioned, else empty"),
                        "category" to Schema.string(description = "1-word tag like Meeting, Work, Personal, Health, Reminder, Idea"),
                        "folderOrBucket" to Schema.string(description = "Organizational folder such as Schedule, To-Do, Work, Personal, Ideas"),
                        "priorityHigh" to Schema.boolean(description = "True if urgent or marked important"),
                        "syncToCalendar" to Schema.boolean(description = "True whenever there is a meeting, appointment, deadline, event, or scheduled date.")
                    )
                ),
                description = "All tasks, meetings, schedules, and organized notes extracted from the user's voice message."
            )
        )
    )

    private fun buildAssistantTools(): Tool {
        val addTaskDecl = FunctionDeclaration(
            name = "add_task_or_calendar_event",
            description = "Adds a task, meeting, or schedule to the user's in-app To-Do list and automatically syncs it to Google Calendar when syncToCalendar is true.",
            parameters = mapOf(
                "title" to Schema.string("Short clear title of the meeting or to-do task, e.g., 'Meeting' or 'Submit Project Report'"),
                "dueIsoDate" to Schema.string("Resolved date in YYYY-MM-DD format relative to current date (e.g. '2026-11-19'). Empty string if undated."),
                "dueTime24h" to Schema.string("Start time in 24h HH:mm format (default '10:00' when a date is mentioned without clock time)."),
                "durationMinutes" to Schema.integer("Duration in minutes (default 60)."),
                "notes" to Schema.string("Detailed notes or context spoken by the user."),
                "location" to Schema.string("Location or meeting room if specified."),
                "category" to Schema.string("Tag such as Meeting, Task, Errand, Focus, Travel."),
                "priorityHigh" to Schema.boolean("True if high priority or urgent."),
                "syncToCalendar" to Schema.boolean("True to create the event on Google Calendar in addition to saving it in the app.")
            ),
            optionalParameters = listOf("dueTime24h", "durationMinutes", "notes", "location", "category", "priorityHigh", "syncToCalendar")
        )

        val saveNoteDecl = FunctionDeclaration(
            name = "save_organized_note",
            description = "Stores general thoughts, factual information, or ideas spoken/typed by the user as an organized note in Seam.",
            parameters = mapOf(
                "title" to Schema.string("Concise title for the note"),
                "content" to Schema.string("Cleaned, well-structured Markdown note text"),
                "folder" to Schema.string("Organizational folder such as Ideas, Reflections, Work, Personal"),
                "category" to Schema.string("1-word category tag")
            ),
            optionalParameters = listOf("folder", "category")
        )

        val listTasksDecl = FunctionDeclaration(
            name = "list_upcoming_tasks",
            description = "Lists the user's current To-Do tasks and upcoming scheduled events stored in the app.",
            parameters = mapOf(
                "filter" to Schema.string("Optional filter: 'all', 'pending', or 'today'")
            ),
            optionalParameters = listOf("filter")
        )

        return Tool.functionDeclarations(listOf(addTaskDecl, saveNoteDecl, listTasksDecl))
    }

    /**
     * Parses natural-language voice or quick text (e.g. "I have a meeting on 19 this month")
     * using Firebase AI Logic (`gemini-4-hydrogen` / `gemini-3.1-flash-lite`) with strict JSON schema
     * and offline date-resolution safety fallback.
     */
    suspend fun parseVoiceInputToActionItems(
        spokenText: String,
        nowMillis: Long = System.currentTimeMillis(),
        preferredModel: GeminiChatModel = GeminiChatModel.HYDROGEN
    ): VoiceParseOutcome = withContext(Dispatchers.IO) {
        val trimmed = spokenText.trim()
        if (trimmed.isBlank()) {
            return@withContext VoiceParseOutcome("", emptyList(), "No speech received.", usedCloudGemini = false)
        }

        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayNameFormatter = SimpleDateFormat("EEEE, MMM d, yyyy (HH:mm)", Locale.US)
        val todayIso = isoFormatter.format(nowCal.time)
        val currentMonth = nowCal.get(Calendar.MONTH) + 1
        val currentYear = nowCal.get(Calendar.YEAR)
        val tzId = TimeZone.getDefault().id

        val prompt = """
            You organize spoken tasks, schedules, and notes for Seam by PaperFlow.
            Current local context:
            - Today's exact date/time: ${dayNameFormatter.format(nowCal.time)}
            - Today ISO: $todayIso (Year: $currentYear, Month: $currentMonth)
            - Device TimeZone: $tzId

            User voice input:
            "$trimmed"

            Instructions:
            1. Extract every distinct task, meeting, event, or piece of information mentioned.
            2. Accurately resolve relative dates relative to $todayIso! For example:
               - "on 19 this month" or "on the 19th" -> resolve to "$currentYear-${currentMonth.toString().padStart(2, '0')}-19"
               - "tomorrow" -> resolve to the next calendar day
               - "this Friday" / "next Monday" -> resolve to the exact YYYY-MM-DD date.
            3. Whenever the user mentions an appointment, meeting, class, call, deadline, or scheduled day/time, set kind="SCHEDULE" or "TASK" and syncToCalendar=true.
            4. If no clock time was spoken for a scheduled day, default dueTime24h="10:00" and durationMinutes=60.
            5. Keep titles short and clean (e.g. "Meeting" or "Team Sync"), and organize details clearly.
        """.trimIndent()

        val candidateModels = listOf(
            preferredModel.modelId,
            GeminiChatModel.FLASH_LITE.modelId,
            "gemini-3.8-flash"
        ).distinct()

        for (modelName in candidateModels) {
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        responseSchema = voiceExtractionSchema
                    }
                )
                val response = withTimeout(30_000L) {
                    model.generateContent(prompt)
                }
                val jsonText = response.text?.trim().orEmpty()
                if (jsonText.isNotBlank()) {
                    val json = JSONObject(jsonText)
                    val summary = json.optString("assistantSummary", "Organized your voice upload.")
                    val itemsJson = json.optJSONArray("items")
                    val parsed = mutableListOf<ExtractedActionItem>()
                    if (itemsJson != null) {
                        for (i in 0 until itemsJson.length()) {
                            val obj = itemsJson.optJSONObject(i) ?: continue
                            val title = obj.optString("title").trim().ifBlank { "Scheduled Task" }
                            val kindStr = obj.optString("kind", "TASK").uppercase(Locale.US)
                            val kind = runCatching { ExtractedItemKind.valueOf(kindStr) }
                                .getOrDefault(ExtractedItemKind.TASK)
                            val isoDate = obj.optString("dueIsoDate").trim()
                            val time24h = obj.optString("dueTime24h").trim()
                            val dueMillis = resolveTimestampMillis(isoDate, time24h, nowMillis)
                            val syncCal = obj.optBoolean("syncToCalendar", kind != ExtractedItemKind.NOTE || dueMillis != null)
                            parsed.add(
                                ExtractedActionItem(
                                    title = title,
                                    kind = kind,
                                    details = obj.optString("details", trimmed).trim(),
                                    dueIsoDate = isoDate,
                                    dueTime24h = time24h,
                                    dueAtMillis = dueMillis,
                                    durationMinutes = obj.optInt("durationMinutes", 60).coerceIn(15, 480),
                                    location = obj.optString("location").trim(),
                                    category = obj.optString("category", if (kind == ExtractedItemKind.SCHEDULE) "Schedule" else "Task").trim(),
                                    folderOrBucket = obj.optString("folderOrBucket", if (kind == ExtractedItemKind.NOTE) "Voice Notes" else "Schedule").trim(),
                                    priorityHigh = obj.optBoolean("priorityHigh", false),
                                    syncToCalendar = syncCal
                                )
                            )
                        }
                    }
                    if (parsed.isNotEmpty()) {
                        return@withContext VoiceParseOutcome(
                            rawInput = trimmed,
                            items = parsed,
                            assistantSummary = summary,
                            usedCloudGemini = true
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Voice parse attempt failed on $modelName: ${e.localizedMessage}")
            }
        }

        // Resilient offline/fallback parser if network is offline so voice capture never drops user intent
        val fallbackItem = buildDeterministicFallbackItem(trimmed, nowMillis)
        VoiceParseOutcome(
            rawInput = trimmed,
            items = listOf(fallbackItem),
            assistantSummary = "Parsed locally and added '${fallbackItem.title}'.",
            usedCloudGemini = false
        )
    }

    /**
     * Multi-turn Gemini Chatbot using Firebase AI Logic (`startChat`) supporting all 4 Gemini models
     * (`gemini-4-carbon`, `gemini-4-argon`, `gemini-4-hydrogen`, `gemini-3.1-flash-lite`),
     * role-based `systemInstruction`, and automatic tool invocation for tasks/calendar/notes.
     */
    fun createChatSession(
        modelChoice: GeminiChatModel,
        role: GeminiAssistantRole,
        existingHistory: List<Content> = emptyList()
    ): Chat {
        val nowCal = Calendar.getInstance()
        val dateStamp = SimpleDateFormat("EEEE, yyyy-MM-dd (HH:mm z)", Locale.US).format(nowCal.time)
        val systemContent = content {
            text("${role.systemInstructionText}\nCurrent local date/time is $dateStamp.")
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelChoice.modelId,
            systemInstruction = systemContent,
            tools = listOf(buildAssistantTools())
        )
        return model.startChat(existingHistory)
    }

    suspend fun sendMultiTurnChatMessage(
        chat: Chat,
        userMessage: String,
        toolExecutor: AssistantToolExecutor
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var response = withTimeout(60_000L) {
                chat.sendMessage(userMessage)
            }

            // Handle any function calls emitted by Gemini cleanly (up to 4 hops)
            var hops = 0
            while (response.functionCalls.isNotEmpty() && hops < 4) {
                hops++
                val functionResponses = mutableListOf<FunctionResponsePart>()
                for (call in response.functionCalls) {
                    val resultJson = executeFunctionCall(call, toolExecutor)
                    functionResponses.add(FunctionResponsePart(call.name, resultJson))
                }
                val functionContent = content("function") {
                    functionResponses.forEach { part(it) }
                }
                response = withTimeout(60_000L) {
                    chat.sendMessage(functionContent)
                }
            }

            val text = response.text?.trim().orEmpty()
                .ifBlank { "Done — I've updated your workspace." }
            Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Multi-turn chat failed", e)
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun probeLiveAudioHardware(context: Context?): String? {
        if (context != null) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return "Microphone permission is required for Live Voice conversation."
            }
        }
        return try {
            val minBuffer = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                return "Audio input buffer unavailable on this device/emulator — use Voice Organizer typing or AI Chat."
            }
            var probeRecord: AudioRecord? = null
            try {
                probeRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(16000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuffer)
                    .build()
                if (probeRecord.state != AudioRecord.STATE_INITIALIZED) {
                    "Microphone audio hardware failed to initialize on this device/emulator — use AI Chat or Voice Organizer typing."
                } else {
                    null
                }
            } finally {
                runCatching { probeRecord?.release() }
            }
        } catch (t: Throwable) {
            "Microphone audio unavailable on this device/emulator (${t.localizedMessage ?: "error"})."
        }
    }

    /**
     * Real-time bidirectional Live API voice conversation powered by `gemini-3.8-live`.
     */
    @OptIn(PublicPreviewAPI::class)
    suspend fun connectAndStartLiveAudio(
        context: Context? = null,
        role: GeminiAssistantRole,
        toolExecutor: AssistantToolExecutor,
        onTranscriptUpdate: (isUser: Boolean, text: String) -> Unit,
        onLiveToolEvent: (String) -> Unit,
        onSessionEnded: (String?) -> Unit
    ): Result<LiveVoiceController> = withContext(Dispatchers.IO) {
        try {
            probeLiveAudioHardware(context)?.let { hwError ->
                return@withContext Result.failure(IllegalStateException(hwError))
            }

            val nowCal = Calendar.getInstance()
            val dateStamp = SimpleDateFormat("EEEE, yyyy-MM-dd (HH:mm z)", Locale.US).format(nowCal.time)
            val liveConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(Voice("AOEDE"))
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            }
            val liveModel = Firebase.ai.liveModel(
                modelName = LIVE_MODEL_ID,
                generationConfig = liveConfig,
                tools = listOf(buildAssistantTools()),
                systemInstruction = content {
                    text(
                        "${role.systemInstructionText}\n" +
                            "Current local date/time is $dateStamp. " +
                            "Respond warmly and concisely in voice. Whenever the user describes a meeting, schedule, or to-do (even naturally like 'I have a meeting on 19 this month'), call add_task_or_calendar_event right away and confirm."
                    )
                }
            )

            val session: LiveSession = withTimeout(25_000L) {
                liveModel.connect()
            }

            val endedOnce = AtomicBoolean(false)
            val notifyEnded: (String?) -> Unit = { msg ->
                if (endedOnce.compareAndSet(false, true)) {
                    onSessionEnded(msg)
                }
            }
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Uncaught coroutine error in LiveSession", throwable)
                notifyEnded(throwable.localizedMessage ?: "Live audio stream ended unexpectedly.")
            }

            val sessionScope = CoroutineScope(SupervisorJob() + exceptionHandler + Dispatchers.IO)
            val controller = LiveVoiceController(session, sessionScope)

            val job = sessionScope.launch {
                try {
                    session.startAudioConversation(
                        functionCallHandler = { call ->
                            val resultObj = runBlocking {
                                val res = runCatching { executeFunctionCall(call, toolExecutor) }
                                    .getOrElse { err ->
                                        buildJsonObject {
                                            put("ok", false)
                                            put("error", err.localizedMessage ?: "Tool error")
                                        }
                                    }
                                val summary = res["status"]?.jsonPrimitive?.contentOrNull
                                    ?: res["message"]?.jsonPrimitive?.contentOrNull
                                    ?: "Executed ${call.name}"
                                onLiveToolEvent(summary)
                                res
                            }
                            FunctionResponsePart(call.name, resultObj)
                        },
                        transcriptHandler = { inputTranscript, outputTranscript ->
                            inputTranscript?.text?.trim()?.takeIf { it.isNotBlank() }?.let { txt ->
                                onTranscriptUpdate(true, txt)
                            }
                            outputTranscript?.text?.trim()?.takeIf { it.isNotBlank() }?.let { txt ->
                                onTranscriptUpdate(false, txt)
                            }
                        }
                    )
                } catch (e: CancellationException) {
                    notifyEnded(null)
                } catch (t: Throwable) {
                    Log.e(TAG, "Live audio conversation ended with error", t)
                    notifyEnded(t.localizedMessage ?: "Live session ended")
                }
            }
            controller.attachJob(job)
            Result.success(controller)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to connect to gemini-3.8-live", t)
            Result.failure(if (t is Exception) t else RuntimeException(t.localizedMessage ?: "Live voice error", t))
        }
    }

    private suspend fun executeFunctionCall(
        call: FunctionCallPart,
        executor: AssistantToolExecutor
    ): JsonObject {
        return when (call.name) {
            "add_task_or_calendar_event" -> {
                val title = call.args.stringArg("title").ifBlank { "Scheduled Task" }
                val dueIsoDate = call.args.stringArg("dueIsoDate")
                val dueTime24h = call.args.stringArg("dueTime24h").ifBlank { if (dueIsoDate.isNotBlank()) "10:00" else "" }
                val duration = call.args.intArg("durationMinutes", 60)
                val notes = call.args.stringArg("notes")
                val location = call.args.stringArg("location")
                val category = call.args.stringArg("category").ifBlank { "Schedule" }
                val priorityHigh = call.args.boolArg("priorityHigh", false)
                val syncCal = call.args.boolArg("syncToCalendar", true)

                val msg = executor.onCreateTaskOrCalendarEvent(
                    title = title,
                    dueIsoDate = dueIsoDate,
                    dueTime24h = dueTime24h,
                    durationMinutes = duration,
                    notes = notes,
                    location = location,
                    category = category,
                    priorityHigh = priorityHigh,
                    syncToCalendar = syncCal
                )
                buildJsonObject {
                    put("ok", true)
                    put("status", msg)
                }
            }

            "save_organized_note" -> {
                val title = call.args.stringArg("title").ifBlank { "Organized Note" }
                val body = call.args.stringArg("content")
                val folder = call.args.stringArg("folder").ifBlank { "Reflections" }
                val category = call.args.stringArg("category").ifBlank { "Mindfulness" }
                val msg = executor.onCreateOrganizedNote(title, body, folder, category)
                buildJsonObject {
                    put("ok", true)
                    put("status", msg)
                }
            }

            "list_upcoming_tasks" -> {
                val tasksSummary = executor.onListUpcomingTasks()
                buildJsonObject {
                    put("ok", true)
                    put("tasks", tasksSummary)
                }
            }

            else -> buildJsonObject {
                put("ok", false)
                put("error", "Unknown function: ${call.name}")
            }
        }
    }

    private fun Map<String, JsonElement>.stringArg(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun Map<String, JsonElement>.intArg(key: String, default: Int): Int =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: default

    private fun Map<String, JsonElement>.boolArg(key: String, default: Boolean): Boolean =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: default

    fun resolveTimestampMillis(
        isoDate: String,
        time24h: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (isoDate.isBlank()) return null
        return try {
            val parts = isoDate.split("-")
            if (parts.size != 3) return null
            val year = parts[0].toInt()
            val monthZeroBased = (parts[1].toInt() - 1).coerceIn(0, 11)
            val day = parts[2].toInt().coerceIn(1, 31)

            val (hour, minute) = if (time24h.contains(":")) {
                val t = time24h.split(":")
                (t[0].toIntOrNull()?.coerceIn(0, 23) ?: 10) to (t[1].toIntOrNull()?.coerceIn(0, 59) ?: 0)
            } else {
                10 to 0
            }

            Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, monthZeroBased)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun buildDeterministicFallbackItem(
        spokenText: String,
        nowMillis: Long
    ): ExtractedActionItem {
        val lower = spokenText.lowercase(Locale.US)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        var resolvedDate = ""
        var resolvedTime = ""
        var syncCal = false

        // Match phrases like "on 19 this month", "on the 19th", "tomorrow", "today"
        val dayOfMonthMatch = Regex("""\b(?:on\s+(?:the\s+)?)?(\d{1,2})(?:st|nd|rd|th)?\s*(?:of\s+)?(?:this\s+month)?\b""")
            .find(lower)
        val dayNum = dayOfMonthMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

        when {
            lower.contains("tomorrow") -> {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                resolvedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                resolvedTime = "10:00"
                syncCal = true
            }
            lower.contains("today") || lower.contains("tonight") -> {
                resolvedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                resolvedTime = if (lower.contains("tonight")) "19:00" else "14:00"
                syncCal = true
            }
            dayNum != null && dayNum in 1..31 -> {
                cal.set(Calendar.DAY_OF_MONTH, dayNum.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                resolvedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                resolvedTime = "10:00"
                syncCal = true
            }
        }

        // Detect time like "4 pm", "10:30 am", "15:00"
        val timeMatch = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""").findAll(lower)
            .firstOrNull { m ->
                val ampm = m.groupValues[3]
                val colon = m.groupValues[2]
                ampm.isNotBlank() || colon.isNotBlank()
            }
        if (timeMatch != null) {
            var hr = timeMatch.groupValues[1].toIntOrNull() ?: 10
            val min = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = timeMatch.groupValues[3]
            if (ampm == "pm" && hr < 12) hr += 12
            if (ampm == "am" && hr == 12) hr = 0
            resolvedTime = "${hr.coerceIn(0, 23).toString().padStart(2, '0')}:${min.coerceIn(0, 59).toString().padStart(2, '0')}"
            if (resolvedDate.isBlank()) {
                resolvedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            }
            syncCal = true
        }

        val isMeetingOrEvent = listOf("meeting", "appointment", "call", "lunch", "dinner", "doctor", "interview", "flight", "schedule")
            .any { lower.contains(it) } || syncCal

        val title = when {
            lower.contains("meeting") -> "Meeting"
            lower.contains("appointment") -> "Appointment"
            lower.contains("call") -> "Call"
            else -> spokenText.trim().split(Regex("\\s+")).take(5).joinToString(" ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        val dueMillis = resolveTimestampMillis(resolvedDate, resolvedTime, nowMillis)
        return ExtractedActionItem(
            title = title,
            kind = if (isMeetingOrEvent) ExtractedItemKind.SCHEDULE else ExtractedItemKind.TASK,
            details = spokenText,
            dueIsoDate = resolvedDate,
            dueTime24h = resolvedTime,
            dueAtMillis = dueMillis,
            durationMinutes = 60,
            location = "",
            category = if (lower.contains("meeting")) "Meeting" else if (isMeetingOrEvent) "Schedule" else "Task",
            folderOrBucket = if (isMeetingOrEvent) "Schedule" else "To-Do",
            priorityHigh = lower.contains("urgent") || lower.contains("important"),
            syncToCalendar = isMeetingOrEvent || dueMillis != null
        )
    }
}

@OptIn(PublicPreviewAPI::class)
class LiveVoiceController(
    private val session: LiveSession,
    private val scope: CoroutineScope
) {
    private var conversationJob: Job? = null

    fun attachJob(job: Job) {
        conversationJob = job
    }

    fun sendTextPrompt(text: String) {
        if (text.isBlank() || session.isClosed()) return
        scope.launch {
            runCatching { session.send(text) }
        }
    }

    fun stop() {
        runCatching { session.stopAudioConversation() }
        conversationJob?.cancel()
        scope.launch {
            runCatching { session.close() }
        }
    }
}
