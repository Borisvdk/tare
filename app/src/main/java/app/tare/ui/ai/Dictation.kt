package app.tare.ui.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Languages offered for dictation; Dutch first, it's what gets spoken at the table. */
enum class DictationLanguage(val tag: String, val label: String) {
    DUTCH("nl-NL", "Nederlands"),
    ENGLISH("en-US", "English"),
}

/**
 * In-place dictation through the system speech recognizer, without its pop-up. It keeps
 * listening through pauses until [stop]: when the recognizer ends a session on silence, a new
 * one starts. Live words are in [partial]; finished phrases go to the callback.
 */
class Dictation internal constructor(private val context: Context, private val onPhrase: (String) -> Unit) {
    var listening by mutableStateOf(false)
        private set

    /** Words recognised so far in the current phrase, shown live. */
    var partial by mutableStateOf("")
        private set

    /** Microphone level, 0..1, for the button's pulse. */
    var level by mutableFloatStateOf(0f)
        private set

    var language by mutableStateOf(DictationLanguage.DUTCH)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private var heardSomething = false
    private var quietRestarts = 0

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (listening) return
        error = null
        listening = true
        quietRestarts = 0
        listen()
    }

    fun stop() {
        if (!listening) return
        listening = false
        // Keep what was said up to the tap.
        commitPartial()
        recognizer?.stopListening()
        release()
    }

    fun switchLanguage() {
        language = if (language == DictationLanguage.DUTCH) DictationLanguage.ENGLISH else DictationLanguage.DUTCH
        if (listening) {
            commitPartial()
            release()
            listen()
        }
    }

    internal fun release() {
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        level = 0f
    }

    private fun listen() {
        heardSomething = false
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Ask for long pauses; recognizers may ignore this, which is what the restart is for.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // One session of up to five minutes that reports each phrase as it's finished. The
            // value names the extra that bounds the session (see RecognizerIntent).
            intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300_000)
        }
        runCatching { r.startListening(intent) }.onFailure { fail("Dictation isn't available on this phone") }
    }

    private fun commitPartial() {
        val words = partial.trim()
        partial = ""
        if (words.isNotEmpty()) onPhrase(words)
    }

    private fun phrase(results: Bundle?) {
        val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        partial = ""
        if (words.isNotEmpty()) {
            heardSomething = true
            onPhrase(words)
        }
    }

    // The session ended on its own (silence, or the recognizer's own limit): go again unless the
    // user stopped. Endless quiet stops after a while so the mic isn't left on by accident.
    private fun sessionEnded() {
        if (!listening) return
        quietRestarts = if (heardSomething) 0 else quietRestarts + 1
        if (quietRestarts > 6) {
            stop()
            return
        }
        // Not from inside the recognizer's own callback, and with a fresh recognizer: some
        // implementations ignore a startListening() made while they're wrapping up.
        release()
        main.postDelayed({ if (listening && recognizer == null) listen() }, 200)
    }

    private fun fail(message: String) {
        error = message
        listening = false
        partial = ""
        release()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
        }

        override fun onSegmentResults(segmentResults: Bundle) = phrase(segmentResults)

        override fun onEndOfSegmentedSession() = sessionEnded()

        override fun onResults(results: Bundle?) {
            phrase(results)
            sessionEnded()
        }

        override fun onError(code: Int) {
            when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> sessionEnded()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Tare needs the microphone to dictate")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    fail("Dictation needs a connection right now")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    fail("${language.label} isn't available for dictation on this phone")
                // A restart raced the previous session; start clean.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> sessionEnded()
                else -> fail("Dictation stopped (error $code)")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

@Composable
fun rememberDictation(onPhrase: (String) -> Unit): Dictation {
    val context = LocalContext.current
    val current by rememberUpdatedState(onPhrase)
    val dictation = remember { Dictation(context) { current(it) } }
    DisposableEffect(dictation) { onDispose { dictation.release() } }
    return dictation
}

/** Adds a dictated phrase to what's typed, with a space between. */
fun appendSpoken(text: String, spoken: String): String = when {
    spoken.isBlank() -> text
    text.isBlank() -> spoken.trim()
    else -> text.trimEnd() + " " + spoken.trim()
}
