package app.tare.ui.root

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.tare.MainActivity
import app.tare.R
import app.tare.ui.navigation.Route
import java.time.LocalDate

/**
 * Ways into the app other than the launcher icon: long-press shortcuts and the system share
 * sheet. Each becomes a [Route] pushed on top of Home, so back always returns to Today.
 */
object DeepLinks {
    const val ACTION_SCAN = "app.tare.action.SCAN"
    const val ACTION_DESCRIBE = "app.tare.action.DESCRIBE"
    const val ACTION_LOG = "app.tare.action.LOG"

    fun routeFor(intent: Intent?, today: LocalDate = LocalDate.now()): Route? {
        val day = today.toEpochDay()
        return when (intent?.action) {
            ACTION_SCAN -> Route.Scan(day)
            ACTION_DESCRIBE -> Route.Describe(day)
            ACTION_LOG -> Route.LogFood(day, focusSearch = true)
            Intent.ACTION_SEND -> shared(intent, day)
            else -> null
        }
    }

    /** A photo shared from the gallery or camera opens Describe with it; shared text pre-fills it. */
    private fun shared(intent: Intent, day: Long): Route? {
        val type = intent.type.orEmpty()
        return when {
            type.startsWith("image/") ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { Route.Describe(day, photoUri = it.toString()) }
            type.startsWith("text/") ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                    ?.let { Route.Describe(day, prefill = it.trim().take(500)) }
            else -> null
        }
    }

    /** Long-press shortcuts on the launcher icon. Dynamic so they follow the debug/release id. */
    fun publishShortcuts(context: Context) {
        fun shortcut(id: String, label: String, longLabel: String, icon: Int, action: String) =
            ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(label)
                .setLongLabel(longLabel)
                .setIcon(IconCompat.createWithResource(context, icon))
                .setIntent(Intent(context, MainActivity::class.java).setAction(action))
                .build()

        ShortcutManagerCompat.setDynamicShortcuts(
            context,
            listOf(
                shortcut("scan", "Scan", "Scan a barcode", R.drawable.ic_shortcut_scan, ACTION_SCAN),
                shortcut("describe", "Describe", "Describe a meal", R.drawable.ic_shortcut_describe, ACTION_DESCRIBE),
                shortcut("log", "Log food", "Search and log food", R.drawable.ic_shortcut_log, ACTION_LOG),
            ),
        )
    }
}
