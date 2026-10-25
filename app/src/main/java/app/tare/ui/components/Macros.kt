package app.tare.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.tare.domain.Nutrients
import app.tare.domain.Targets
import app.tare.ui.Fmt
import app.tare.ui.theme.Tare

enum class Macro(val label: String, val short: String) {
    PROTEIN("Protein", "P"),
    CARBS("Carbs", "C"),
    FAT("Fat", "F"),
}

@Composable
fun Macro.color(): Color = when (this) {
    Macro.PROTEIN -> Tare.colors.protein
    Macro.CARBS -> Tare.colors.carbs
    Macro.FAT -> Tare.colors.fat
}

fun Nutrients.of(macro: Macro): Double = when (macro) {
    Macro.PROTEIN -> protein
    Macro.CARBS -> carbs
    Macro.FAT -> fat
}

fun Targets.of(macro: Macro): Int = when (macro) {
    Macro.PROTEIN -> protein
    Macro.CARBS -> carbs
    Macro.FAT -> fat
}

/** Three macro bars side by side: label, "82 / 150 g", and a thin bar. */
@Composable
fun MacroBars(eaten: Nutrients, targets: Targets, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Macro.entries.forEach { macro ->
            MacroBar(
                macro = macro,
                value = eaten.of(macro),
                target = targets.of(macro),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun MacroBar(macro: Macro, value: Double, target: Int, modifier: Modifier = Modifier) {
    val colors = Tare.colors
    val color = macro.color()
    Column(modifier) {
        Text(macro.label, style = Tare.type.caption, color = colors.textMuted)
        Spacer(Modifier.height(2.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.text)) { append(Fmt.grams(value)) }
                withStyle(SpanStyle(color = colors.textFaint)) { append(" / ${target}g") }
            },
            style = Tare.type.figure,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        ProgressBar(fraction = if (target > 0) (value / target).toFloat() else 0f, color = color)
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = Tare.colors.track
    val animated by animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "bar",
    )
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (animated > 0f) {
            val width = (size.width * animated).coerceAtLeast(size.height)
            drawRoundRect(color, size = Size(width, size.height), cornerRadius = radius)
        }
    }
}

/** Compact "P 32 · C 45 · F 12" line with coloured initials. */
@Composable
fun MacroLine(nutrients: Nutrients, modifier: Modifier = Modifier) {
    val colors = Tare.colors
    val protein = colors.protein
    val carbs = colors.carbs
    val fat = colors.fat
    Text(
        buildAnnotatedString {
            listOf(Macro.PROTEIN to protein, Macro.CARBS to carbs, Macro.FAT to fat).forEachIndexed { i, (m, c) ->
                if (i > 0) withStyle(SpanStyle(color = colors.textFaint)) { append("  ") }
                withStyle(SpanStyle(color = c)) { append(m.short) }
                withStyle(SpanStyle(color = colors.textMuted)) { append(" ${Fmt.grams(nutrients.of(m))}") }
            }
        },
        style = Tare.type.caption.merge(Tare.type.figure.copy(fontSize = Tare.type.caption.fontSize)),
        modifier = modifier,
        maxLines = 1,
    )
}

/** A larger macro readout used in sheets: coloured dot, value, label. */
@Composable
fun MacroStat(macro: Macro, grams: Double, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(macro.color(), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(Fmt.grams(grams), style = Tare.type.figure, color = Tare.colors.text)
        Text(" g ${macro.label.lowercase()}", style = Tare.type.caption, color = Tare.colors.textMuted)
    }
}
