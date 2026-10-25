package app.tare.ui.root

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.tare.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import app.tare.data.settings.ThemeMode
import app.tare.ui.ai.DescribeScreen
import app.tare.ui.food.FoodEditorScreen
import app.tare.ui.home.HomeScreen
import app.tare.ui.log.LogFoodScreen
import app.tare.ui.meal.MealEditorScreen
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Navigator
import app.tare.ui.navigation.Route
import app.tare.ui.recipe.RecipeEditorScreen
import app.tare.ui.scan.ScanScreen
import app.tare.ui.settings.SettingsScreen
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import app.tare.ui.theme.TareTheme

/**
 * App UI. [start] lets screenshot tests open a specific screen; [deepLinks] carries routes from
 * launcher shortcuts and the share sheet, which are pushed on top of the current screen.
 */
@Composable
fun TareRoot(graph: AppGraph, start: Route = Route.Home, deepLinks: MutableStateFlow<Route?>? = null) {
    val theme by graph.settings.theme.collectAsState(initial = ThemeMode.SYSTEM)
    TareTheme(theme) {
        val backStack = rememberNavBackStack(start)
        val navigator = remember(backStack) { Navigator(backStack) }
        val snackbar = remember { SnackbarHostState() }
        val rootScope = rememberCoroutineScope()
        val notifier = remember(snackbar) { Notifier(snackbar, rootScope) }
        if (deepLinks != null) {
            LaunchedEffect(deepLinks, navigator) {
                deepLinks.collect { route ->
                    if (route != null) {
                        navigator.go(route)
                        deepLinks.value = null
                    }
                }
            }
        }
        CompositionLocalProvider(
            LocalAppGraph provides graph,
            LocalNavigator provides navigator,
            LocalNotifier provides notifier,
        ) {
            Box(Modifier.fillMaxSize().background(Tare.colors.background)) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { navigator.back() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    transitionSpec = {
                        (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 10 } + fadeOut(tween(180)))
                    },
                    popTransitionSpec = {
                        (slideInHorizontally(tween(260)) { -it / 10 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { it / 6 } + fadeOut(tween(180)))
                    },
                    predictivePopTransitionSpec = {
                        (slideInHorizontally(tween(260)) { -it / 10 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { it / 6 } + fadeOut(tween(180)))
                    },
                    entryProvider = entryProvider {
                        entry<Route.Home> { HomeScreen() }
                        entry<Route.LogFood> { LogFoodScreen(it) }
                        entry<Route.Scan> { ScanScreen(it) }
                        entry<Route.Describe> { DescribeScreen(it) }
                        entry<Route.FoodEditor> { FoodEditorScreen(it) }
                        entry<Route.MealEditor> { MealEditorScreen(it) }
                        entry<Route.RecipeEditor> { RecipeEditorScreen(it) }
                        entry<Route.Settings> { SettingsScreen() }
                    },
                )
                SnackbarHost(
                    snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 104.dp, start = 16.dp, end = 16.dp),
                ) { data ->
                    Snackbar(
                        shape = Radii.row,
                        containerColor = Tare.colors.text,
                        contentColor = Tare.colors.background,
                        actionContentColor = Tare.colors.kcal,
                        action = data.visuals.actionLabel?.let { label ->
                            {
                                Text(
                                    label,
                                    style = Tare.type.bodyStrong,
                                    color = Tare.colors.kcal,
                                    modifier = Modifier
                                        .clip(Radii.chip)
                                        .clickable { data.performAction() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        },
                    ) {
                        Text(data.visuals.message, style = Tare.type.bodyStrong)
                    }
                }
            }
        }
    }
}
