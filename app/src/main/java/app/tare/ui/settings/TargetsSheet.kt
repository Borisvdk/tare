package app.tare.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import app.tare.domain.MacroPlan
import app.tare.domain.Targets
import app.tare.domain.WeightPoint
import app.tare.domain.planTargets
import app.tare.domain.weightTrend
import app.tare.ui.Fmt
import app.tare.ui.components.AmountInputState
import app.tare.ui.components.NumberPad
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.SelectChip
import app.tare.ui.components.TareSheet
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private enum class TargetField { KCAL, PROTEIN, FAT }

private data class TargetsStart(val targets: Targets, val plan: MacroPlan, val weightKg: Double?)

/**
 * Daily targets. You set calories; protein follows body weight (or a number you type), fat a
 * share of calories (or a number you type), and carbs fill the rest, so changing calories
 * keeps the macros adding up. Tare never changes the saved numbers by itself.
 */
@Composable
fun TargetsSheet(onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val colors = Tare.colors
    val start by remember {
        combine(graph.settings.targets, graph.settings.macroPlan, graph.weights.observeAll()) { targets, plan, weights ->
            val trend = weightTrend(weights.map { WeightPoint(it.day, it.kg) }).lastOrNull()?.trendKg
            TargetsStart(targets, plan ?: MacroPlan.inferFrom(targets), trend)
        }
    }.collectAsState(initial = null)
    val initial = start ?: return
    val weight = initial.weightKg

    val kcalInput = remember(initial) { AmountInputState(initial.targets.kcal.toDouble(), maxDecimals = 0) }
    val proteinInput = remember(initial) { AmountInputState(initial.targets.protein.toDouble(), maxDecimals = 0) }
    val fatInput = remember(initial) { AmountInputState(initial.targets.fat.toDouble(), maxDecimals = 0) }
    var plan by remember(initial) { mutableStateOf(initial.plan) }
    var focused by remember { mutableStateOf(TargetField.KCAL) }

    val kcal = kcalInput.value?.toInt() ?: 0
    val targets = planTargets(
        kcal = kcal,
        plan = plan,
        weightKg = weight,
        protein = proteinInput.value?.toInt() ?: 0,
        fat = fatInput.value?.toInt() ?: 0,
    )

    fun press(key: app.tare.ui.components.PadKey) {
        when (focused) {
            TargetField.KCAL -> kcalInput.press(key)
            // Typing a number takes that macro off its rule.
            TargetField.PROTEIN -> {
                if (plan.proteinPerKg != null) {
                    proteinInput.set(targets.protein.toDouble())
                    plan = plan.copy(proteinPerKg = null)
                }
                proteinInput.press(key)
            }
            TargetField.FAT -> {
                if (plan.fatPercent != null) {
                    fatInput.set(targets.fat.toDouble())
                    plan = plan.copy(fatPercent = null)
                }
                fatInput.press(key)
            }
        }
    }

    TareSheet(onDismiss = onDismiss) {
        Text("Daily targets", style = Tare.type.title, color = colors.text)
        Text("Set calories; protein and fat follow your choices, carbs fill the rest.", style = Tare.type.caption, color = colors.textMuted)
        Spacer(Modifier.height(14.dp))

        TargetBox("Calories", "${kcalInput.text.ifEmpty { "0" }} kcal", colors.kcal, focused == TargetField.KCAL, Modifier.fillMaxWidth()) {
            focused = TargetField.KCAL
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetBox("Protein", "${targets.protein} g", colors.protein, focused == TargetField.PROTEIN, Modifier.weight(1f)) {
                focused = TargetField.PROTEIN
            }
            TargetBox("Fat", "${targets.fat} g", colors.fat, focused == TargetField.FAT, Modifier.weight(1f)) {
                focused = TargetField.FAT
            }
            TargetBox("Carbs · the rest", "${targets.carbs} g", colors.carbs, selected = false, modifier = Modifier.weight(1f), onClick = null)
        }

        Spacer(Modifier.height(12.dp))
        RuleRow("Protein") {
            MacroPlan.PROTEIN_PER_KG.forEach { perKg ->
                SelectChip(
                    "${"%.1f".format(java.util.Locale.ROOT, perKg)} g/kg",
                    selected = weight != null && plan.proteinPerKg == perKg,
                    onClick = {
                        if (weight != null) {
                            plan = plan.copy(proteinPerKg = perKg)
                            focused = TargetField.KCAL
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        RuleRow("Fat") {
            MacroPlan.FAT_PERCENT.forEach { percent ->
                SelectChip("$percent%", selected = plan.fatPercent == percent, onClick = {
                    plan = plan.copy(fatPercent = percent)
                    focused = TargetField.KCAL
                })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("1.6–2.2 g protein per kg keeps muscle; aim high while eating less. Fat 25–35% of calories.")
                append(if (weight != null) " Based on your trend weight, ${Fmt.amount(weight)} kg." else " Log a weigh-in on Progress for g/kg protein.")
                if (targets.carbs == 0 && kcal > 0) append(" Protein and fat already use all your calories.")
            },
            style = Tare.type.caption,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        NumberPad(onKey = ::press, allowDecimal = false)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            "Save targets",
            onClick = {
                scope.launch {
                    graph.settings.setTargets(targets, plan)
                    onDismiss()
                }
            },
            enabled = targets.kcal > 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TargetBox(label: String, value: String, accent: Color, selected: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    val colors = Tare.colors
    val border by animateColorAsState(if (selected) accent else Color.Transparent, label = "tf")
    Column(
        modifier
            .clip(Radii.row)
            .background(colors.surfaceRaised)
            .border(1.5.dp, border, Radii.row)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = Tare.type.caption, color = colors.textMuted, maxLines = 1)
        Text(value, style = Tare.type.figure, color = colors.text, maxLines = 1)
    }
}

@Composable
private fun RuleRow(label: String, chips: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Tare.type.caption, color = Tare.colors.textMuted, modifier = Modifier.width(56.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}
