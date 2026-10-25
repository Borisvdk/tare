package app.tare.ui.log

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.tare.data.db.Food
import app.tare.domain.FoodSource
import app.tare.domain.Portion
import app.tare.ui.Fmt
import app.tare.ui.components.FoodImage
import app.tare.ui.components.Macro
import app.tare.ui.components.MacroStat
import app.tare.ui.components.NumberPad
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SelectChip
import app.tare.ui.components.TareSheet
import app.tare.ui.components.TextAction
import app.tare.ui.components.defaultAmount
import app.tare.ui.components.of
import app.tare.ui.components.rememberAmountInput
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class AmountMode { LOG, EDIT, PICK }

data class AmountResult(val grams: Double, val portion: Portion?, val time: LocalTime?)

/**
 * The core interaction: pick how much of a food. Units are grams/ml plus the food's portions;
 * a built-in number pad avoids the system keyboard so the sheet never jumps.
 */
@Composable
fun AmountSheet(
    food: Food,
    mode: AmountMode,
    onConfirm: (AmountResult) -> Unit,
    onDismiss: () -> Unit,
    initialGrams: Double? = null,
    initialPortionLabel: String? = null,
    initialTime: LocalTime? = null,
    remainingKcal: Double? = null,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onEditFood: (() -> Unit)? = null,
) {
    val colors = Tare.colors
    val (defaultGrams, defaultLabel) = remember(food.id) { defaultAmount(food) }
    val startGrams = initialGrams ?: defaultGrams
    val startLabel = if (initialGrams != null) initialPortionLabel else defaultLabel
    var portion by remember(food.id) { mutableStateOf(food.portions.firstOrNull { it.label == startLabel }) }
    val input = rememberAmountInput(
        initial = portion?.let { startGrams / it.grams } ?: startGrams,
        key = food.id,
        maxDecimals = 2,
    )
    var time by remember { mutableStateOf(initialTime) }
    var pickingTime by remember { mutableStateOf(false) }

    val grams = (input.value ?: 0.0) * (portion?.grams ?: 1.0)
    val nutrients = food.per100.forGrams(grams)

    TareSheet(onDismiss = onDismiss) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (food.imageUrl != null) {
                FoodImage(food.imageUrl, size = 52.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(food.name, style = Tare.type.title.copy(fontSize = Tare.type.title.fontSize * 1.1f), color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val origin = when (food.source) {
                    FoodSource.GENERIC -> "Basic food"
                    FoodSource.OPEN_FOOD_FACTS -> food.brand ?: "Open Food Facts"
                    FoodSource.CUSTOM -> food.brand ?: "My food"
                    FoodSource.RECIPE -> "Recipe"
                    FoodSource.AI -> "Estimated by Claude"
                    FoodSource.QUICK -> "Quick add"
                }
                Text(
                    "$origin · ${Fmt.kcal(food.per100.kcal)} kcal per 100 ${food.unit}",
                    style = Tare.type.caption,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onEditFood != null) {
                RoundIconButton(Icons.Rounded.Edit, "Edit food", onEditFood, size = 40.dp, iconSize = 18.dp)
                Spacer(Modifier.width(8.dp))
            }
            if (onToggleFavorite != null) {
                RoundIconButton(
                    if (food.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (food.favorite) "Remove favourite" else "Add favourite",
                    onToggleFavorite,
                    size = 40.dp,
                    iconSize = 20.dp,
                    tint = if (food.favorite) colors.fat else colors.textMuted,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Amount readout
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            AnimatedContent(
                targetState = input.text.ifEmpty { "0" },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "amount",
            ) { text ->
                Text(
                    text,
                    style = Tare.type.hero.copy(fontSize = Tare.type.hero.fontSize * 0.85f),
                    color = colors.text,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                portion?.label ?: food.unit,
                style = Tare.type.title,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (portion != null) {
            Text(
                "${Fmt.amount(grams)} ${food.unit}",
                style = Tare.type.caption,
                color = colors.textFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Spacer(Modifier.height(14.dp))

        // Units
        if (food.portions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                SelectChip(food.unit, selected = portion == null, onClick = {
                    if (portion != null) input.set(grams.takeIf { it > 0 }?.let { Math.round(it).toDouble() })
                    portion = null
                })
                food.portions.forEach { p ->
                    SelectChip(
                        "${p.label} · ${Fmt.amount(p.grams)} ${food.unit}",
                        selected = portion == p,
                        onClick = {
                            if (portion != p) input.set(1.0)
                            portion = p
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Live nutrition
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radii.row)
                .background(colors.surfaceRaised)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.kcal)) { append(Fmt.kcal(nutrients.kcal)) }
                        withStyle(SpanStyle(color = colors.textMuted, fontSize = Tare.type.caption.fontSize)) { append(" kcal") }
                    },
                    style = Tare.type.number.copy(fontSize = Tare.type.title.fontSize * 1.3f),
                )
                Spacer(Modifier.weight(1f))
                Macro.entries.forEachIndexed { i, m ->
                    if (i > 0) Spacer(Modifier.width(12.dp))
                    MacroStat(m, nutrients.of(m))
                }
            }
            if (remainingKcal != null && mode == AmountMode.LOG) {
                val after = remainingKcal - nutrients.kcal
                Text(
                    if (after >= 0) "${Fmt.kcal(after)} kcal left after this" else "${Fmt.kcal(-after)} kcal over after this",
                    style = Tare.type.caption,
                    color = if (after >= 0) colors.textMuted else colors.over,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        NumberPad(onKey = input::press)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                RoundIconButton(Icons.Rounded.DeleteOutline, "Delete entry", onDelete, size = 54.dp, tint = colors.over)
                Spacer(Modifier.width(10.dp))
            }
            if (mode != AmountMode.PICK) {
                TextAction(
                    text = time?.format(TIME) ?: "Now",
                    onClick = { pickingTime = true },
                    color = colors.textMuted,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            PrimaryButton(
                text = when (mode) {
                    AmountMode.LOG -> "Log · ${Fmt.kcal(nutrients.kcal)} kcal"
                    AmountMode.EDIT -> "Save"
                    AmountMode.PICK -> "Add · ${Fmt.kcal(nutrients.kcal)} kcal"
                },
                onClick = { onConfirm(AmountResult(grams, portion, time)) },
                enabled = grams > 0,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (pickingTime) {
        val now = time ?: LocalTime.now()
        val state = rememberTimePickerState(now.hour, now.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextAction("Set", onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    pickingTime = false
                })
            },
            dismissButton = { TextAction("Cancel", onClick = { pickingTime = false }, color = colors.textMuted) },
            title = { Text("Time eaten", style = Tare.type.title) },
        ) {
            TimePicker(state)
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
