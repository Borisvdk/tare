package app.tare.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.tare.data.db.WeightEntry
import app.tare.ui.Fmt
import app.tare.ui.components.AmountInputState
import app.tare.ui.components.Card
import app.tare.ui.components.ListRow
import app.tare.ui.components.NumberPad
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SectionHeader
import app.tare.ui.components.SelectChip
import app.tare.ui.components.TareSheet
import app.tare.ui.components.TextAction
import app.tare.ui.navigation.tareViewModel
import app.tare.ui.theme.Tare
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

@Composable
fun ProgressScreen(contentPadding: PaddingValues) {
    val viewModel = tareViewModel { ProgressViewModel(it) }
    val state by viewModel.state.collectAsState()
    var range by rememberSaveable { mutableStateOf(Range.QUARTER) }
    var weighing by remember { mutableStateOf<WeightEntry?>(null) }
    var newWeight by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(Modifier.statusBarsPadding().padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Progress", style = Tare.type.headline, color = Tare.colors.text)
                    Text("The trend, not the noise", style = Tare.type.caption, color = Tare.colors.textMuted)
                }
                RoundIconButton(Icons.Rounded.Add, "Log weight", { newWeight = true }, background = Tare.colors.surface)
            }
        }
        item(key = "weight") { WeightCard(state, range, onRange = { range = it }, onLog = { newWeight = true }) }
        item(key = "intake") { IntakeCard(state) }
        if (state.weights.isNotEmpty()) {
            item(key = "h-weighins") { SectionHeader("Weigh-ins") }
            items(state.weights.asReversed().take(30), key = { it.day }) { entry ->
                val trend = state.trend.firstOrNull { it.day == entry.day }
                ListRow(
                    title = "${Fmt.weight(entry.kg)} kg",
                    subtitle = Fmt.shortDay(LocalDate.ofEpochDay(entry.day)) + (trend?.let { " · trend ${Fmt.weight(it.trendKg)}" } ?: ""),
                    kcal = null,
                    onClick = { weighing = entry },
                )
            }
        }
    }

    if (newWeight || weighing != null) {
        val editing = weighing
        WeightSheet(
            initialKg = editing?.kg ?: state.weights.lastOrNull()?.kg,
            day = editing?.let { LocalDate.ofEpochDay(it.day) } ?: state.today,
            onSave = { day, kg ->
                viewModel.logWeight(day, kg)
                newWeight = false
                weighing = null
            },
            onDelete = editing?.let {
                {
                    viewModel.deleteWeight(LocalDate.ofEpochDay(it.day))
                    weighing = null
                }
            },
            onDismiss = {
                newWeight = false
                weighing = null
            },
        )
    }
}

@Composable
private fun WeightCard(state: ProgressState, range: Range, onRange: (Range) -> Unit, onLog: () -> Unit) {
    val colors = Tare.colors
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Column {
            Text("Weight trend", style = Tare.type.label, color = colors.textMuted)
            val latest = state.latestTrend
            if (latest == null) {
                Spacer(Modifier.height(8.dp))
                Text("No weigh-ins yet", style = Tare.type.title, color = colors.text)
                Text(
                    "Weigh yourself in the morning, a few times a week. Tare smooths out water and food swings so you see where you're really heading.",
                    style = Tare.type.caption,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Log weight", onClick = onLog, modifier = Modifier.fillMaxWidth())
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.weight(latest.trendKg), style = Tare.type.number, color = colors.text)
                Text(" kg", style = Tare.type.title, color = colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                state.trendChange(range.days ?: 3650)?.let { change ->
                    Text(
                        "${Fmt.signedWeight(change)} kg",
                        style = Tare.type.figure,
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            WeightChart(state, range, Modifier.fillMaxWidth().height(160.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Range.entries.forEach { r -> SelectChip(r.label, selected = r == range, onClick = { onRange(r) }) }
            }
        }
    }
}

@Composable
private fun WeightChart(state: ProgressState, range: Range, modifier: Modifier = Modifier) {
    val colors = Tare.colors
    val endDay = state.today.toEpochDay()
    val startDay = range.days?.let { endDay - it } ?: (state.trend.firstOrNull()?.day ?: endDay)
    val points = state.trend.filter { it.day >= startDay }
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val values = points.flatMap { listOf(it.kg, it.trendKg) }
        var lo = values.min()
        var hi = values.max()
        if (hi - lo < 1.0) {
            val mid = (hi + lo) / 2
            lo = mid - 0.5
            hi = mid + 0.5
        }
        val pad = (hi - lo) * 0.12
        lo -= pad
        hi += pad
        val span = max(1L, endDay - startDay).toFloat()
        fun x(day: Long) = ((day - startDay) / span) * size.width
        fun y(kg: Double) = (size.height * (1 - (kg - lo) / (hi - lo))).toFloat()

        // Gridlines.
        repeat(3) { i ->
            val gy = size.height * (i + 1) / 4f
            drawLine(colors.outline, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        }
        // Raw weigh-ins.
        points.forEach { p -> drawCircle(colors.textFaint, 3.dp.toPx(), Offset(x(p.day), y(p.kg))) }
        // Trend line.
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val px = x(p.day)
                val py = y(p.trendKg)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, colors.protein, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val last = points.last()
        drawCircle(colors.protein, 5.dp.toPx(), Offset(x(last.day), y(last.trendKg)))
        drawCircle(colors.surface, 2.dp.toPx(), Offset(x(last.day), y(last.trendKg)))
    }
}

@Composable
private fun IntakeCard(state: ProgressState) {
    val colors = Tare.colors
    val avg7 = state.averageIntake(7)
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Column {
            Text("Calories · last 14 days", style = Tare.type.label, color = colors.textMuted)
            Spacer(Modifier.height(4.dp))
            if (avg7 == null) {
                Text("Log a few days to see your average.", style = Tare.type.body, color = colors.textMuted)
            } else {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.text)) { append(Fmt.kcal(avg7.kcal)) }
                        withStyle(SpanStyle(color = colors.textMuted, fontSize = Tare.type.title.fontSize)) { append(" kcal / day") }
                    },
                    style = Tare.type.number,
                )
                Text(
                    "7-day average of logged days · ${Fmt.grams(avg7.protein)} g protein · target ${Fmt.kcal(state.targets.kcal)}",
                    style = Tare.type.caption,
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.height(16.dp))
            IntakeBars(state, Modifier.fillMaxWidth().height(120.dp))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                state.intake.forEachIndexed { i, d ->
                    Text(
                        if (i % 2 == 1 || i == state.intake.lastIndex) Fmt.weekdayLetter(d.day) else "",
                        style = Tare.type.caption,
                        color = if (d.day == state.today) colors.kcal else colors.textFaint,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntakeBars(state: ProgressState, modifier: Modifier = Modifier) {
    val colors = Tare.colors
    val target = state.targets.kcal.toDouble()
    Canvas(modifier) {
        val maxValue = max(target * 1.25, state.intake.maxOfOrNull { it.nutrients.kcal } ?: 0.0).coerceAtLeast(1.0)
        val slot = size.width / state.intake.size
        val barWidth = min(slot * 0.56f, 18.dp.toPx())
        state.intake.forEachIndexed { i, d ->
            val kcal = d.nutrients.kcal
            val cx = slot * i + slot / 2
            val h = (kcal / maxValue * size.height).toFloat()
            val color = if (kcal > target * 1.05) colors.over else colors.kcal
            if (kcal <= 0) {
                drawRoundRect(colors.track, Offset(cx - barWidth / 2, size.height - 4.dp.toPx()), Size(barWidth, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            } else {
                drawRoundRect(color, Offset(cx - barWidth / 2, size.height - h), Size(barWidth, h), CornerRadius(barWidth / 2))
            }
        }
        val ty = (size.height * (1 - target / maxValue)).toFloat()
        drawLine(colors.textMuted, Offset(0f, ty), Offset(size.width, ty), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
    }
}

@Composable
private fun WeightSheet(
    initialKg: Double?,
    day: LocalDate,
    onSave: (LocalDate, Double) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val input = remember { AmountInputState(initialKg, maxDecimals = 1) }
    TareSheet(onDismiss = onDismiss) {
        Text(if (onDelete != null) "Edit weigh-in" else "Log weight", style = Tare.type.title, color = Tare.colors.text)
        Text(Fmt.daySubtitle(day), style = Tare.type.caption, color = Tare.colors.textMuted)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(input.text.ifEmpty { "0" }, style = Tare.type.hero, color = Tare.colors.text)
            Text(" kg", style = Tare.type.title, color = Tare.colors.textMuted, modifier = Modifier.padding(bottom = 10.dp))
        }
        Spacer(Modifier.height(16.dp))
        NumberPad(onKey = input::press)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            "Save",
            onClick = { input.value?.let { onSave(day, it) } },
            enabled = (input.value ?: 0.0) in 20.0..400.0,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onDelete != null) {
            Spacer(Modifier.height(4.dp))
            TextAction("Delete weigh-in", onClick = onDelete, color = Tare.colors.over, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
