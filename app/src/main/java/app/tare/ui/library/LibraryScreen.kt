package app.tare.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tare.AppGraph
import app.tare.data.db.Food
import app.tare.data.db.MealWithItems
import app.tare.domain.FoodSource
import app.tare.ui.Fmt
import app.tare.ui.components.EmptyState
import app.tare.ui.components.FoodRow
import app.tare.ui.components.ListRow
import app.tare.ui.components.SecondaryButton
import app.tare.ui.components.Segmented
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Route
import app.tare.ui.navigation.tareViewModel
import app.tare.ui.theme.Tare
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

enum class LibraryTab(val label: String) { MEALS("Meals"), RECIPES("Recipes"), FOODS("My foods") }

class LibraryViewModel(graph: AppGraph) : ViewModel() {
    val meals: StateFlow<List<MealWithItems>?> = graph.meals.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val recipes: StateFlow<List<Food>?> = graph.foods.recipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val foods: StateFlow<List<Food>?> = graph.foods.customAndFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun LibraryScreen(contentPadding: PaddingValues) {
    val viewModel = tareViewModel { LibraryViewModel(it) }
    val meals by viewModel.meals.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val foods by viewModel.foods.collectAsState()
    val nav = LocalNavigator.current
    var tab by rememberSaveable { mutableStateOf(LibraryTab.MEALS) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        item(key = "header") {
            Column(Modifier.statusBarsPadding().padding(top = 12.dp, bottom = 12.dp, start = 4.dp)) {
                Text("Library", style = Tare.type.headline, color = Tare.colors.text)
                Text("Things you eat again and again", style = Tare.type.caption, color = Tare.colors.textMuted)
            }
        }
        item(key = "tabs") {
            Segmented(LibraryTab.entries, tab, { it.label }, { tab = it }, Modifier.fillMaxWidth())
        }
        item(key = "new-$tab") {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(
                    when (tab) {
                        LibraryTab.MEALS -> "New meal"
                        LibraryTab.RECIPES -> "New recipe"
                        LibraryTab.FOODS -> "New food"
                    },
                    icon = Icons.Rounded.Add,
                    onClick = {
                        nav.go(
                            when (tab) {
                                LibraryTab.MEALS -> Route.MealEditor()
                                LibraryTab.RECIPES -> Route.RecipeEditor()
                                LibraryTab.FOODS -> Route.FoodEditor()
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        when (tab) {
            LibraryTab.MEALS -> {
                val list = meals
                if (list != null && list.isEmpty()) {
                    item(key = "empty-meals") {
                        EmptyState("No meals yet", "Save foods you eat together, like your usual breakfast. Or long-press entries on Today and tap save.")
                    }
                }
                items(list.orEmpty(), key = { it.meal.id }) { meal ->
                    ListRow(
                        title = meal.meal.name,
                        subtitle = meal.sortedItems.mapNotNull { it.food?.name }.joinToString(", "),
                        kcal = meal.nutrients.kcal,
                        onClick = { nav.go(Route.MealEditor(meal.meal.id)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            LibraryTab.RECIPES -> {
                val list = recipes
                if (list != null && list.isEmpty()) {
                    item(key = "empty-recipes") {
                        EmptyState("No recipes yet", "Cook once, weigh the pot, and log any portion by weight from then on.")
                    }
                }
                items(list.orEmpty(), key = { it.id }) { recipe ->
                    ListRow(
                        title = recipe.name,
                        subtitle = "${Fmt.kcal(recipe.per100.kcal)} kcal per 100 g" +
                            (recipe.portions.firstOrNull { it.label == "serving" }?.let { " · serving ${Fmt.amount(it.grams)} g" } ?: ""),
                        kcal = null,
                        onClick = { nav.go(Route.RecipeEditor(recipe.id)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            LibraryTab.FOODS -> {
                val list = foods
                if (list != null && list.isEmpty()) {
                    item(key = "empty-foods") {
                        EmptyState("No foods yet", "Foods you create and products you star show up here.")
                    }
                }
                items(list.orEmpty(), key = { it.id }) { food ->
                    FoodRow(
                        food = food,
                        onClick = {
                            nav.go(
                                if (food.source == FoodSource.RECIPE) Route.RecipeEditor(food.id) else Route.FoodEditor(foodId = food.id),
                            )
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
