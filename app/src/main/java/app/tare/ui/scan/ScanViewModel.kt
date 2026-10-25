package app.tare.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tare.AppGraph
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.data.food.BarcodeResult
import app.tare.domain.Portion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed interface ScanState {
    data object Scanning : ScanState
    data class Looking(val barcode: String) : ScanState
    data class Found(val food: Food) : ScanState

    /** Unknown, incomplete or unreachable — all offer the same ways forward. */
    data class Missing(val barcode: String, val title: String, val message: String, val name: String?) : ScanState
}

class ScanViewModel(private val graph: AppGraph, private val day: LocalDate) : ViewModel() {
    private val _state = MutableStateFlow<ScanState>(ScanState.Scanning)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    val remainingKcal: StateFlow<Double?> = combine(graph.log.observeDay(day), graph.settings.targets) { entries, targets ->
        targets.kcal - entries.sumOf { it.nutrients.kcal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onBarcode(barcode: String) {
        if (_state.value != ScanState.Scanning) return
        _state.value = ScanState.Looking(barcode)
        viewModelScope.launch {
            _state.value = when (val result = graph.foods.lookupBarcode(barcode)) {
                is BarcodeResult.Found -> ScanState.Found(result.food)
                is BarcodeResult.NotFound -> ScanState.Missing(
                    barcode,
                    "Not in Open Food Facts yet",
                    "Snap the nutrition label and Claude fills it in, or type the values yourself. Next time this barcode scans instantly.",
                    null,
                )
                is BarcodeResult.Incomplete -> ScanState.Missing(
                    barcode,
                    result.name ?: "Missing nutrition data",
                    "This product is known but has no nutrition values. Add them once and it's saved for next time.",
                    result.name,
                )
                is BarcodeResult.Failed -> ScanState.Missing(
                    barcode,
                    "Couldn't look it up",
                    "Open Food Facts didn't respond (${result.message}). You can still add the product yourself.",
                    null,
                )
            }
        }
    }

    fun reset() {
        _state.value = ScanState.Scanning
    }

    suspend fun log(food: Food, grams: Double, portion: Portion?, time: LocalTime?): LogEntry =
        graph.log.logFood(food, grams, portion, day, time)
}
