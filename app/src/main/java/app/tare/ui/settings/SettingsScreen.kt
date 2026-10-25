package app.tare.ui.settings

import app.tare.data.ai.AiResult
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tare.BuildConfig
import app.tare.data.backup.AutoBackup
import app.tare.data.settings.AiModel
import app.tare.data.settings.ThemeMode
import app.tare.data.diagnostics.CrashReports
import app.tare.domain.Targets
import app.tare.ui.copyToClipboard
import app.tare.ui.scan.CameraScannerGuard
import app.tare.ui.Fmt
import app.tare.ui.components.Card
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SecondaryButton
import app.tare.ui.components.SectionHeader
import app.tare.ui.components.Segmented
import app.tare.ui.components.TareTextField
import app.tare.ui.components.TextAction
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.root.LocalNotifier
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen() {
    val graph = LocalAppGraph.current
    val nav = LocalNavigator.current
    val notify = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uri = LocalUriHandler.current

    val targets by graph.settings.targets.collectAsState(initial = Targets())
    val theme by graph.settings.theme.collectAsState(initial = ThemeMode.SYSTEM)
    val model by graph.settings.aiModel.collectAsState(initial = AiModel.OPUS)
    val hasKey by graph.secrets.hasApiKey.collectAsState(initial = false)

    var showTargets by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf("") }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var checkingKey by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { target ->
        if (target != null) {
            scope.launch {
                val message = runCatching {
                    context.contentResolver.openOutputStream(target)!!.use { graph.backup.export(it) }
                }.fold(
                    { "Backup saved · ${it.entries} entries, ${it.meals} meals" },
                    { "Backup failed: ${it.message}" },
                )
                notify.show(message)
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingImport = it }
    val backupFolder by graph.settings.autoBackupFolder.collectAsState(initial = null)
    val lastBackup by graph.settings.lastAutoBackup.collectAsState(initial = null)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            // Keep access across reboots; the backup worker writes here without the UI.
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            scope.launch {
                graph.settings.setAutoBackupFolder(tree.toString())
                AutoBackup.schedule(context)
                AutoBackup.runNow(context)
                notify.show("Daily backup turned on")
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Tare.colors.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() }, background = Color.Transparent)
            Spacer(Modifier.width(8.dp))
            Text("Settings", style = Tare.type.headline.copy(fontSize = Tare.type.headline.fontSize * 0.8f), color = Tare.colors.text)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        ) {
            SectionHeader("Targets")
            Card(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp), onClick = { showTargets = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${Fmt.kcal(targets.kcal)} kcal per day", style = Tare.type.title, color = Tare.colors.text)
                        Text(
                            "Protein ${targets.protein} g · Carbs ${targets.carbs} g · Fat ${targets.fat} g",
                            style = Tare.type.caption,
                            color = Tare.colors.textMuted,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Tare.colors.textFaint)
                }
            }

            SectionHeader("Appearance")
            Segmented(
                options = ThemeMode.entries,
                selected = theme,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.LIGHT -> "Light"
                    }
                },
                onSelect = { scope.launch { graph.settings.setTheme(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Barcode scanner")
            var googleScanner by remember { mutableStateOf(CameraScannerGuard.isBroken(context)) }
            Segmented(
                options = listOf(false, true),
                selected = googleScanner,
                label = { if (it) "Google's scanner" else "Tare's camera" },
                onSelect = {
                    googleScanner = it
                    CameraScannerGuard.useGoogle(context, it)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Google's scanner opens full screen and works on any phone with Google Play. Tare switches to it by itself if its own camera fails.",
                style = Tare.type.caption,
                color = Tare.colors.textMuted,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )

            SectionHeader("Claude")
            Card(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Describe, photo estimates and label reading use the Claude API with your own key. Only what you send to Claude leaves the phone.",
                        style = Tare.type.caption,
                        color = Tare.colors.textMuted,
                    )
                    if (hasKey) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("API key saved", style = Tare.type.bodyStrong, color = Tare.colors.carbs)
                                keyStatus?.let { Text(it, style = Tare.type.caption, color = Tare.colors.textMuted) }
                            }
                            TextAction(if (checkingKey) "Checking…" else "Check", color = Tare.colors.textMuted, onClick = {
                                if (!checkingKey) {
                                    checkingKey = true
                                    scope.launch {
                                        val key = graph.secrets.apiKey()
                                        keyStatus = when (val r = key?.let { graph.ai.checkKey(it) }) {
                                            is AiResult.Success -> "Works with ${model.label}"
                                            is AiResult.Failed -> r.message
                                            else -> null
                                        }
                                        checkingKey = false
                                    }
                                }
                            })
                            TextAction("Remove", onClick = {
                                keyStatus = null
                                scope.launch { graph.secrets.setApiKey(null) }
                            }, color = Tare.colors.over)
                        }
                    } else {
                        KeyStep(1, "Claim your plan's API credits", "Claude Max includes monthly API credits. On claude.ai: Settings → Billing → API credits, and link a Console organization.") {
                            TextAction("Open claude.ai billing", onClick = { uri.openUri("https://claude.ai/settings/billing") })
                        }
                        KeyStep(2, "Create a key", "In the Claude Console: API keys → Create key, then copy it.") {
                            TextAction("Open the Console", onClick = { uri.openUri("https://platform.claude.com/settings/keys") })
                        }
                        KeyStep(3, "Paste it here", null) {}
                        TareTextField(
                            value = keyDraft,
                            onValueChange = {
                                keyDraft = it.trim()
                                keyStatus = null
                            },
                            placeholder = "sk-ant-…",
                            keyboardType = KeyboardType.Password,
                            trailing = {
                                TextAction("Paste", onClick = {
                                    clipboardText(context)?.let {
                                        keyDraft = it.trim()
                                        keyStatus = null
                                    }
                                })
                            },
                        )
                        keyStatus?.let { Text(it, style = Tare.type.caption, color = Tare.colors.over) }
                        PrimaryButton(
                            if (checkingKey) "Checking key…" else "Save key",
                            onClick = {
                                checkingKey = true
                                scope.launch {
                                    // Check before saving, so a mistyped key never lands; an
                                    // unreachable network is no reason to refuse it.
                                    val result = graph.ai.checkKey(keyDraft)
                                    val rejected = result is AiResult.Failed && "rejected" in result.message
                                    if (rejected) {
                                        keyStatus = (result as AiResult.Failed).message
                                    } else {
                                        graph.secrets.setApiKey(keyDraft)
                                        keyDraft = ""
                                        keyStatus = (result as? AiResult.Failed)?.message
                                        notify.show(if (result is AiResult.Success) "Key saved and working" else "Key saved")
                                    }
                                    checkingKey = false
                                }
                            },
                            enabled = keyDraft.startsWith("sk-") && !checkingKey,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AiModel.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(Radii.row)
                                .clickable { scope.launch { graph.settings.setAiModel(option) } }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (option == model) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                null,
                                tint = if (option == model) Tare.colors.kcal else Tare.colors.textFaint,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(option.label, style = Tare.type.bodyStrong, color = Tare.colors.text)
                                Text(option.blurb, style = Tare.type.caption, color = Tare.colors.textMuted)
                            }
                        }
                    }
                }
            }

            SectionHeader("Health Connect")
            HealthConnectCard()

            SectionHeader("Data")
            Card(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Everything lives on this phone. Turn on the daily backup to a folder that syncs (Google Drive, for example) so a lost phone doesn't mean a lost diary.",
                        style = Tare.type.caption,
                        color = Tare.colors.textMuted,
                    )
                    val folder = backupFolder
                    if (folder == null) {
                        PrimaryButton(
                            "Back up daily to a folder",
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Daily backup on", style = Tare.type.bodyStrong, color = Tare.colors.carbs)
                                Text(
                                    folderLabel(folder) + " · " + (lastBackup?.let { "last ${Fmt.backupTime(it)}" } ?: "first backup pending"),
                                    style = Tare.type.caption,
                                    color = Tare.colors.textMuted,
                                )
                            }
                            TextAction("Turn off", color = Tare.colors.textMuted, onClick = {
                                scope.launch {
                                    graph.settings.setAutoBackupFolder(null)
                                    AutoBackup.cancel(context)
                                    runCatching {
                                        context.contentResolver.releasePersistableUriPermission(
                                            Uri.parse(folder),
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                        )
                                    }
                                }
                            })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Export", onClick = { exporter.launch("tare-backup-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f))
                        SecondaryButton("Import", onClick = { importer.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            SectionHeader("Updates")
            UpdatesCard()

            SectionHeader("About")
            Text(
                "Tare ${BuildConfig.VERSION_NAME}\n" +
                    "Product data © Open Food Facts contributors, available under the Open Database License (ODbL).\n" +
                    "Basic foods from USDA FoodData Central (public domain).\n" +
                    "Geist and Instrument Serif fonts under the SIL Open Font License.",
                style = Tare.type.caption,
                color = Tare.colors.textFaint,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            val crashReport = remember { CrashReports.last(context) }
            if (crashReport != null) {
                TextAction("Copy last problem report", color = Tare.colors.textMuted, onClick = {
                    copyToClipboard(context, "Tare problem report", crashReport)
                    notify.show("Report copied")
                })
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTargets) TargetsSheet(onDismiss = { showTargets = false })

    pendingImport?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = Tare.colors.surface,
            title = { Text("Replace your data?", style = Tare.type.title, color = Tare.colors.text) },
            text = {
                Text(
                    "Your diary, meals, recipes and weights will be replaced with the backup's. This can't be undone.",
                    style = Tare.type.body,
                    color = Tare.colors.textMuted,
                )
            },
            confirmButton = {
                TextAction("Replace", color = Tare.colors.over, onClick = {
                    pendingImport = null
                    scope.launch {
                        val message = runCatching {
                            context.contentResolver.openInputStream(source)!!.use { graph.backup.import(it) }
                        }.fold(
                            { "Restored ${it.entries} entries, ${it.meals} meals, ${it.weights} weigh-ins" },
                            { "Import failed: ${it.message}" },
                        )
                        notify.show(message)
                    }
                })
            },
            dismissButton = { TextAction("Cancel", onClick = { pendingImport = null }, color = Tare.colors.textMuted) },
        )
    }
}

/** "Documents/Tare" from a SAF tree Uri like content://…/tree/primary%3ADocuments%2FTare. */
private fun folderLabel(tree: String): String =
    Uri.decode(tree.substringAfterLast("/tree/")).substringAfter(':').ifBlank { "Selected folder" }

/** One numbered step of getting a Claude key. */
@Composable
private fun KeyStep(number: Int, title: String, body: String?, action: @Composable () -> Unit) {
    Row {
        Text("$number", style = Tare.type.bodyStrong, color = Tare.colors.textFaint, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Tare.type.bodyStrong, color = Tare.colors.text)
            body?.let { Text(it, style = Tare.type.caption, color = Tare.colors.textMuted) }
            Box(Modifier.offset(x = (-10).dp)) { action() }
        }
    }
}

private fun clipboardText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
