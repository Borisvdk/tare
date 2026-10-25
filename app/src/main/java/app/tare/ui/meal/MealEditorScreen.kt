package app.tare.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tare.data.meal.DraftItem
import app.tare.domain.Nutrients
import app.tare.ui.components.Card
import app.tare.ui.components.KcalFigure
import app.tare.ui.components.ListRow
import app.tare.ui.components.MacroLine
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SecondaryButton
import app.tare.ui.components.SectionHeader
import app.tare.ui.components.TareTextField
import app.tare.ui.components.foodSubtitle
import app.tare.ui.log.AmountMode
import app.tare.ui.log.AmountSheet
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Route
import app.tare.ui.navigation.tareViewModel
import app.tare.ui.root.LocalNotifier
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch

@Composable
fun MealEditorScreen(route: Route.MealEditor) {
    val viewModel = tareViewModel(key = "meal-${route.mealId}") { MealEditorViewModel(it, route.mealId) }
    val graph = LocalAppGraph.current
    val nav = LocalNavigator.current
    val notify = LocalNotifier.current
    val scope = rememberCoroutineScope()

    EditorScaffold(
        title = if (viewModel.isNew) "New meal" else "Edit meal",
        onBack = { nav.back() },
        onDelete = if (viewModel.isNew) null else {
            {
                scope.launch {
                    val deleted = viewModel.delete()
                    nav.back()
                    notify.show("Meal deleted", actionLabel = "Undo") {
                        deleted?.let { graph.appScope.launch { graph.meals.restore(it) } }
                    }
                }
            }
        },
        primaryText = "Save meal",
        primaryEnabled = viewModel.canSave,
        onPrimary = {
            scope.launch {
                viewModel.save()
                nav.back()
                notify.show("Saved ${viewModel.name.trim()}")
            }
        },
    ) {
        TareTextField(viewModel.name, { viewModel.name = it }, label = "Name", placeholder = "e.g. Usual breakfast", imeAction = ImeAction.Done)
        ItemsSection(viewModel, emptyHint = "Add the foods you usually eat together. You can scale the whole meal when you log it.")
        TotalsCard(viewModel.total, caption = "Total")
    }
}

/** Ingredient list + "Add food" + picker, shared by meal and recipe editors. */
@Composable
fun ItemsSection(viewModel: ItemsEditorViewModel, emptyHint: String) {
    val recent by viewModel.recent.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    SectionHeader("Foods") {
        if (viewModel.items.isNotEmpty()) {
            Text("${viewModel.items.size}", style = Tare.type.caption, color = Tare.colors.textFaint)
        }
    }
    if (viewModel.items.isEmpty()) {
        Text(emptyHint, style = Tare.type.caption, color = Tare.colors.textMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    }
    viewModel.items.forEachIndexed { index, item ->
        ListRow(
            title = item.food.name,
            subtitle = foodSubtitle(item.food, item.grams, item.portion?.label),
            kcal = item.nutrients.kcal,
            onClick = { editingIndex = index },
            trailingAction = {
                RoundIconButton(Icons.Rounded.Close, "Remove", { viewModel.remove(index) }, size = 32.dp, iconSize = 16.dp)
            },
        )
    }
    SecondaryButton("Add food", icon = Icons.Rounded.Add, onClick = { picking = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

    if (picking) {
        FoodPickerDialog(
            search = viewModel.search,
            recent = recent,
            onPicked = {
                viewModel.add(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
    editingIndex?.let { index ->
        val item = viewModel.items.getOrNull(index) ?: return@let
        AmountSheet(
            food = item.food,
            mode = AmountMode.PICK,
            initialGrams = item.grams,
            initialPortionLabel = item.portion?.label,
            onConfirm = { result ->
                viewModel.replace(index, DraftItem(item.food, result.grams, result.portion))
                editingIndex = null
            },
            onDismiss = { editingIndex = null },
            onDelete = {
                viewModel.remove(index)
                editingIndex = null
            },
        )
    }
}

@Composable
fun TotalsCard(total: Nutrients, caption: String, extra: String? = null) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp), padding = PaddingValues(16.dp)) {
        Column {
            Text(caption, style = Tare.type.caption, color = Tare.colors.textMuted)
            KcalFigure(total.kcal)
            MacroLine(total)
            if (extra != null) Text(extra, style = Tare.type.caption, color = Tare.colors.textFaint)
        }
    }
}

@Composable
fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    primaryText: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Saving pops the screen; ignore further taps until it does so nothing is saved twice.
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Tare.colors.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, background = Color.Transparent)
            Spacer(Modifier.width(8.dp))
            Text(title, style = Tare.type.headline.copy(fontSize = Tare.type.headline.fontSize * 0.8f), color = Tare.colors.text, modifier = Modifier.weight(1f))
            if (onDelete != null) {
                RoundIconButton(Icons.Rounded.DeleteOutline, "Delete", {
                    if (!busy) {
                        busy = true
                        onDelete()
                    }
                }, tint = Tare.colors.over)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            content()
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            primaryText,
            onClick = {
                if (!busy) {
                    busy = true
                    onPrimary()
                }
            },
            enabled = primaryEnabled && !busy,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
        )
    }
}
