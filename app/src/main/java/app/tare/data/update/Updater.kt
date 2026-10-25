package app.tare.data.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    /** No token yet, or not checked. */
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class Installing(val update: AppUpdate) : UpdateState
    data class Failed(val message: String, val update: AppUpdate? = null) : UpdateState
}

/**
 * App-wide update flow: checks GitHub now and then, and on "Update" downloads, verifies and
 * installs the new APK. Screens only read [state] and call [check] / [install].
 */
class Updater(
    private val context: Context,
    private val repository: UpdateRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    /** Debug builds are a different app id; releases can't update them. */
    val enabled: Boolean,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    private val prefs get() = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** On app start/resume: a check every few hours is plenty. */
    fun checkIfDue() {
        if (!enabled || job?.isActive == true) return
        if (_state.value is UpdateState.Available || _state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        if (now() - prefs.getLong(LAST_CHECK, 0) < CHECK_EVERY) return
        check()
    }

    fun check() {
        if (!enabled || job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            val result = repository.check()
            _state.value = when (result) {
                is UpdateCheck.Available -> UpdateState.Available(result.update)
                UpdateCheck.UpToDate -> UpdateState.UpToDate(now())
                UpdateCheck.NoToken -> UpdateState.Idle
                is UpdateCheck.Failed -> UpdateState.Failed(result.message)
            }
            if (result !is UpdateCheck.Failed) prefs.edit().putLong(LAST_CHECK, now()).apply()
        }
    }

    fun install(update: AppUpdate) {
        if (job?.isActive == true) return
        job = scope.launch {
            val apk = File(context.cacheDir, "updates/tare-${update.versionName}.apk")
            try {
                _state.value = UpdateState.Downloading(update, 0f)
                repository.download(update, apk) { _state.value = UpdateState.Downloading(update, it) }
                ApkCheck.problem(context, apk)?.let { problem ->
                    apk.delete()
                    _state.value = UpdateState.Failed(problem, update)
                    return@launch
                }
                _state.value = UpdateState.Installing(update)
                UpdateInstaller.install(context, apk)
            } catch (e: Exception) {
                apk.delete()
                _state.value = UpdateState.Failed(e.message ?: "The update couldn't be downloaded", update)
            }
        }
    }

    /** From the installer: cancelled at the confirmation, blocked, or broken. */
    fun installFailed(message: String) {
        val update = (state.value as? UpdateState.Installing)?.update
        _state.value = UpdateState.Failed(message, update)
    }

    private companion object {
        const val LAST_CHECK = "last_check"
        const val CHECK_EVERY = 3 * 60 * 60 * 1000L
    }
}
