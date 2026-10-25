package app.tare.ui.log

import app.tare.AppGraph
import app.tare.data.db.Food
import app.tare.data.db.MealWithItems
import app.tare.data.food.LocalResults
import app.tare.domain.matchesAll
import app.tare.domain.searchTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

sealed interface RemoteState {
    data object Idle : RemoteState
    data object Loading : RemoteState
    data class Loaded(val foods: List<Food>) : RemoteState
    data object Offline : RemoteState
}

data class SearchResults(
    val query: String = "",
    val meals: List<MealWithItems> = emptyList(),
    val local: List<Food> = emptyList(),
    /** Set when [local] is for the query with its typos fixed. */
    val fixedQuery: String? = null,
    val remote: RemoteState = RemoteState.Idle,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

/**
 * Shared search pipeline for the log hub and the meal/recipe ingredient picker. Local results
 * (your foods, bundled basics, saved meals) come back in a few ms; Open Food Facts results
 * follow once typing pauses.
 */
class FoodSearch(private val graph: AppGraph, scope: CoroutineScope, includeMeals: Boolean) {
    val query = MutableStateFlow("")

    private val trimmed = query.map { it.trim() }.distinctUntilChanged()

    private val localResults = trimmed.debounce(80).mapLatest { q ->
        if (q.isEmpty()) {
            emptyList<MealWithItems>() to LocalResults(emptyList())
        } else {
            val meals = if (includeMeals) graph.meals.search(q) else emptyList()
            meals to graph.foods.searchLocalResults(q)
        }
    }

    private val remoteResults = trimmed.debounce(450).flatMapLatest { q ->
        if (q.length < 3) {
            flowOf<RemoteState>(RemoteState.Idle)
        } else {
            flow<RemoteState> {
                emit(RemoteState.Loading)
                val foods = runCatching { graph.foods.searchOnline(q) }.getOrNull()
                emit(if (foods == null) RemoteState.Offline else RemoteState.Loaded(foods))
            }
        }
    }

    val results: StateFlow<SearchResults> = combine(trimmed, localResults, remoteResults) { q, (meals, found), remote ->
        // A "typo" that real products match ("fristi", "ontbijtkoek") was a word we don't
        // know, not a typo: drop the guesses once Open Food Facts shows that.
        val tokens = searchTokens(q)
        val wasRealWord = found.fixedQuery != null && remote is RemoteState.Loaded && remote.foods.any { matchesAll(it.searchText, tokens) }
        val local = if (wasRealWord) emptyList() else found.foods
        val localIds = local.map { it.id }.toSet()
        SearchResults(
            query = q,
            meals = meals,
            local = local,
            fixedQuery = found.fixedQuery.takeUnless { wasRealWord },
            remote = if (remote is RemoteState.Loaded) RemoteState.Loaded(remote.foods.filterNot { it.id in localIds }) else remote,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SearchResults())
}
