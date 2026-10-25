package app.tare.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tare.data.db.MealWithItems
import app.tare.ui.Fmt
import app.tare.ui.components.KcalFigure
import app.tare.ui.components.MacroLine
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SelectChip
import app.tare.ui.components.TareSheet
import app.tare.ui.theme.Tare
import kotlin.math.roundToInt

private val SCALES = listOf(0.5 to "½×", 1.0 to "1×", 1.5 to "1½×", 2.0 to "2×")

/** Confirm logging a saved meal, optionally scaled (half portion, double, …). */
@Composable
fun MealLogSheet(
    meal: MealWithItems,
    remainingKcal: Double?,
    onLog: (scale: Double) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Tare.colors
    var scale by remember { mutableDoubleStateOf(1.0) }
    val total = meal.nutrients * scale
    TareSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal.meal.name, style = Tare.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Fmt.items(meal.items.size), style = Tare.type.caption, color = colors.textMuted)
            }
            RoundIconButton(Icons.Rounded.Edit, "Edit meal", onEdit, size = 40.dp, iconSize = 18.dp)
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            meal.sortedItems.forEach { item ->
                val food = item.food ?: return@forEach
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(food.name, style = Tare.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(item.item.grams * scale).roundToInt()} ${food.unit}", style = Tare.type.caption, color = colors.textMuted)
                    }
                    KcalFigure(item.nutrients.kcal * scale)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SCALES.forEach { (value, label) ->
                SelectChip(label, selected = scale == value, onClick = { scale = value })
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MacroLine(total)
                if (remainingKcal != null) {
                    val after = remainingKcal - total.kcal
                    Text(
                        if (after >= 0) "${Fmt.kcal(after)} kcal left after this" else "${Fmt.kcal(-after)} kcal over after this",
                        style = Tare.type.caption,
                        color = if (after >= 0) colors.textFaint else colors.over,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Log meal · ${Fmt.kcal(total.kcal)} kcal", onClick = { onLog(scale) }, modifier = Modifier.fillMaxWidth())
    }
}
