package app.tare.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tare.BuildConfig
import app.tare.data.update.UpdateState
import app.tare.ui.Fmt
import app.tare.ui.components.Card
import app.tare.ui.components.TextAction
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.theme.Tare
import app.tare.ui.update.UpdateProgress

/** Updates from GitHub Releases, installed from inside Tare. */
@Composable
fun UpdatesCard() {
    val updater = LocalAppGraph.current.updater
    val state by updater.state.collectAsState()
    val colors = Tare.colors

    Card(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tare ${BuildConfig.VERSION_NAME}", style = Tare.type.bodyStrong, color = colors.text)
            if (!updater.enabled) {
                Text("A development build. Updates go to the release app.", style = Tare.type.caption, color = colors.textMuted)
                return@Column
            }
            when (val s = state) {
                UpdateState.Idle -> Text("Tare checks for new versions every few hours.", style = Tare.type.caption, color = colors.textMuted)
                UpdateState.Checking -> Text("Checking for updates…", style = Tare.type.caption, color = colors.textMuted)
                is UpdateState.UpToDate -> Text("Up to date · checked ${Fmt.backupTime(s.checkedAt)}", style = Tare.type.caption, color = colors.textMuted)
                is UpdateState.Available -> UpdateProgress(s, s.update, onInstall = { updater.install(s.update) })
                is UpdateState.Downloading -> UpdateProgress(s, s.update, onInstall = {})
                is UpdateState.Installing -> UpdateProgress(s, s.update, onInstall = {})
                is UpdateState.Failed -> {
                    val update = s.update
                    if (update != null) {
                        UpdateProgress(s, update, onInstall = { updater.install(update) })
                    } else {
                        Text(s.message, style = Tare.type.caption, color = colors.over)
                    }
                }
            }
            if (state !is UpdateState.Downloading && state !is UpdateState.Installing) {
                TextAction("Check for updates", onClick = { updater.check() })
            }
        }
    }
}
