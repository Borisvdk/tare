package app.tare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.tare.AppGraph
import app.tare.domain.DayPart
import kotlinx.serialization.Serializable

/** Navigation destinations. Days are passed as epoch days. */
sealed interface Route : NavKey {
    @Serializable data object Home : Route

    /** The search/log hub for a day. [part] presets which part of the day logs go to. */
    @Serializable data class LogFood(val day: Long, val focusSearch: Boolean = false, val part: DayPart? = null) : Route

    @Serializable data class Scan(val day: Long, val part: DayPart? = null) : Route

    /** AI logging. [prefill] carries a search query over ("2 eggs and toast"). */
    @Serializable data class Describe(
        val day: Long,
        val prefill: String = "",
        val openCamera: Boolean = false,
        val part: DayPart? = null,
    ) : Route

    /** Create (foodId = null) or edit a custom food. [logDay] logs it right after saving. */
    @Serializable data class FoodEditor(
        val foodId: String? = null,
        val barcode: String? = null,
        val logDay: Long? = null,
        val name: String = "",
        /** Open the camera straight away to read a nutrition label with Claude. */
        val readLabel: Boolean = false,
    ) : Route

    @Serializable data class MealEditor(val mealId: String? = null) : Route

    @Serializable data class RecipeEditor(val recipeId: String? = null) : Route

    @Serializable data object Settings : Route
}

class Navigator(private val backStack: NavBackStack<NavKey>) {
    fun go(route: Route) {
        backStack.add(route)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Replaces the current screen, e.g. Scan → FoodEditor when a barcode is unknown. */
    fun replace(route: Route) {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(route)
    }
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/** ViewModel built from the app graph, scoped to the current nav entry. */
@Composable
inline fun <reified VM : ViewModel> tareViewModel(
    key: String? = null,
    crossinline create: (AppGraph) -> VM,
): VM {
    val graph = LocalAppGraph.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(graph) } })
}
