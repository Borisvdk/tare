package app.tare.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.tare.data.health.HealthPermissions
import app.tare.data.health.HealthStatus
import app.tare.ui.Fmt
import app.tare.ui.components.Card
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.TextAction
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.root.LocalNotifier
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch

/** Connect Health Connect: weigh-ins from a scale app, calories burned from a watch. Read only. */
@Composable
fun HealthConnectCard() {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notify = LocalNotifier.current
    val colors = Tare.colors

    val status = remember { graph.health.status() }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    val imported by graph.health.importedWeighIns.collectAsState(initial = 0)
    val lastSync by graph.health.lastSync.collectAsState(initial = null)

    fun importNow(afterConnecting: Boolean) {
        scope.launch {
            granted = graph.health.granted()
            val added = graph.health.syncWeights(force = true)
            notify.show(
                when {
                    HealthPermissions.WEIGHT !in granted && HealthPermissions.CALORIES_BURNED !in granted -> "Nothing shared with Tare yet"
                    HealthPermissions.WEIGHT !in granted -> "Connected for calories burned"
                    added > 0 -> "Imported $added weigh-ins"
                    afterConnecting -> "Connected. No weigh-ins to import yet"
                    else -> "Up to date"
                },
            )
        }
    }

    val connect = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { importNow(afterConnecting = true) }
    // Access can change in Health Connect's own settings while Tare is in the background.
    LifecycleResumeEffect(Unit) {
        scope.launch { granted = graph.health.granted() }
        onPauseOrDispose { }
    }

    Card(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Bring in weigh-ins from your scale app (like Renpho) and calories burned from Garmin Connect, through Health Connect. Tare only reads.",
                style = Tare.type.caption,
                color = colors.textMuted,
            )
            when (status) {
                HealthStatus.UNAVAILABLE -> Text("Health Connect isn't available on this phone.", style = Tare.type.body, color = colors.textMuted)
                HealthStatus.NEEDS_UPDATE -> PrimaryButton(
                    "Update Health Connect",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                HealthStatus.AVAILABLE -> {
                    val weight = HealthPermissions.WEIGHT in granted
                    val burned = HealthPermissions.CALORIES_BURNED in granted
                    if (!weight && !burned) {
                        PrimaryButton("Connect Health Connect", onClick = { connect.launch(HealthPermissions.ALL) }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "In Renpho and Garmin Connect, turn on Health Connect too, so they share their data.",
                            style = Tare.type.caption,
                            color = colors.textFaint,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Connected", style = Tare.type.bodyStrong, color = colors.carbs)
                                Text(
                                    listOfNotNull(
                                        listOfNotNull("weight".takeIf { weight }, "calories burned".takeIf { burned }).joinToString(" and "),
                                        "$imported weigh-ins imported".takeIf { weight },
                                        lastSync?.let { "checked ${Fmt.backupTime(it)}" },
                                    ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                                    style = Tare.type.caption,
                                    color = colors.textMuted,
                                )
                            }
                            TextAction("Import now", color = colors.textMuted, onClick = { importNow(afterConnecting = false) })
                        }
                        if (weight && imported == 0) {
                            Text("No weigh-ins yet. In Renpho, turn on Health Connect sync.", style = Tare.type.caption, color = colors.textFaint)
                        }
                        Row {
                            if (!granted.containsAll(HealthPermissions.ALL)) TextAction("Allow the rest", onClick = { connect.launch(HealthPermissions.ALL) })
                            TextAction("Manage access", color = colors.textMuted, onClick = {
                                runCatching { context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context)) }
                            })
                        }
                    }
                }
            }
        }
    }
}
