package app.tare.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tare.data.db.DailyFoodWithFood
import app.tare.domain.DayPart
import app.tare.domain.FoodSource
import app.tare.ui.Fmt
import app.tare.ui.components.Card
import app.tare.ui.components.ListRow
import app.tare.ui.components.SecondaryButton
import app.tare.ui.components.SectionHeader
import app.tare.ui.log.AmountMode
import app.tare.ui.log.AmountSheet
import app.tare.ui.log.FoodSearch
import app.tare.ui.meal.FoodPickerDialog
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.root.LocalNotifier
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Foods that log themselves every day: what they are, at what amount and time, and a way to
 * add one without logging it first. The quick way in is the switch in the amount sheet.
 */
@Composable
fun EveryDaySection() {
    val graph = LocalAppGraph.current
    val notify = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val dailies by graph.daily.observe().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DailyFoodWithFood?>(null) }

    SectionHeader("Every day")
    if (dailies.isNotEmpty()) {
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
            Column {
                dailies.forEach { item ->
                    val food = item.food ?: return@forEach
                    val portion = food.portions.firstOrNull { it.label == item.daily.portionLabel }
                    ListRow(
                        title = food.name,
                        subtitle = "${timeOf(item.daily.minuteOfDay).format(TIME)} · " +
                            Fmt.amountOf(item.daily.grams, food.isLiquid, portion?.label, portion?.grams),
                        kcal = item.nutrients.kcal,
                        macros = item.nutrients,
                        onClick = { editing = item },
                    )
                }
            }
        }
    }
    Text(
        if (dailies.isEmpty()) {
            "Have something every day, like a morning coffee? Tare can log it for you each day, and you delete it on a day you skip it. Turn on the repeat button when you log a food, or add one here."
        } else {
            "Logged for you each day, the first time you open Tare. On a day you skip one, delete it from Today."
        },
        style = Tare.type.caption,
        color = Tare.colors.textMuted,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp),
    )
    SecondaryButton("Add a food", icon = Icons.Rounded.Add, onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))

    if (adding) {
        val search = remember { FoodSearch(graph, scope, includeMeals = false) }
        val recent by graph.foods.recent(30).collectAsState(initial = emptyList())
        val now = Instant.ofEpochMilli(graph.now()).atZone(ZoneId.systemDefault()).toLocalTime()
        FoodPickerDialog(
            search = search,
            recent = recent,
            placeholder = "Something you have every day",
            initialTime = DayPart.of(now).defaultTime,
            onPicked = { item, time ->
                adding = false
                graph.appScope.launch {
                    val food = if (item.food.source == FoodSource.OPEN_FOOD_FACTS) graph.foods.saveRemote(item.food) else item.food
                    graph.daily.save(food, item.grams, item.portion, time ?: DayPart.of(now).defaultTime)
                    notify.show("${food.name} logs itself every day from tomorrow", actionLabel = "Undo") {
                        graph.appScope.launch { graph.daily.remove(food.id) }
                    }
                }
            },
            onDismiss = { adding = false },
        )
    }

    editing?.let { item ->
        val food = item.food ?: return@let
        val initialTime = timeOf(item.daily.minuteOfDay)
        AmountSheet(
            food = food,
            mode = AmountMode.EDIT,
            initialGrams = item.daily.grams,
            initialPortionLabel = item.daily.portionLabel,
            initialTime = initialTime,
            deleteDescription = "Stop logging every day",
            onConfirm = { result ->
                editing = null
                graph.appScope.launch {
                    graph.daily.save(food, result.grams, result.portion, result.time ?: initialTime)
                    notify.show("Saved ${food.name} · from tomorrow")
                }
            },
            onDismiss = { editing = null },
            onDelete = {
                editing = null
                graph.appScope.launch {
                    val removed = graph.daily.remove(food.id) ?: return@launch
                    notify.show("${food.name} no longer logs itself", actionLabel = "Undo") {
                        graph.appScope.launch { graph.daily.restore(removed) }
                    }
                }
            },
        )
    }
}

private fun timeOf(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
