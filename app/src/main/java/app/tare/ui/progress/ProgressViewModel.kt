package app.tare.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tare.AppGraph
import app.tare.data.db.WeightEntry
import app.tare.domain.Nutrients
import app.tare.domain.Targets
import app.tare.domain.TrendPoint
import app.tare.domain.WeightPoint
import app.tare.domain.weightTrend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Range(val label: String, val days: Long?) {
    MONTH("1M", 30),
    QUARTER("3M", 91),
    HALF("6M", 182),
    YEAR("1Y", 365),
    ALL("All", null),
}

data class DayIntake(val day: LocalDate, val nutrients: Nutrients)

data class ProgressState(
    val today: LocalDate,
    val weights: List<WeightEntry> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    /** The last 21 days, oldest first, including empty days (the chart shows the last 14). */
    val intake: List<DayIntake> = emptyList(),
    val targets: Targets = Targets(),
    val loaded: Boolean = false,
) {
    val latestTrend: TrendPoint? get() = trend.lastOrNull()

    /** Trend change over the last [days], or null without enough history. */
    fun trendChange(days: Long): Double? {
        val last = trend.lastOrNull() ?: return null
        val from = last.day - days
        val earlier = trend.lastOrNull { it.day <= from } ?: trend.firstOrNull()?.takeIf { it.day < last.day } ?: return null
        return last.trendKg - earlier.trendKg
    }

    val chart: List<DayIntake> get() = intake.takeLast(14)

    /**
     * Average over the [days] before today (skipping the most recent [skip] days), counting
     * only days with entries so a forgotten day doesn't drag it down. Today is excluded
     * because it isn't finished yet.
     */
    fun averageIntake(days: Int, skip: Int = 0): Nutrients? {
        val logged = intake.filter { it.day < today }.dropLast(skip).takeLast(days).filter { it.nutrients.kcal > 0 }
        if (logged.isEmpty()) return null
        return logged.fold(Nutrients.Zero) { acc, d -> acc + d.nutrients } * (1.0 / logged.size)
    }
}

class ProgressViewModel(private val graph: AppGraph) : ViewModel() {
    private fun currentDay(): LocalDate = Instant.ofEpochMilli(graph.now()).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Re-read on resume so a screen kept alive past midnight logs weight for the right day. */
    private val todayFlow = MutableStateFlow(currentDay())

    fun onResume() {
        todayFlow.value = currentDay()
    }

    val state: StateFlow<ProgressState> = todayFlow.flatMapLatest { today ->
        val from = today.minusDays(20)
        combine(
            graph.weights.observeAll(),
            graph.log.observeTotals(from, today),
            graph.settings.targets,
        ) { weights, totals, targets -> Triple(weights, totals, targets) }
            .map { (weights, totals, targets) -> build(today, from, weights, totals, targets) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState(currentDay()))

    private fun build(
        today: LocalDate,
        from: LocalDate,
        weights: List<WeightEntry>,
        totals: Map<Long, Nutrients>,
        targets: Targets,
    ): ProgressState =
        ProgressState(
            today = today,
            weights = weights,
            trend = weightTrend(weights.map { WeightPoint(it.day, it.kg) }),
            intake = (0L..20L).map { offset ->
                val day = from.plusDays(offset)
                DayIntake(day, totals[day.toEpochDay()] ?: Nutrients.Zero)
            },
            targets = targets,
            loaded = true,
        )

    fun logWeight(day: LocalDate, kg: Double) = viewModelScope.launch { graph.weights.log(day, kg) }

    fun deleteWeight(day: LocalDate) = viewModelScope.launch { graph.weights.delete(day) }
}
