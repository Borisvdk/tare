package app.tare.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.tare.domain.Targets
import app.tare.ui.Fmt
import app.tare.ui.components.AmountInputState
import app.tare.ui.components.NumberPad
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.TareSheet
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class TargetField(val label: String, val unit: String) {
    KCAL("Calories", "kcal"),
    PROTEIN("Protein", "g"),
    CARBS("Carbs", "g"),
    FAT("Fat", "g"),
}

/** Daily targets. No formulas, no auto-adjusting: you pick the numbers. */
@Composable
fun TargetsSheet(onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val current by graph.settings.targets.collectAsState(initial = null)
    val colors = Tare.colors
    val start = current ?: return
    val inputs = remember(start) {
        mapOf(
            TargetField.KCAL to AmountInputState(start.kcal.toDouble(), maxDecimals = 0),
            TargetField.PROTEIN to AmountInputState(start.protein.toDouble(), maxDecimals = 0),
            TargetField.CARBS to AmountInputState(start.carbs.toDouble(), maxDecimals = 0),
            TargetField.FAT to AmountInputState(start.fat.toDouble(), maxDecimals = 0),
        )
    }
    var focused by remember { mutableStateOf(TargetField.KCAL) }

    fun value(f: TargetField) = inputs.getValue(f).value?.toInt() ?: 0
    val targets = Targets(value(TargetField.KCAL), value(TargetField.PROTEIN), value(TargetField.CARBS), value(TargetField.FAT))
    val diff = targets.macroKcal - targets.kcal

    TareSheet(onDismiss = onDismiss) {
        Text("Daily targets", style = Tare.type.title, color = colors.text)
        Text("Pick numbers you can live with. Tare never changes them for you.", style = Tare.type.caption, color = colors.textMuted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetField.entries.forEach { field ->
                val accent = when (field) {
                    TargetField.KCAL -> colors.kcal
                    TargetField.PROTEIN -> colors.protein
                    TargetField.CARBS -> colors.carbs
                    TargetField.FAT -> colors.fat
                }
                val border by animateColorAsState(if (focused == field) accent else Color.Transparent, label = "tf")
                Column(
                    Modifier
                        .weight(if (field == TargetField.KCAL) 1.3f else 1f)
                        .clip(Radii.row)
                        .background(colors.surfaceRaised)
                        .border(1.5.dp, border, Radii.row)
                        .clickable { focused = field }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(field.label, style = Tare.type.caption, color = colors.textMuted)
                    Text(
                        inputs.getValue(field).text.ifEmpty { "0" } + " " + field.unit,
                        style = Tare.type.figure,
                        color = colors.text,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                targets.macroKcal == 0 -> " "
                abs(diff) <= 50 -> "Macros add up to ${Fmt.kcal(targets.macroKcal)} kcal. Nicely balanced."
                diff > 0 -> "Macros add up to ${Fmt.kcal(targets.macroKcal)} kcal, ${Fmt.kcal(diff)} more than your calorie target."
                else -> "Macros add up to ${Fmt.kcal(targets.macroKcal)} kcal, ${Fmt.kcal(-diff)} less than your calorie target."
            },
            style = Tare.type.caption,
            color = if (abs(diff) <= 50) colors.carbs else colors.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        NumberPad(onKey = { inputs.getValue(focused).press(it) }, allowDecimal = false)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            "Save targets",
            onClick = {
                scope.launch {
                    graph.settings.setTargets(targets)
                    onDismiss()
                }
            },
            enabled = targets.kcal > 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
