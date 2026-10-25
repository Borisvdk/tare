package app.tare.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tare.AppGraph
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.domain.DayPart
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.Targets
import app.tare.domain.sum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A row on the Today screen: a single entry or a group logged together (saved meal, AI batch). */
sealed interface DayItem {
    val key: String
    val kcal: Double

    data class Single(val entry: LogEntry) : DayItem {
        override val key = "e${entry.id}"
        override val kcal = entry.nutrients.kcal
    }

    data class Group(val groupId: String, val name: String, val entries: List<LogEntry>) : DayItem {
        override val key = "g$groupId"
        override val kcal = entries.sumOf { it.nutrients.kcal }
    }
}

data class DaySection(val part: DayPart, val items: List<DayItem>) {
    val kcal: Double get() = items.sumOf { it.kcal }
}

data class TodayState(
    val day: LocalDate,
    val today: LocalDate,
    val targets: Targets = Targets(),
    val targetsConfirmed: Boolean = true,
    val totals: Nutrients = Nutrients.Zero,
    val sections: List<DaySection> = emptyList(),
    val entries: List<LogEntry> = emptyList(),
    /** kcal per epoch day, for the week strip. */
    val history: Map<Long, Double> = emptyMap(),
    val loaded: Boolean = false,
) {
    val remaining: Double get() = targets.kcal - totals.kcal
}

class TodayViewModel(private val graph: AppGraph) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun today(): LocalDate = Instant.ofEpochMilli(graph.now()).atZone(zone).toLocalDate()

    private val selectedDay = MutableStateFlow(today())

    /** The day that was "today" when the user last looked; detects midnight while open. */
    private var lastSeenToday = today()

    val state: StateFlow<TodayState> = combine(
        selectedDay,
        selectedDay.flatMapLatest { graph.log.observeDay(it) },
        graph.settings.targets,
        graph.settings.targetsConfirmed,
        graph.log.observeTotals(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)).map { m -> m.mapValues { it.value.kcal } },
    ) { day, entries, targets, confirmed, history ->
        TodayState(
            day = day,
            today = today(),
            targets = targets,
            targetsConfirmed = confirmed,
            totals = entries.map { it.nutrients }.sum(),
            sections = sectionsOf(entries),
            entries = entries,
            history = history,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState(today(), today()))

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    fun goToToday() {
        selectedDay.value = today()
    }

    /**
     * Called when the screen resumes. If the date changed since the user last looked and they
     * were on "today", follow the new day; if they were browsing another day, leave it.
     */
    fun onResume() {
        val now = today()
        if (now != lastSeenToday) {
            if (selectedDay.value == lastSeenToday) selectedDay.value = now
            lastSeenToday = now
        }
    }

    fun updateAmount(entry: LogEntry, grams: Double, portion: Portion?, time: LocalTime?) = viewModelScope.launch {
        graph.log.updateAmount(entry, grams, portion)
        if (time != null) graph.log.get(entry.id)?.let { graph.log.setTime(listOf(it), time) }
    }

    fun updateQuick(entry: LogEntry, name: String, nutrients: Nutrients) = viewModelScope.launch {
        graph.log.update(entry.copy(name = name.ifBlank { "Quick add" }, nutrients = nutrients))
    }

    fun setTime(entries: List<LogEntry>, time: LocalTime) = viewModelScope.launch { graph.log.setTime(entries, time) }

    /** Deletes and hands back the rows so the UI can offer undo. */
    suspend fun delete(entries: List<LogEntry>): List<LogEntry> = graph.log.delete(entries)

    fun restore(entries: List<LogEntry>) = viewModelScope.launch { graph.log.restore(entries) }

    fun copyToToday(entries: List<LogEntry>) = viewModelScope.launch { graph.log.copyTo(entries, today()) }

    /** Copies everything from the selected day to today; returns the copies for undo. */
    suspend fun copyDayToToday(): List<LogEntry> = graph.log.copyTo(state.value.entries, today())

    fun undoCopy(copies: List<LogEntry>) = graph.appScope.launch { graph.log.delete(copies) }

    fun copyTo(entries: List<LogEntry>, day: LocalDate) = viewModelScope.launch { graph.log.copyTo(entries, day) }

    suspend fun saveAsMeal(name: String, entries: List<LogEntry>): String = graph.meals.saveFromEntries(name, entries)

    suspend fun food(id: String): Food? = graph.foods.resolve(id)

    fun toggleFavorite(food: Food) = viewModelScope.launch { graph.foods.setFavorite(food.id, !food.favorite) }

    private fun sectionsOf(entries: List<LogEntry>): List<DaySection> {
        val zone = zone
        val byPart = entries.groupBy { DayPart.of(Instant.ofEpochMilli(it.loggedAt).atZone(zone).toLocalTime()) }
        return DayPart.entries.mapNotNull { part ->
            val list = byPart[part] ?: return@mapNotNull null
            val items = mutableListOf<DayItem>()
            val seenGroups = mutableSetOf<String>()
            list.forEach { entry ->
                val gid = entry.groupId
                if (gid == null) {
                    items += DayItem.Single(entry)
                } else if (seenGroups.add(gid)) {
                    val members = list.filter { it.groupId == gid }
                    items += if (members.size == 1) {
                        DayItem.Single(entry)
                    } else {
                        DayItem.Group(gid, entry.groupName ?: "Meal", members)
                    }
                }
            }
            DaySection(part, items)
        }
    }
}
