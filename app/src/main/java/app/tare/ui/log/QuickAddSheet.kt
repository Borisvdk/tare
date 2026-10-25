package app.tare.ui.log

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.tare.domain.Nutrients
import app.tare.ui.Fmt
import app.tare.ui.components.AmountInputState
import app.tare.ui.components.NumberPad
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.TareSheet
import app.tare.ui.components.TareTextField
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare

private enum class QuickField(val label: String) { KCAL("Calories"), PROTEIN("Protein"), CARBS("Carbs"), FAT("Fat") }

/**
 * Log raw numbers without a food: a restaurant meal, something from a label you won't eat
 * again. Leaving calories empty derives them from the macros (4/4/9).
 */
@Composable
fun QuickAddSheet(
    onConfirm: (name: String, nutrients: Nutrients) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initial: Nutrients? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colors = Tare.colors
    var name by remember { mutableStateOf(initialName) }
    val inputs = remember {
        mapOf(
            QuickField.KCAL to AmountInputState(initial?.kcal?.takeIf { it > 0 }, maxDecimals = 0),
            QuickField.PROTEIN to AmountInputState(initial?.protein?.takeIf { it > 0 }),
            QuickField.CARBS to AmountInputState(initial?.carbs?.takeIf { it > 0 }),
            QuickField.FAT to AmountInputState(initial?.fat?.takeIf { it > 0 }),
        )
    }
    var focused by remember { mutableStateOf(QuickField.KCAL) }

    val protein = inputs.getValue(QuickField.PROTEIN).value ?: 0.0
    val carbs = inputs.getValue(QuickField.CARBS).value ?: 0.0
    val fat = inputs.getValue(QuickField.FAT).value ?: 0.0
    val typedKcal = inputs.getValue(QuickField.KCAL).value
    val derivedKcal = protein * 4 + carbs * 4 + fat * 9
    val kcal = typedKcal ?: derivedKcal

    TareSheet(onDismiss = onDismiss) {
        Text(if (onDelete != null) "Edit quick add" else "Quick add", style = Tare.type.title, color = colors.text)
        Spacer(Modifier.height(12.dp))
        TareTextField(value = name, onValueChange = { name = it }, placeholder = "Name (optional)")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickField.entries.forEach { field ->
                val accent = when (field) {
                    QuickField.KCAL -> colors.kcal
                    QuickField.PROTEIN -> colors.protein
                    QuickField.CARBS -> colors.carbs
                    QuickField.FAT -> colors.fat
                }
                val input = inputs.getValue(field)
                val shown = when {
                    input.text.isNotEmpty() -> input.text
                    field == QuickField.KCAL && derivedKcal > 0 -> Fmt.kcal(derivedKcal)
                    else -> "0"
                }
                QuickFieldBox(
                    label = field.label,
                    value = shown,
                    unit = if (field == QuickField.KCAL) "kcal" else "g",
                    accent = accent,
                    focused = focused == field,
                    dim = input.text.isEmpty(),
                    onClick = { focused = field },
                    modifier = Modifier.weight(if (field == QuickField.KCAL) 1.3f else 1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        NumberPad(onKey = { inputs.getValue(focused).press(it) }, allowDecimal = focused != QuickField.KCAL)
        Spacer(Modifier.height(12.dp))
        Row {
            if (onDelete != null) {
                RoundIconButton(Icons.Rounded.DeleteOutline, "Delete entry", onDelete, size = 54.dp, tint = colors.over)
                Spacer(Modifier.width(10.dp))
            }
            PrimaryButton(
                text = if (onDelete != null) "Save" else "Log · ${Fmt.kcal(kcal)} kcal",
                onClick = { onConfirm(name.trim(), Nutrients(kcal, protein, carbs, fat)) },
                enabled = kcal > 0,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickFieldBox(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    focused: Boolean,
    dim: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tare.colors
    val border by animateColorAsState(if (focused) accent else Color.Transparent, label = "qf")
    Column(
        modifier
            .clip(Radii.row)
            .background(colors.surfaceRaised)
            .border(1.5.dp, border, Radii.row)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = Tare.type.caption, color = colors.textMuted, maxLines = 1)
        Row(Modifier.fillMaxWidth()) {
            Text(value, style = Tare.type.figure.copy(fontSize = Tare.type.title.fontSize), color = if (dim) colors.textFaint else colors.text, maxLines = 1)
            Text(" $unit", style = Tare.type.caption, color = colors.textFaint, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
