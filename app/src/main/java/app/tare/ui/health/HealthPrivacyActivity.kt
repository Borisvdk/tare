package app.tare.ui.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tare.TareApp
import app.tare.data.settings.ThemeMode
import app.tare.ui.components.PrimaryButton
import app.tare.ui.theme.Tare
import app.tare.ui.theme.TareTheme

/**
 * What Health Connect shows when you ask why Tare wants access (its permission screen links
 * here). Health Connect refuses to grant anything to apps that don't have it.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as TareApp).graph
        setContent {
            val theme by graph.settings.theme.collectAsState(initial = ThemeMode.SYSTEM)
            TareTheme(theme) {
                Column(
                    Modifier.fillMaxSize().background(Tare.colors.background).safeDrawingPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Tare and your health data", style = Tare.type.headline, color = Tare.colors.text)
                    Text(
                        "Tare reads two things from Health Connect, only after you allow it: your weight, " +
                            "so weigh-ins from your scale app show up in your weight trend, and calories burned, " +
                            "so Progress can compare what you ate with what you burned.",
                        style = Tare.type.body,
                        color = Tare.colors.textMuted,
                    )
                    Text(
                        "Tare never writes to Health Connect. Imported weigh-ins are stored on this phone like " +
                            "the ones you type in, and are part of your Tare backups. Nothing is sent to any server.",
                        style = Tare.type.body,
                        color = Tare.colors.textMuted,
                    )
                    Text(
                        "You can remove access any time in Health Connect's settings.",
                        style = Tare.type.body,
                        color = Tare.colors.textMuted,
                    )
                    PrimaryButton("Done", onClick = { finish() })
                }
            }
        }
    }
}
