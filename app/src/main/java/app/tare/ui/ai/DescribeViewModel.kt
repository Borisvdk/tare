package app.tare.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tare.AppGraph
import app.tare.data.ai.AiResult
import app.tare.data.ai.EstimatedItem
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.domain.DayPart
import app.tare.domain.FoodSource
import app.tare.domain.logTimeFor
import app.tare.domain.Nutrients
import app.tare.domain.buildSearchText
import app.tare.domain.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class EstimateRow(val key: Int, val food: Food, val grams: Double, val lowConfidence: Boolean) {
    val nutrients: Nutrients get() = food.per100.forGrams(grams)
}

sealed interface DescribeState {
    data object Idle : DescribeState
    data object Working : DescribeState
    data class Ready(val title: String, val note: String, val rows: List<EstimateRow>) : DescribeState {
        val total: Nutrients get() = rows.fold(Nutrients.Zero) { acc, r -> acc + r.nutrients }
    }
    data class Error(val message: String) : DescribeState
    data object NoApiKey : DescribeState
}

class DescribeViewModel(
    private val graph: AppGraph,
    private val day: LocalDate,
    private val part: DayPart? = null,
    sharedPhotoUri: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<DescribeState>(DescribeState.Idle)
    val state: StateFlow<DescribeState> = _state.asStateFlow()

    val photo = MutableStateFlow<ByteArray?>(null)

    init {
        if (sharedPhotoUri != null) {
            viewModelScope.launch(Dispatchers.IO) { photo.value = graph.readUri(sharedPhotoUri) }
        }
    }
    private var job: Job? = null

    val hasApiKey: StateFlow<Boolean> = graph.secrets.hasApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val remainingKcal: StateFlow<Double?> = combine(graph.log.observeDay(day), graph.settings.targets) { entries, targets ->
        targets.kcal - entries.sumOf { it.nutrients.kcal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun estimate(text: String) {
        val image = photo.value
        if (text.isBlank() && image == null) return
        job?.cancel()
        _state.value = DescribeState.Working
        job = viewModelScope.launch {
            _state.value = when (val result = graph.ai.estimateMeal(text, image)) {
                is AiResult.Success -> {
                    val now = graph.now()
                    DescribeState.Ready(
                        title = result.value.title.ifBlank { "Meal" },
                        note = result.value.note,
                        rows = result.value.items.filter { it.grams > 0 }.mapIndexed { i, item ->
                            EstimateRow(i, item.toFood(now), item.grams, item.confidence == "low")
                        },
                    )
                }
                AiResult.NoApiKey -> DescribeState.NoApiKey
                is AiResult.Failed -> DescribeState.Error(result.message)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = DescribeState.Idle
    }

    fun setGrams(key: Int, grams: Double) = updateRows { rows -> rows.map { if (it.key == key) it.copy(grams = grams) else it } }

    fun remove(key: Int) = updateRows { rows -> rows.filterNot { it.key == key } }

    fun rename(title: String) = _state.update { if (it is DescribeState.Ready) it.copy(title = title) else it }

    private var logging = false

    /** Logs every row once; repeated taps while it runs return nothing. */
    suspend fun logAll(): List<LogEntry> {
        val ready = _state.value as? DescribeState.Ready ?: return emptyList()
        if (logging) return emptyList()
        logging = true
        val items = ready.rows.map { row -> Triple(graph.foods.saveRemote(row.food), row.grams, null) }
        val time = part?.let { logTimeFor(it, day, Instant.ofEpochMilli(graph.now()).atZone(ZoneId.systemDefault())) }
        val entries = graph.log.logBatch(items, day, ready.title, time)
        _state.value = DescribeState.Idle
        photo.value = null
        logging = false
        return entries
    }

    private fun updateRows(transform: (List<EstimateRow>) -> List<EstimateRow>) =
        _state.update { if (it is DescribeState.Ready) it.copy(rows = transform(it.rows)) else it }
}

/**
 * AI estimates become foods (source AI) keyed by name, so "Flat white" estimated twice is
 * one food that shows up in recents and search with its usage history.
 */
private fun EstimatedItem.toFood(now: Long): Food {
    val cleanName = name.trim().replaceFirstChar { it.titlecase() }
    return Food(
        id = "ai:" + normalizeForSearch(cleanName).replace(' ', '-'),
        source = FoodSource.AI,
        name = cleanName,
        searchText = buildSearchText(cleanName, null, null),
        per100 = Nutrients(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g, fiberPer100g),
        isLiquid = isLiquid,
        createdAt = now,
        updatedAt = now,
    )
}
