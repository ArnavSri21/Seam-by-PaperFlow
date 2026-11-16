package com.example.data.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

class VoiceSpeechController(
    context: Context,
    private val onPartialText: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onRmsChanged: (Float) -> Unit,
    private val onErrorMessage: (String) -> Unit
) {
    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile
    private var isReleased: Boolean = false
    private var activeSessionId: Long = 0L

    private inline fun postMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAvailable(): Boolean {
        return try {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                false
            } else {
                val services = appContext.packageManager.queryIntentServices(
                    Intent(RecognitionService.SERVICE_INTERFACE),
                    0
                )
                services.isNotEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun buildSystemVoiceIntent(prompt: String = "Speak your task or schedule…"): Intent? {
        return try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            if (intent.resolveActivity(appContext.packageManager) != null) intent else null
        } catch (_: Throwable) {
            null
        }
    }

    fun canLaunchSystemSpeechActivity(): Boolean = buildSystemVoiceIntent() != null

    fun startListening() {
        postMain {
            if (isReleased) return@postMain
            stopRecognizerInternal()

            if (!hasAudioPermission()) {
                onErrorMessage("Microphone permission is needed for live speech — grant permission or type below.")
                return@postMain
            }

            if (!isAvailable()) {
                val hint = if (canLaunchSystemSpeechActivity()) {
                    "Inline speech service unavailable — tap System Voice or type below."
                } else {
                    "Speech service unavailable on this emulator/device — type or tap a sample below."
                }
                onErrorMessage(hint)
                return@postMain
            }

            val sessionToken = ++activeSessionId

            try {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        postMain {
                            if (!isReleased && activeSessionId == sessionToken) {
                                onRmsChanged(normalized)
                            }
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Didn't catch that clearly — tap Mic again or type below."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "No speech detected — tap Mic or type below."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission is required for voice capture."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Speech service network unavailable — you can type your task below."
                            SpeechRecognizer.ERROR_CLIENT ->
                                "Speech service couldn't connect here — tap System Voice or type below."
                            else -> "Voice capture stopped ($error) — tap Mic again or type below."
                        }
                        postMain {
                            if (!isReleased && activeSessionId == sessionToken) {
                                onErrorMessage(message)
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull()?.trim().orEmpty()
                        postMain {
                            if (isReleased || activeSessionId != sessionToken) return@postMain
                            if (best.isNotBlank()) {
                                onFinalText(best)
                            } else {
                                onErrorMessage("Didn't catch any words — try again or type below.")
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            postMain {
                                if (!isReleased && activeSessionId == sessionToken) {
                                    onPartialText(partial)
                                }
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                recognizer.startListening(intent)
            } catch (t: Throwable) {
                stopRecognizerInternal()
                onErrorMessage("Speech recognizer unavailable (${t.localizedMessage ?: "error"}) — type or tap a sample below.")
            }
        }
    }

    fun stopListening() {
        postMain {
            activeSessionId++
            stopRecognizerInternal()
        }
    }

    fun release() {
        isReleased = true
        mainHandler.removeCallbacksAndMessages(null)
        postMain {
            activeSessionId++
            stopRecognizerInternal()
        }
    }

    private fun stopRecognizerInternal() {
        val current = speechRecognizer
        speechRecognizer = null
        if (current != null) {
            runCatching { current.cancel() }
            runCatching { current.stopListening() }
            runCatching { current.destroy() }
        }
    }
}
