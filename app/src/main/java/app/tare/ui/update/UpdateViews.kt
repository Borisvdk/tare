package app.tare.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.tare.data.update.AppUpdate
import app.tare.data.update.UpdateState
import app.tare.ui.components.Card
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.TextAction
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import kotlin.math.roundToInt

/** On Today, only while there's an update to install (or one under way). */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val updater = LocalAppGraph.current.updater
    val state by updater.state.collectAsState()
    val update = when (val s = state) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.Installing -> s.update
        is UpdateState.Failed -> s.update
        else -> null
    } ?: return
    Card(modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        UpdateProgress(state, update, onInstall = { updater.install(update) })
    }
}

/** What's new, then the button, the download bar, or what went wrong. */
@Composable
fun UpdateProgress(state: UpdateState, update: AppUpdate, onInstall: () -> Unit) {
    val colors = Tare.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tare ${update.versionName} is ready", style = Tare.type.title, color = colors.text)
        update.changes.take(4).forEach { change ->
            Row {
                Text("·  ", style = Tare.type.caption, color = colors.textFaint)
                Text(change.replaceFirstChar { it.uppercase() }, style = Tare.type.caption, color = colors.textMuted)
            }
        }
        if (update.changes.size > 4) Text("and ${update.changes.size - 4} more", style = Tare.type.caption, color = colors.textFaint)
        when (state) {
            is UpdateState.Downloading -> {
                Text("Downloading · ${(state.progress * 100).roundToInt()}%", style = Tare.type.caption, color = colors.textMuted)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(Radii.pill).background(colors.track)) {
                    Box(Modifier.fillMaxWidth(state.progress).fillMaxHeight().clip(Radii.pill).background(colors.text))
                }
            }
            is UpdateState.Installing -> Text("Installing… Android may ask you to confirm.", style = Tare.type.caption, color = colors.textMuted)
            is UpdateState.Failed -> {
                Text(state.message, style = Tare.type.caption, color = colors.over)
                Row(verticalAlignment = Alignment.CenterVertically) { TextAction("Try again", onClick = onInstall) }
            }
            else -> PrimaryButton("Update", onClick = onInstall, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}
