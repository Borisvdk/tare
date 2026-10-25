package app.tare.ui.meal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tare.AppGraph
import app.tare.data.db.Food
import app.tare.data.db.MealWithItems
import app.tare.data.meal.DraftItem
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.sum
import app.tare.ui.log.FoodSearch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared editing state for anything made of foods + amounts (meals and recipes). */
abstract class ItemsEditorViewModel(protected val graph: AppGraph) : ViewModel() {
    var name by mutableStateOf("")
    val items = mutableStateListOf<DraftItem>()
    var loaded by mutableStateOf(false)
        protected set

    val search = FoodSearch(graph, viewModelScope, includeMeals = false)
    val recent: StateFlow<List<Food>> = graph.foods.recent(30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: Nutrients get() = items.map { it.nutrients }.sum()

    fun add(item: DraftItem) {
        viewModelScope.launch {
            val food = if (item.food.source == FoodSource.OPEN_FOOD_FACTS) graph.foods.saveRemote(item.food) else item.food
            items += item.copy(food = food)
        }
    }

    fun replace(index: Int, item: DraftItem) {
        if (index in items.indices) items[index] = item
    }

    fun remove(index: Int) {
        if (index in items.indices) items.removeAt(index)
    }
}

class MealEditorViewModel(graph: AppGraph, private val mealId: String?) : ItemsEditorViewModel(graph) {
    val isNew: Boolean get() = mealId == null

    init {
        viewModelScope.launch {
            mealId?.let { graph.meals.get(it) }?.let { meal ->
                name = meal.meal.name
                items += meal.sortedItems.mapNotNull { i ->
                    i.food?.let { f -> DraftItem(f, i.item.grams, f.portions.firstOrNull { it.label == i.item.portionLabel }) }
                }
            }
            loaded = true
        }
    }

    val canSave: Boolean get() = name.isNotBlank() && items.isNotEmpty()

    suspend fun save() = graph.meals.save(mealId, name, items.toList())

    suspend fun delete(): MealWithItems? = mealId?.let { graph.meals.delete(it) }
}
