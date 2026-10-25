package app.tare.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.domain.FoodSource
import app.tare.ui.Fmt
import app.tare.ui.components.CalorieGauge
import app.tare.ui.components.Card
import app.tare.ui.components.EmptyState
import app.tare.ui.components.KcalFigure
import app.tare.ui.components.ListRow
import app.tare.ui.components.MacroBars
import app.tare.ui.components.MiniRing
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SectionHeader
import app.tare.ui.components.TareSheet
import app.tare.ui.components.TareTextField
import app.tare.ui.components.TextAction
import app.tare.ui.log.AmountMode
import app.tare.ui.log.AmountSheet
import app.tare.ui.log.QuickAddSheet
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Route
import app.tare.ui.root.LocalNotifier
import app.tare.ui.settings.TargetsSheet
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private const val WEEK_PAGES = 520
private val HEADER_HEIGHT = 64.dp

@Composable
fun TodayScreen(viewModel: TodayViewModel, onDayChange: (LocalDate) -> Unit, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsState()
    val nav = LocalNavigator.current
    val notify = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var selected by remember { mutableStateOf(setOf<Long>()) }
    var expandedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<Pair<LogEntry, Food?>?>(null) }
    var showTargets by remember { mutableStateOf(false) }
    var savingMeal by remember { mutableStateOf<List<LogEntry>?>(null) }
    var retiming by remember { mutableStateOf<List<LogEntry>?>(null) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.day) {
        onDayChange(state.day)
        selected = emptySet()
    }

    fun deleteWithUndo(entries: List<LogEntry>) {
        scope.launch {
            val removed = viewModel.delete(entries)
            val label = if (removed.size == 1) removed.first().name else Fmt.items(removed.size)
            notify.show("Removed $label", actionLabel = "Undo") { viewModel.restore(removed) }
        }
    }

    fun openEntry(entry: LogEntry) {
        scope.launch {
            val food = entry.foodId?.let { viewModel.food(it) }
            editing = entry to food
        }
    }

    val selectedEntries = state.entries.filter { it.id in selected }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 0.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item(key = "header") {
            AnimatedContent(targetState = selected.isNotEmpty(), label = "header") { selecting ->
                if (selecting) {
                    SelectionBar(
                        count = selected.size,
                        canCopyToToday = state.day != state.today,
                        onClose = { selected = emptySet() },
                        onSaveMeal = { savingMeal = selectedEntries },
                        onCopy = {
                            viewModel.copyToToday(selectedEntries)
                            selected = emptySet()
                            notify.show("Copied to today")
                        },
                        onRetime = { retiming = selectedEntries },
                        onDelete = {
                            deleteWithUndo(selectedEntries)
                            selected = emptySet()
                        },
                    )
                } else {
                    DayHeader(
                        day = state.day,
                        today = state.today,
                        canCopy = state.entries.isNotEmpty(),
                        onToday = viewModel::goToToday,
                        onCopyToToday = {
                            scope.launch {
                                val copies = viewModel.copyDayToToday()
                                notify.show("Copied ${Fmt.items(copies.size)} to today", actionLabel = "Undo") {
                                    viewModel.undoCopy(copies)
                                }
                            }
                        },
                        onSettings = { nav.go(Route.Settings) },
                    )
                }
            }
        }
        item(key = "week") {
            WeekStrip(
                selected = state.day,
                today = state.today,
                history = state.history,
                target = state.targets.kcal,
                onSelect = viewModel::selectDay,
            )
        }
        item(key = "hero") {
            HeroCard(state)
        }
        if (!state.targetsConfirmed && state.loaded) {
            item(key = "setup") {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp), onClick = { showTargets = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Set your daily targets", style = Tare.type.title, color = Tare.colors.text)
                            Text(
                                "Using ${Fmt.kcal(state.targets.kcal)} kcal and ${state.targets.protein} g protein until you do.",
                                style = Tare.type.caption,
                                color = Tare.colors.textMuted,
                            )
                        }
                        Icon(Icons.Rounded.Tune, null, tint = Tare.colors.kcal)
                    }
                }
            }
        }

        if (state.loaded && state.entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = if (state.day == state.today) "Nothing logged yet" else "Nothing logged",
                    body = "Tap + to add food. Hold it to scan a barcode.",
                )
            }
        }

        state.sections.forEach { section ->
            item(key = "section-${section.part}") {
                SectionHeader(section.part.label) {
                    Text("${Fmt.kcal(section.kcal)} kcal", style = Tare.type.caption, color = Tare.colors.textFaint)
                    Spacer(Modifier.width(8.dp))
                    RoundIconButton(
                        Icons.Rounded.Add,
                        "Add to ${section.part.label.lowercase()}",
                        { nav.go(Route.LogFood(state.day.toEpochDay(), part = section.part)) },
                        size = 28.dp,
                        iconSize = 16.dp,
                    )
                }
            }
            sectionItems(
                section = section,
                selected = selected,
                expandedGroups = expandedGroups,
                onToggleGroup = { id -> expandedGroups = if (id in expandedGroups) expandedGroups - id else expandedGroups + id },
                onClickEntry = { entry ->
                    if (selected.isNotEmpty()) {
                        selected = if (entry.id in selected) selected - entry.id else selected + entry.id
                    } else {
                        openEntry(entry)
                    }
                },
                onLongPress = { entries ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val ids = entries.map { it.id }.toSet()
                    selected = if (selected.containsAll(ids)) selected - ids else selected + ids
                },
                onSwipeDelete = { deleteWithUndo(listOf(it)) },
            )
        }
    }

    editing?.let { (entry, food) ->
        if (food != null && entry.grams != null) {
            AmountSheet(
                food = food,
                mode = AmountMode.EDIT,
                initialGrams = entry.grams,
                initialPortionLabel = entry.portionLabel,
                initialTime = Instant.ofEpochMilli(entry.loggedAt).atZone(ZoneId.systemDefault()).toLocalTime(),
                onConfirm = { result ->
                    viewModel.updateAmount(entry, result.grams, result.portion, result.time)
                    editing = null
                },
                onDismiss = { editing = null },
                onDelete = {
                    editing = null
                    deleteWithUndo(listOf(entry))
                },
                onToggleFavorite = {
                    viewModel.toggleFavorite(food)
                    editing = entry to food.copy(favorite = !food.favorite)
                },
                onEditFood = if (food.hidden) {
                    null
                } else if (food.source == FoodSource.CUSTOM || food.source == FoodSource.OPEN_FOOD_FACTS) {
                    {
                        editing = null
                        nav.go(Route.FoodEditor(foodId = food.id))
                    }
                } else if (food.source == FoodSource.RECIPE) {
                    {
                        editing = null
                        nav.go(Route.RecipeEditor(food.id))
                    }
                } else {
                    null
                },
            )
        } else {
            QuickAddSheet(
                initialName = entry.name,
                initial = entry.nutrients,
                onConfirm = { name, nutrients ->
                    viewModel.updateQuick(entry, name, nutrients)
                    editing = null
                },
                onDismiss = { editing = null },
                onDelete = {
                    editing = null
                    deleteWithUndo(listOf(entry))
                },
            )
        }
    }

    if (showTargets) {
        TargetsSheet(onDismiss = { showTargets = false })
    }

    savingMeal?.let { entries ->
        SaveMealSheet(
            suggestedName = entries.firstNotNullOfOrNull { it.groupName } ?: "",
            entries = entries,
            onDismiss = { savingMeal = null },
            onSave = { name ->
                scope.launch {
                    viewModel.saveAsMeal(name, entries)
                    savingMeal = null
                    selected = emptySet()
                    notify.show("Saved “$name” to your meals")
                }
            },
        )
    }

    retiming?.let { entries ->
        val first = Instant.ofEpochMilli(entries.first().loggedAt).atZone(ZoneId.systemDefault()).toLocalTime()
        val timeState = rememberTimePickerState(first.hour, first.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { retiming = null },
            confirmButton = {
                TextAction("Set", onClick = {
                    viewModel.setTime(entries, java.time.LocalTime.of(timeState.hour, timeState.minute))
                    retiming = null
                    selected = emptySet()
                })
            },
            dismissButton = { TextAction("Cancel", onClick = { retiming = null }, color = Tare.colors.textMuted) },
            title = { Text("Time eaten", style = Tare.type.title) },
        ) { TimePicker(timeState) }
    }
}

private fun LazyListScope.sectionItems(
    section: DaySection,
    selected: Set<Long>,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    onClickEntry: (LogEntry) -> Unit,
    onLongPress: (List<LogEntry>) -> Unit,
    onSwipeDelete: (LogEntry) -> Unit,
) {
    section.items.forEach { item ->
        when (item) {
            is DayItem.Single -> item(key = "${section.part}-${item.key}") {
                SwipeToDelete(onDelete = { onSwipeDelete(item.entry) }, modifier = Modifier.animateItem()) {
                    EntryRow(
                        entry = item.entry,
                        selected = item.entry.id in selected,
                        onClick = { onClickEntry(item.entry) },
                        onLongClick = { onLongPress(listOf(item.entry)) },
                    )
                }
            }
            is DayItem.Group -> {
                val expanded = item.groupId in expandedGroups
                item(key = "${section.part}-${item.key}") {
                    val allSelected = item.entries.all { it.id in selected }
                    ListRow(
                        title = item.name,
                        subtitle = Fmt.items(item.entries.size),
                        kcal = item.kcal,
                        selected = allSelected,
                        modifier = Modifier.animateItem(),
                        trailingAction = {
                            Icon(
                                Icons.Rounded.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = Tare.colors.textFaint,
                                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                            )
                        },
                        onClick = {
                            if (selected.isNotEmpty()) onLongPress(item.entries) else onToggleGroup(item.groupId)
                        },
                        onLongClick = { onLongPress(item.entries) },
                    )
                }
                item(key = "${section.part}-${item.key}-children") {
                    AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.padding(start = 16.dp)) {
                            item.entries.forEach { entry ->
                                EntryRow(
                                    entry = entry,
                                    selected = entry.id in selected,
                                    onClick = { onClickEntry(entry) },
                                    onLongClick = { onLongPress(listOf(entry)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LogEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    ListRow(
        title = entry.name,
        subtitle = listOfNotNull(entry.brand, Fmt.entryAmount(entry)).joinToString(" · "),
        kcal = entry.nutrients.kcal,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(state) {
        snapshotFlow { state.currentValue }.collect { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                state.snapTo(SwipeToDismissBoxValue.Settled)
            }
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(Radii.row)
                    .background(Tare.colors.over.copy(alpha = 0.16f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = Tare.colors.over)
            }
        },
    ) {
        // Square, opaque backing: rounded corners here let the red delete layer peek through
        // between rows when nothing is being swiped.
        Box(Modifier.background(Tare.colors.background)) { content() }
    }
}

@Composable
private fun DayHeader(
    day: LocalDate,
    today: LocalDate,
    canCopy: Boolean,
    onToday: () -> Unit,
    onCopyToToday: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 8.dp, start = 4.dp).height(HEADER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.dayHeadline(day, today), style = Tare.type.headline, color = Tare.colors.text)
            Text(Fmt.daySubtitle(day), style = Tare.type.caption, color = Tare.colors.textMuted)
        }
        if (day != today) {
            if (canCopy && day.isBefore(today)) {
                RoundIconButton(Icons.Rounded.ContentCopy, "Copy this day to today", onCopyToToday, background = Tare.colors.surface)
                Spacer(Modifier.width(8.dp))
            }
            TextAction("Today", onClick = onToday)
            Spacer(Modifier.width(4.dp))
        }
        RoundIconButton(Icons.Rounded.Tune, "Settings", onSettings, background = Tare.colors.surface)
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    canCopyToToday: Boolean,
    onClose: () -> Unit,
    onSaveMeal: () -> Unit,
    onCopy: () -> Unit,
    onRetime: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 8.dp).height(HEADER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundIconButton(Icons.Rounded.Close, "Clear selection", onClose, background = Tare.colors.surface)
        Text("$count selected", style = Tare.type.title, color = Tare.colors.text, modifier = Modifier.weight(1f))
        RoundIconButton(Icons.Rounded.BookmarkAdd, "Save as meal", onSaveMeal)
        if (canCopyToToday) RoundIconButton(Icons.Rounded.ContentCopy, "Copy to today", onCopy)
        RoundIconButton(Icons.Rounded.Schedule, "Change time", onRetime)
        RoundIconButton(Icons.Rounded.DeleteOutline, "Delete", onDelete, tint = Tare.colors.over)
    }
}

@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    history: Map<Long, Double>,
    target: Int,
    onSelect: (LocalDate) -> Unit,
) {
    val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun pageOf(day: LocalDate): Int {
        val weekStart = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (WEEK_PAGES - 1 - ChronoUnit.WEEKS.between(weekStart, currentWeekStart).toInt()).coerceIn(0, WEEK_PAGES - 1)
    }
    val pager = rememberPagerState(initialPage = pageOf(selected)) { WEEK_PAGES }
    LaunchedEffect(selected) {
        val page = pageOf(selected)
        if (pager.currentPage != page) pager.animateScrollToPage(page)
    }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { page ->
        val weekStart = currentWeekStart.minusWeeks((WEEK_PAGES - 1 - page).toLong())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0 until 7).forEach { offset ->
                val day = weekStart.plusDays(offset.toLong())
                DayCell(
                    day = day,
                    isSelected = day == selected,
                    isToday = day == today,
                    isFuture = day.isAfter(today),
                    fraction = (history[day.toEpochDay()] ?: 0.0).let { if (target > 0) (it / target).toFloat() else 0f },
                    onClick = { onSelect(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    fraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tare.colors
    Column(
        modifier
            .padding(horizontal = 2.dp)
            .clip(Radii.row)
            .background(if (isSelected) colors.surface else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            Fmt.weekdayLetter(day),
            style = Tare.type.caption,
            color = if (isToday) colors.kcal else colors.textFaint,
        )
        Spacer(Modifier.height(6.dp))
        MiniRing(fraction = if (isFuture) 0f else fraction, size = 32.dp, stroke = 3.dp) {
            Text(
                day.dayOfMonth.toString(),
                style = Tare.type.label,
                color = when {
                    isFuture -> colors.textFaint
                    isSelected -> colors.text
                    else -> colors.textMuted
                },
            )
        }
    }
}

@Composable
private fun HeroCard(state: TodayState) {
    val colors = Tare.colors
    val remaining = state.remaining
    Card(Modifier.fillMaxWidth().padding(top = 4.dp), padding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 22.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CalorieGauge(eaten = state.totals.kcal, target = state.targets.kcal) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Fmt.kcal(kotlin.math.abs(remaining)),
                        style = Tare.type.hero,
                        color = if (remaining < 0) colors.over else colors.text,
                    )
                    Text(
                        if (remaining < 0) "kcal over" else "kcal left",
                        style = Tare.type.label,
                        color = colors.textMuted,
                    )
                }
            }
            Row(
                Modifier.offset(y = (-18).dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KcalFigure(state.totals.kcal)
                Text("of ${Fmt.kcal(state.targets.kcal)}", style = Tare.type.caption, color = colors.textFaint, textAlign = TextAlign.Center)
            }
            MacroBars(state.totals, state.targets)
        }
    }
}

@Composable
private fun SaveMealSheet(
    suggestedName: String,
    entries: List<LogEntry>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    TareSheet(onDismiss = onDismiss) {
        Text("Save as meal", style = Tare.type.title, color = Tare.colors.text)
        Text(
            "${Fmt.items(entries.size)} · ${Fmt.kcal(entries.sumOf { it.nutrients.kcal })} kcal. Log them together with one tap.",
            style = Tare.type.caption,
            color = Tare.colors.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        TareTextField(value = name, onValueChange = { name = it }, placeholder = "e.g. Usual breakfast", onIme = {
            if (name.isNotBlank()) onSave(name.trim())
        })
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Save meal", onClick = { onSave(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(4.dp))
    }
}
