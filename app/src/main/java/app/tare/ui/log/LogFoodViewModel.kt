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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.tare.domain.DayPart
import app.tare.domain.logTimeFor
import app.tare.domain.usualFoods
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalTime

private data class Hub(val base: Long?, val yesterday: List<CopySource>, val favorites: List<Food>, val usual: List<Food>)

/** One time-of-day block from yesterday that can be copied to the current day in one tap. */
data class CopySource(val part: DayPart, val entries: List<LogEntry>) {
    val kcal: Double get() = entries.sumOf { it.nutrients.kcal }
}

data class LogHubState(
    val yesterday: List<CopySource> = emptyList(),
    val favorites: List<Food> = emptyList(),
    /** What you usually have in the chosen part of the day, carrying your usual amount. */
    val usual: List<Food> = emptyList(),
    val recent: List<Food> = emptyList(),
    val meals: List<MealWithItems> = emptyList(),
    val dayTotals: Nutrients = Nutrients.Zero,
    val targets: Targets = Targets(),
    /** Entries added while this screen has been open (for the "3 added" bar). */
    val session: List<LogEntry> = emptyList(),
) {
    val remainingKcal: Double get() = targets.kcal - dayTotals.kcal
}

class LogFoodViewModel(private val graph: AppGraph, val day: LocalDate, initialPart: DayPart?) : ViewModel() {
    private fun now() = Instant.ofEpochMilli(graph.now()).atZone(ZoneId.systemDefault())

    /** Which part of the day this session logs into; defaults to the current one. */
    val part = MutableStateFlow(initialPart ?: DayPart.of(now().toLocalTime()))

    fun setPart(part: DayPart) {
        this.part.value = part
    }

    /** Time for new entries: null (now) or the chosen part's usual time. */
    fun sessionTime(): LocalTime? = logTimeFor(part.value, day, now())

    /** Entries with an id above this were logged while the screen was open (including via Scan/Describe). */
    private val baseline = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch { baseline.value = graph.log.maxId() }
    }

    val search = FoodSearch(graph, viewModelScope, includeMeals = true)

    private val yesterday = graph.log.observeDay(day.minusDays(1)).map { entries ->
        val zone = ZoneId.systemDefault()
        entries.groupBy { DayPart.of(Instant.ofEpochMilli(it.loggedAt).atZone(zone).toLocalTime()) }
            .toSortedMap()
            .map { (part, list) -> CopySource(part, list) }
    }

    // Foods you had in this part of the day on at least two days of the last four weeks, minus
    // the ones already logged there today. Each comes back as a copy carrying the usual
    // amount, so the row and its + use it.
    private val usual = combine(part, graph.log.observeFoodEntriesSince(day.minusDays(28))) { part, entries ->
        val (thisDay, before) = entries.partition { it.day == day.toEpochDay() }
        val zone = ZoneId.systemDefault()
        val had = usualFoods(thisDay, part, zone, minDays = 1, limit = Int.MAX_VALUE).map { it.foodId }.toSet()
        usualFoods(before, part, zone).filterNot { it.foodId in had }
    }.mapLatest { usual ->
        usual.mapNotNull { u ->
            graph.foods.resolve(u.foodId)?.let { food ->
                food.copy(lastGrams = u.grams, lastPortionLabel = u.portionLabel.takeIf { label -> food.portions.any { it.label == label } })
            }
        }.distinctBy { it.id }
    }

    val hub: StateFlow<LogHubState> = combine(
        graph.foods.recent(30),
        graph.meals.observeAll(),
        graph.log.observeDay(day),
        graph.settings.targets,
        combine(baseline, yesterday, graph.foods.favorites(), usual) { base, yesterday, favorites, usual -> Hub(base, yesterday, favorites, usual) },
    ) { recent, meals, entries, targets, (base, yesterday, favorites, usual) ->
        // Favourites have their own section; don't list anything twice.
        val shown = usual.filterNot { it.favorite }
        val shownIds = shown.map { it.id }.toSet()
        LogHubState(
            yesterday = yesterday,
            favorites = favorites,
            usual = shown,
            recent = recent.filterNot { it.favorite || it.id in shownIds },
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
        return graph.log.logFood(saved, grams, portion, day, time ?: sessionTime())
    }

    suspend fun logMeal(meal: MealWithItems, scale: Double, time: LocalTime?): List<LogEntry> {
        return graph.log.logMeal(meal, scale, day, time ?: sessionTime())
    }

    suspend fun logQuick(name: String, nutrients: Nutrients): LogEntry {
        return graph.log.logQuick(name, nutrients, day, sessionTime())
    }

    suspend fun copy(source: CopySource): List<LogEntry> = graph.log.copyTo(source.entries, day)

    fun undo(entries: List<LogEntry>) = viewModelScope.launch { graph.log.delete(entries) }

    fun toggleFavorite(food: Food) = viewModelScope.launch { graph.foods.setFavorite(food.id, !food.favorite) }
}
