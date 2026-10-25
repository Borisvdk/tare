package app.tare.ui.root

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shows snackbars from a scope that outlives individual screens, so "Logged …" still appears
 * after a screen pops itself. A new message replaces the current one instead of queueing,
 * which keeps "Undo" pointing at the latest action.
 */
@Stable
class Notifier(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, actionLabel: String? = null, long: Boolean = false, onAction: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message, actionLabel = actionLabel, duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

val LocalNotifier = staticCompositionLocalOf<Notifier> { error("Notifier not provided") }
