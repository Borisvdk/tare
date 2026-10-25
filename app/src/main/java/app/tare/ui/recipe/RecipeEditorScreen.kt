package app.tare.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.tare.AppGraph
import app.tare.data.meal.DraftItem
import app.tare.domain.Nutrients
import app.tare.ui.Fmt
import app.tare.ui.components.SectionHeader
import app.tare.ui.components.TareTextField
import app.tare.ui.meal.EditorScaffold
import app.tare.ui.meal.ItemsEditorViewModel
import app.tare.ui.meal.ItemsSection
import app.tare.ui.meal.TotalsCard
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Route
import app.tare.ui.navigation.tareViewModel
import app.tare.ui.root.LocalSnackbar
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch

class RecipeEditorViewModel(graph: AppGraph, private val recipeId: String?) : ItemsEditorViewModel(graph) {
    var cookedWeight by mutableStateOf("")
    var servings by mutableStateOf("")
    val isNew: Boolean get() = recipeId == null

    init {
        viewModelScope.launch {
            recipeId?.let { graph.recipes.get(it) }?.let { r ->
                name = r.recipe.name
                cookedWeight = r.recipe.recipeYieldGrams?.let(Fmt::amount).orEmpty()
                servings = r.recipe.recipeServings?.let(Fmt::amount).orEmpty()
                items += r.sortedIngredients.mapNotNull { i ->
                    i.food?.let { f -> DraftItem(f, i.ingredient.grams, f.portions.firstOrNull { it.label == i.ingredient.portionLabel }) }
                }
            }
            loaded = true
        }
    }

    private fun num(text: String) = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    val rawWeight: Double get() = items.sumOf { it.grams }
    val yieldGrams: Double get() = num(cookedWeight) ?: rawWeight
    val servingCount: Double? get() = num(servings)

    val per100: Nutrients get() = if (yieldGrams > 0) total * (100.0 / yieldGrams) else Nutrients.Zero

    val canSave: Boolean get() = name.isNotBlank() && items.isNotEmpty()

    suspend fun save() = graph.recipes.save(recipeId, name, items.toList(), num(cookedWeight), servingCount)

    suspend fun delete() {
        recipeId?.let { graph.foods.hide(it) }
    }
}

@Composable
fun RecipeEditorScreen(route: Route.RecipeEditor) {
    val viewModel = tareViewModel(key = "recipe-${route.recipeId}") { RecipeEditorViewModel(it, route.recipeId) }
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    EditorScaffold(
        title = if (viewModel.isNew) "New recipe" else "Edit recipe",
        onBack = { nav.back() },
        onDelete = if (viewModel.isNew) null else {
            {
                scope.launch {
                    viewModel.delete()
                    nav.back()
                    snackbar.showSnackbar("Recipe deleted")
                }
            }
        },
        primaryText = "Save recipe",
        primaryEnabled = viewModel.canSave,
        onPrimary = {
            scope.launch {
                val recipe = viewModel.save()
                nav.back()
                snackbar.showSnackbar("Saved ${recipe.name}")
            }
        },
    ) {
        TareTextField(viewModel.name, { viewModel.name = it }, label = "Name", placeholder = "e.g. Chili con carne", imeAction = ImeAction.Done)
        ItemsSection(viewModel, emptyHint = "Add every ingredient with its raw weight. Then weigh the pot when it's done so each portion is exact.")

        SectionHeader("After cooking")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TareTextField(
                value = viewModel.cookedWeight,
                onValueChange = { t -> viewModel.cookedWeight = t.filter { it.isDigit() || it == '.' || it == ',' } },
                label = "Cooked weight",
                placeholder = Fmt.amount(viewModel.rawWeight),
                keyboardType = KeyboardType.Decimal,
                suffix = "g",
                modifier = Modifier.weight(1f),
            )
            TareTextField(
                value = viewModel.servings,
                onValueChange = { t -> viewModel.servings = t.filter { it.isDigit() || it == '.' || it == ',' } },
                label = "Servings",
                placeholder = "Optional",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Leave cooked weight empty to use the raw total. Water lost while cooking makes the food denser, so weighing matters for stews and rice.",
            style = Tare.type.caption,
            color = Tare.colors.textFaint,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )

        val per100 = viewModel.per100
        val perServing = viewModel.servingCount?.let { viewModel.total * (1.0 / it) }
        TotalsCard(
            total = per100,
            caption = "Per 100 g",
            extra = perServing?.let { "Per serving (${Fmt.amount(viewModel.yieldGrams / viewModel.servingCount!!)} g): ${Fmt.kcal(it.kcal)} kcal" },
        )
    }
}
