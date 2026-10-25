package app.tare.ui.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tare.BuildConfig
import app.tare.data.update.UpdateCheck
import app.tare.data.update.UpdateState
import app.tare.ui.Fmt
import app.tare.ui.components.Card
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.TareTextField
import app.tare.ui.components.TextAction
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.root.LocalNotifier
import app.tare.ui.theme.Tare
import app.tare.ui.update.UpdateProgress
import kotlinx.coroutines.launch

/** Updates from GitHub Releases, installed from inside Tare. */
@Composable
fun UpdatesCard() {
    val graph = LocalAppGraph.current
    val updater = graph.updater
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val notify = LocalNotifier.current
    val colors = Tare.colors

    val hasToken by graph.secrets.hasGithubToken.collectAsState(initial = false)
    val state by updater.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var tokenError by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tare ${BuildConfig.VERSION_NAME}", style = Tare.type.bodyStrong, color = colors.text)
            when {
                !updater.enabled -> Text("A development build. Updates go to the release app.", style = Tare.type.caption, color = colors.textMuted)
                !hasToken -> {
                    Text(
                        "Tare can update itself from its GitHub releases. The repository is private, so it needs a read-only GitHub token once. The one Obtainium uses works too.",
                        style = Tare.type.caption,
                        color = colors.textMuted,
                    )
                    Text(
                        "On GitHub: Settings → Developer settings → Fine-grained tokens → Generate. Repository access: only Borisvdk/tare. Permissions: Contents, read-only.",
                        style = Tare.type.caption,
                        color = colors.textMuted,
                    )
                    TextAction("Open GitHub", onClick = { uri.openUri("https://github.com/settings/personal-access-tokens/new") })
                    TareTextField(
                        value = draft,
                        onValueChange = {
                            draft = it.trim()
                            tokenError = null
                        },
                        placeholder = "github_pat_…",
                        keyboardType = KeyboardType.Password,
                        trailing = {
                            TextAction("Paste", onClick = {
                                clipboard(context)?.let {
                                    draft = it.trim()
                                    tokenError = null
                                }
                            })
                        },
                    )
                    tokenError?.let { Text(it, style = Tare.type.caption, color = colors.over) }
                    PrimaryButton(
                        if (saving) "Checking token…" else "Save token",
                        onClick = {
                            saving = true
                            scope.launch {
                                graph.secrets.setGithubToken(draft)
                                // A token that can't read the releases is no use; don't keep it.
                                when (val result = graph.updates.check()) {
                                    is UpdateCheck.Failed -> {
                                        graph.secrets.setGithubToken(null)
                                        tokenError = result.message
                                    }
                                    else -> {
                                        draft = ""
                                        notify.show("Token saved")
                                        updater.check()
                                    }
                                }
                                saving = false
                            }
                        },
                        enabled = draft.length > 20 && !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    when (val s = state) {
                        UpdateState.Idle -> Unit
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state !is UpdateState.Downloading && state !is UpdateState.Installing) {
                            TextAction("Check for updates", onClick = { updater.check() })
                        }
                        TextAction("Remove token", color = colors.textMuted, onClick = { scope.launch { graph.secrets.setGithubToken(null) } })
                    }
                }
            }
        }
    }
}

private fun clipboard(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
