package app.tare.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.data.db.MealWithItems
import app.tare.domain.FoodSource
import app.tare.ui.Fmt
import app.tare.ui.components.Card
import app.tare.ui.components.FoodRow
import app.tare.ui.components.KcalFigure
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SectionHeader
import app.tare.ui.components.TareIcons
import app.tare.ui.components.TareTextField
import app.tare.ui.components.combinedClickableCompat
import app.tare.ui.components.pressScale
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Route
import app.tare.ui.navigation.tareViewModel
import app.tare.ui.root.LocalSnackbar
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun LogFoodScreen(route: Route.LogFood) {
    val day = LocalDate.ofEpochDay(route.day)
    val viewModel = tareViewModel(key = "log-${route.day}") { LogFoodViewModel(it, day) }
    val hub by viewModel.hub.collectAsState()
    val results by viewModel.search.results.collectAsState()
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<Food?>(null) }
    var pickedMeal by remember { mutableStateOf<MealWithItems?>(null) }
    var quickAdd by remember { mutableStateOf(false) }

    LaunchedEffect(query) { viewModel.setQuery(query) }
    // New results must start at the top; otherwise the list stays anchored to an old row
    // and the best match ends up scrolled out of view.
    LaunchedEffect(results.query, results.local.firstOrNull()?.id) { listState.scrollToItem(0) }
    LaunchedEffect(Unit) { if (route.focusSearch) focusRequester.requestFocus() }
    // Dismiss the keyboard once the user starts scrolling results.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (it) focus.clearFocus() }
    }

    fun announce(entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        scope.launch {
            val kcal = entries.sumOf { it.nutrients.kcal }
            val label = if (entries.size == 1) entries.first().name else "${entries.size} items"
            val result = snackbar.showSnackbar("Logged $label · ${Fmt.kcal(kcal)} kcal", actionLabel = "Undo")
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.undo(entries)
        }
    }

    fun quickLog(food: Food) {
        scope.launch { announce(listOf(viewModel.quickLog(food))) }
    }

    Column(Modifier.fillMaxSize().background(Tare.colors.background).statusBarsPadding().imePadding()) {
        // Search bar
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() }, background = Color.Transparent)
            Spacer(Modifier.width(4.dp))
            TareTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search foods & products",
                imeAction = ImeAction.Search,
                onIme = { focus.clearFocus() },
                leading = { Icon(Icons.Rounded.Search, null, tint = Tare.colors.textFaint, modifier = Modifier.size(20.dp)) },
                trailing = if (query.isNotEmpty()) {
                    { RoundIconButton(Icons.Rounded.Close, "Clear", { query = "" }, size = 28.dp, iconSize = 16.dp, background = Tare.colors.surfacePressed) }
                } else {
                    null
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            ) {
                if (!results.isSearching) {
                    item(key = "actions") {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile(TareIcons.Barcode, "Scan", Tare.colors.text, Modifier.weight(1f)) {
                                nav.go(Route.Scan(route.day))
                            }
                            ActionTile(Icons.Rounded.AutoAwesome, "Describe", Tare.colors.kcal, Modifier.weight(1f)) {
                                nav.go(Route.Describe(route.day))
                            }
                            ActionTile(Icons.Rounded.Bolt, "Quick add", Tare.colors.fat, Modifier.weight(1f)) {
                                quickAdd = true
                            }
                        }
                    }
                    if (hub.yesterday.isNotEmpty()) {
                        item(key = "h-yesterday") { SectionHeader("Same as yesterday") }
                        item(key = "yesterday") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(hub.yesterday, key = { it.part.name }) { source ->
                                    CopyCard(source) {
                                        scope.launch { announce(viewModel.copy(source)) }
                                    }
                                }
                            }
                        }
                    }
                    if (hub.meals.isNotEmpty()) {
                        item(key = "h-meals") { SectionHeader("Meals") }
                        item(key = "meals") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(hub.meals, key = { it.meal.id }) { meal ->
                                    MealCard(meal) { pickedMeal = meal }
                                }
                            }
                        }
                    }
                    if (hub.recent.isNotEmpty()) {
                        item(key = "h-recent") { SectionHeader("Recent") }
                        items(hub.recent, key = { "recent-" + it.id }) { food ->
                            FoodRow(food, onClick = { picked = food }, onQuickAdd = { quickLog(food) }, modifier = Modifier.animateItem())
                        }
                    } else {
                        item(key = "hint") {
                            Text(
                                "Search thousands of basic foods and millions of products, scan a barcode, or describe a whole meal and let Claude do the maths.",
                                style = Tare.type.body,
                                color = Tare.colors.textMuted,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp),
                            )
                        }
                    }
                } else {
                    searchResults(
                        results = results,
                        onFood = {
                            focus.clearFocus()
                            picked = it
                        },
                        onQuickAdd = { quickLog(it) },
                        onMeal = {
                            focus.clearFocus()
                            pickedMeal = it
                        },
                        onAskAi = { nav.go(Route.Describe(route.day, prefill = it)) },
                        onCreate = { nav.go(Route.FoodEditor(logDay = route.day, name = it)) },
                    )
                }
            }

            SessionBar(
                session = hub.session,
                onDone = { nav.back() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    picked?.let { food ->
        AmountSheet(
            food = food,
            mode = AmountMode.LOG,
            remainingKcal = hub.remainingKcal,
            onConfirm = { result ->
                picked = null
                scope.launch { announce(listOf(viewModel.log(food, result.grams, result.portion, result.time))) }
            },
            onDismiss = { picked = null },
            onToggleFavorite = if (food.source != FoodSource.OPEN_FOOD_FACTS || food.useCount > 0) {
                {
                    viewModel.toggleFavorite(food)
                    picked = food.copy(favorite = !food.favorite)
                }
            } else {
                null
            },
        )
    }

    pickedMeal?.let { meal ->
        MealLogSheet(
            meal = meal,
            remainingKcal = hub.remainingKcal,
            onLog = { scale ->
                pickedMeal = null
                scope.launch { announce(viewModel.logMeal(meal, scale, null)) }
            },
            onEdit = {
                pickedMeal = null
                nav.go(Route.MealEditor(meal.meal.id))
            },
            onDismiss = { pickedMeal = null },
        )
    }

    if (quickAdd) {
        QuickAddSheet(
            onConfirm = { name, nutrients ->
                quickAdd = false
                scope.launch { announce(listOf(viewModel.logQuick(name, nutrients))) }
            },
            onDismiss = { quickAdd = false },
        )
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction)
            .clip(Radii.card)
            .background(Tare.colors.surface)
            .combinedClickableCompat(interaction, onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = Tare.type.label, color = Tare.colors.text)
    }
}

@Composable
private fun MealCard(meal: MealWithItems, onClick: () -> Unit) {
    Card(Modifier.width(172.dp), padding = PaddingValues(16.dp), onClick = onClick) {
        Column {
            Text(meal.meal.name, style = Tare.type.bodyStrong, color = Tare.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Fmt.items(meal.items.size), style = Tare.type.caption, color = Tare.colors.textMuted)
            Spacer(Modifier.height(12.dp))
            KcalFigure(meal.nutrients.kcal)
        }
    }
}

@Composable
private fun CopyCard(source: CopySource, onClick: () -> Unit) {
    Card(Modifier.width(172.dp), padding = PaddingValues(16.dp), onClick = onClick) {
        Column {
            Text(source.part.label, style = Tare.type.bodyStrong, color = Tare.colors.text)
            Text(
                source.entries.map { it.groupName ?: it.name }.distinct().joinToString(", "),
                style = Tare.type.caption,
                color = Tare.colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
            )
            Spacer(Modifier.height(10.dp))
            KcalFigure(source.kcal)
        }
    }
}

@Composable
private fun SessionBar(session: List<LogEntry>, onDone: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = session.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Tare.colors.background, Tare.colors.background)))
                .navigationBarsPadding()
                .padding(16.dp)
                .clip(Radii.pill)
                .background(Tare.colors.surfaceRaised)
                .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${session.size} added", style = Tare.type.bodyStrong, color = Tare.colors.text)
                Text("${Fmt.kcal(session.sumOf { it.nutrients.kcal })} kcal", style = Tare.type.caption, color = Tare.colors.textMuted)
            }
            PrimaryButton("Done", onClick = onDone)
        }
    }
}
