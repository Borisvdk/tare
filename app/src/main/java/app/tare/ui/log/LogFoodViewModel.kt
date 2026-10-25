package app.tare.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tare.AppGraph
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.data.db.MealWithItems
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.Targets
import app.tare.domain.sum
import app.tare.ui.components.defaultAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class LogHubState(
    val recent: List<Food> = emptyList(),
    val meals: List<MealWithItems> = emptyList(),
    val dayTotals: Nutrients = Nutrients.Zero,
    val targets: Targets = Targets(),
    /** Entries added while this screen has been open (for the "3 added" bar). */
    val session: List<LogEntry> = emptyList(),
) {
    val remainingKcal: Double get() = targets.kcal - dayTotals.kcal
}

class LogFoodViewModel(private val graph: AppGraph, val day: LocalDate) : ViewModel() {
    /** Entries with an id above this were logged while the screen was open (including via Scan/Describe). */
    private val baseline = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch { baseline.value = graph.log.maxId() }
    }

    val search = FoodSearch(graph, viewModelScope, includeMeals = true)

    val hub: StateFlow<LogHubState> = combine(
        graph.foods.recent(30),
        graph.meals.observeAll(),
        graph.log.observeDay(day),
        graph.settings.targets,
        baseline,
    ) { recent, meals, entries, targets, base ->
        LogHubState(
            recent = recent,
            meals = meals,
            dayTotals = entries.map { it.nutrients }.sum(),
            targets = targets,
            session = if (base == null) emptyList() else entries.filter { it.id > base },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogHubState())

    fun setQuery(q: String) {
        search.query.value = q
    }

    /** One-tap re-log at the food's default amount. Returns the entry for undo. */
    suspend fun quickLog(food: Food): LogEntry {
        val (grams, label) = defaultAmount(food)
        return log(food, grams, food.portions.firstOrNull { it.label == label }, null)
    }

    suspend fun log(food: Food, grams: Double, portion: Portion?, time: LocalTime?): LogEntry {
        val saved = if (food.source == FoodSource.OPEN_FOOD_FACTS) graph.foods.saveRemote(food) else food
        return graph.log.logFood(saved, grams, portion, day, time)
    }

    suspend fun logMeal(meal: MealWithItems, scale: Double, time: LocalTime?): List<LogEntry> {
        return graph.log.logMeal(meal, scale, day, time)
    }

    suspend fun logQuick(name: String, nutrients: Nutrients): LogEntry {
        return graph.log.logQuick(name, nutrients, day)
    }

    fun undo(entries: List<LogEntry>) = viewModelScope.launch { graph.log.delete(entries) }

    fun toggleFavorite(food: Food) = viewModelScope.launch { graph.foods.setFavorite(food.id, !food.favorite) }
}
