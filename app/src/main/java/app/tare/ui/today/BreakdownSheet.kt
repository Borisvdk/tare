package app.tare.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.tare.domain.DayPart
import app.tare.domain.Nutrients
import app.tare.domain.Share
import app.tare.domain.Targets
import app.tare.domain.rankShares
import app.tare.ui.Fmt
import app.tare.ui.components.EnergySplitBar
import app.tare.ui.components.GramsFigure
import app.tare.ui.components.KcalFigure
import app.tare.ui.components.Macro
import app.tare.ui.components.MacroBars
import app.tare.ui.components.ProgressBar
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.Segmented
import app.tare.ui.components.TareSheet
import app.tare.ui.components.color
import app.tare.ui.components.of
import app.tare.ui.components.pressScale
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare

/** What a breakdown sheet shows: the whole day, or one group of entries logged together. */
sealed interface Breakdown {
    data object Day : Breakdown

    data class Group(val part: DayPart, val groupId: String) : Breakdown
}

/** What the foods in a breakdown are ranked by. */
enum class Measure(val label: String, val macro: Macro?) {
    KCAL("kcal", null),
    PROTEIN("Protein", Macro.PROTEIN),
    CARBS("Carbs", Macro.CARBS),
    FAT("Fat", Macro.FAT),
    FIBRE("Fibre", null),
    ;

    fun of(nutrients: Nutrients): Double = when {
        macro != null -> nutrients.of(macro)
        this == FIBRE -> nutrients.fiber
        else -> nutrients.kcal
    }

    companion object {
        fun of(macro: Macro): Measure = entries.first { it.macro == macro }
    }
}

/**
 * The Dutch Health Council's advice for adults is 30 to 40 g of fibre a day. It's a fixed
 * reference, not a target the user sets: fibre isn't something Tare tracks against goals.
 */
private const val FIBRE_ADVICE_GRAMS = 30

/** Fibre has no colour of its own (colour is for calories and macros), so it stays neutral. */
@Composable
private fun Measure.color(): Color = when {
    macro != null -> macro.color()
    this == Measure.FIBRE -> Tare.colors.textMuted
    else -> Tare.colors.kcal
}

/**
 * How a meal (or a whole day) adds up: its calories against the daily target, where those
 * calories come from, its macros against the targets, and which foods gave the most of each.
 */
@Composable
fun BreakdownSheet(
    title: String,
    subtitle: String,
    total: Nutrients,
    targets: Targets,
    items: List<DayItem>,
    measure: Measure,
    onMeasure: (Measure) -> Unit,
    onItem: (DayItem) -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    /** Identifies what's shown; a new page starts scrolled to the top. */
    page: Any = title,
) {
    TareSheet(onDismiss = onDismiss) {
        key(page) {
            BreakdownContent(title, subtitle, total, targets, items, measure, onMeasure, onItem, onBack)
        }
    }
}

@Composable
private fun BreakdownContent(
    title: String,
    subtitle: String,
    total: Nutrients,
    targets: Targets,
    items: List<DayItem>,
    measure: Measure,
    onMeasure: (Measure) -> Unit,
    onItem: (DayItem) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val colors = Tare.colors
    val shares = rankShares(items) { measure.of(it.nutrients) }
    LazyColumn {
        item(key = "header") {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 40.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = Tare.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = Tare.type.caption, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item(key = "summary") {
            Summary(total, targets, Modifier.padding(top = 18.dp))
        }
        item(key = "measure") {
            Segmented(
                options = Measure.entries,
                selected = measure,
                label = { it.label },
                onSelect = onMeasure,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            )
        }
        items(shares, key = { it.item.key }) { share ->
            ShareRow(share, measure, onClick = { onItem(share.item) }, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun Summary(total: Nutrients, targets: Targets, modifier: Modifier = Modifier) {
    val colors = Tare.colors
    Column(modifier) {
        Row {
            Text(Fmt.kcal(total.kcal), style = Tare.type.number, color = colors.text, modifier = Modifier.alignByBaseline())
            Text(" kcal", style = Tare.type.label, color = colors.textMuted, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            if (targets.kcal > 0) {
                Text(
                    "${Fmt.percent(total.kcal / targets.kcal)} of your ${Fmt.kcal(targets.kcal)} kcal",
                    style = Tare.type.caption,
                    color = colors.textMuted,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        EnergySplitBar(total)
        Spacer(Modifier.height(22.dp))
        MacroBars(total, targets)
        Spacer(Modifier.height(18.dp))
        FibreLine(total.fiber)
    }
}

@Composable
private fun FibreLine(grams: Double) {
    val colors = Tare.colors
    Column {
        Row {
            Text("Fibre", style = Tare.type.caption, color = colors.textMuted, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.text)) { append(Fmt.grams(grams)) }
                    withStyle(SpanStyle(color = colors.textFaint)) { append(" / ${FIBRE_ADVICE_GRAMS}g a day") }
                },
                style = Tare.type.figure,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar((grams / FIBRE_ADVICE_GRAMS).toFloat(), colors.textMuted)
    }
}

@Composable
private fun ShareRow(share: Share<DayItem>, measure: Measure, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Tare.colors
    val item = share.item
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(Radii.row)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val (title, subtitle) = when (item) {
                    is DayItem.Single -> item.entry.name to listOfNotNull(item.entry.brand, Fmt.entryAmount(item.entry)).joinToString(" · ")
                    is DayItem.Group -> item.name to Fmt.items(item.entries.size)
                }
                Text(title, style = Tare.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = Tare.type.caption, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                if (measure == Measure.KCAL) KcalFigure(share.value) else GramsFigure(share.value)
                Text(Fmt.percent(share.fraction), style = Tare.type.caption, color = colors.textFaint)
            }
            if (item is DayItem.Group) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Open", tint = colors.textFaint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(share.fraction.toFloat(), measure.color(), height = 4.dp)
    }
}
