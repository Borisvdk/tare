package app.tare.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.tare.AppGraph
import app.tare.data.db.Food
import app.tare.data.db.TareDatabase
import app.tare.data.health.FakeHealthSource
import app.tare.data.meal.DraftItem
import app.tare.data.settings.ThemeMode
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.Targets
import app.tare.domain.buildSearchText
import app.tare.ui.ai.DescribeState
import app.tare.ui.ai.EstimateResult
import app.tare.ui.ai.EstimateRow
import app.tare.ui.library.LibraryScreen
import app.tare.ui.log.AmountMode
import app.tare.ui.log.AmountSheet
import androidx.navigation3.runtime.rememberNavBackStack
import app.tare.ui.navigation.LocalAppGraph
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Navigator
import app.tare.ui.navigation.Route
import app.tare.ui.progress.ProgressScreen
import app.tare.ui.root.TareRoot
import app.tare.ui.settings.TargetsSheet
import app.tare.ui.theme.Tare
import app.tare.ui.theme.TareTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import kotlin.math.sin

/**
 * Renders real screens with seeded data. `./gradlew recordRoborazziDebug` writes the PNGs to
 * app/src/test/screenshots so you can look at UI changes without a device; plain
 * `testDebugUnitTest` still renders them, which catches crashes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 10, 25)
    private val now = today.atTime(19, 30).atZone(zone).toInstant().toEpochMilli()
    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        graph = AppGraph(
            ApplicationProvider.getApplicationContext(),
            now = { now },
            databaseFactory = { TareDatabase.inMemory(it) },
            httpFactory = { HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }) },
            // A watch's calories burned for the last two weeks, for Progress.
            healthFactory = { FakeHealthSource(burned = (1L..14L).associate { today.minusDays(it) to 2450 + 220 * sin(it * 1.3) }) },
        )
    }

    private fun food(id: String, name: String, per100: Nutrients, portions: List<Portion> = emptyList(), brand: String? = null, source: FoodSource = FoodSource.GENERIC) =
        Food(id, source, name, brand, searchText = buildSearchText(name, brand, null), per100 = per100, portions = portions, createdAt = 0, updatedAt = 0)

    private val oats = food("usda:oats", "Rolled oats", Nutrients(379.0, 13.2, 67.7, 6.5, 10.1), listOf(Portion("cup", 81.0)))
    private val skyr = food("off:1", "Skyr naturel", Nutrients(63.0, 11.0, 4.0, 0.2), listOf(Portion("serving", 150.0)), brand = "Arla", source = FoodSource.OPEN_FOOD_FACTS)
    private val banana = food("usda:banana", "Banana", Nutrients(89.0, 1.1, 22.8, 0.3, 2.6), listOf(Portion("medium", 118.0), Portion("small", 101.0), Portion("large", 136.0)))
    private val chicken = food("usda:chicken", "Chicken breast, roasted", Nutrients(165.0, 31.0, 0.0, 3.6))
    private val rice = food("usda:rice", "White rice, cooked", Nutrients(130.0, 2.7, 28.2, 0.3, 0.4))
    private val broccoli = food("usda:broccoli", "Broccoli, boiled", Nutrients(35.0, 2.4, 7.2, 0.4, 3.3))
    private val pb = food("usda:pb", "Peanut butter", Nutrients(588.0, 25.0, 20.0, 50.0, 6.0), listOf(Portion("tbsp", 16.0)))

    private fun seed(theme: ThemeMode, withDiary: Boolean = true) = runBlocking {
        graph.settings.setTheme(theme)
        graph.settings.setTargets(Targets(2300, 160, 240, 75))
        graph.db.foodDao().upsertAll(listOf(oats, skyr, banana, chicken, rice, broccoli, pb))
        if (!withDiary) return@runBlocking
        val log = graph.log
        log.logFood(oats, 60.0, null, today, LocalTime.of(8, 5))
        log.logFood(skyr, 150.0, skyr.portions.first(), today, LocalTime.of(8, 5))
        log.logFood(banana, 118.0, banana.portions.first(), today, LocalTime.of(10, 40))
        val dinner = graph.meals.save(null, "Chicken rice bowl", listOf(DraftItem(chicken, 160.0), DraftItem(rice, 220.0), DraftItem(broccoli, 120.0)))
        log.logMeal(graph.meals.get(dinner)!!, 1.0, today, LocalTime.of(18, 45))
        log.logFood(pb, 32.0, pb.portions.first(), today, LocalTime.of(15, 10))
        // Two weeks of history for the week strip and charts.
        (1L..13L).forEach { back ->
            val day = today.minusDays(back)
            if (back % 6 != 0L) log.logQuick("Day total", Nutrients(1900.0 + 350 * sin(back.toDouble()), 150.0, 210.0, 70.0), day)
            // An evening habit, for "Usually in the evening".
            if (back <= 3L) log.logFood(skyr, 200.0, null, day, LocalTime.of(20, 15))
        }
        (0L..70L).forEach { back ->
            if (back % 3 != 1L) graph.weights.log(today.minusDays(back), 79.8 - (70 - back) * 0.035 + 0.6 * sin(back * 1.7))
        }
    }

    private fun shoot(name: String, waitFor: String? = null, content: @Composable () -> Unit) {
        compose.setContent(content)
        if (waitFor != null) compose.waitUntil(5_000) { compose.onAllNodes(hasText(waitFor, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun todayLight() {
        seed(ThemeMode.LIGHT)
        shoot("today_light", waitFor = "Rolled oats") { TareRoot(graph) }
    }

    @Test fun todayDark() {
        seed(ThemeMode.DARK)
        shoot("today_dark", waitFor = "Rolled oats") { TareRoot(graph) }
    }

    @Test fun todayEmpty() {
        seed(ThemeMode.DARK, withDiary = false)
        shoot("today_empty_dark", waitFor = "Nothing logged") { TareRoot(graph) }
    }

    @Test fun logHub() {
        seed(ThemeMode.DARK)
        shoot("log_hub_dark", waitFor = "Rolled oats") { TareRoot(graph, start = Route.LogFood(today.toEpochDay())) }
    }

    @Test fun amountSheet() {
        seed(ThemeMode.DARK, withDiary = false)
        shoot("amount_sheet_dark", waitFor = "Log ·") {
            TareTheme(ThemeMode.DARK) {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    AmountSheet(food = banana, mode = AmountMode.LOG, remainingKcal = 1240.0, onConfirm = {}, onDismiss = {}, onToggleFavorite = {})
                }
            }
        }
    }

    @Test fun amountSheetFromScanner() {
        seed(ThemeMode.DARK, withDiary = false)
        shoot("amount_sheet_scan_dark", waitFor = "Log & next") {
            TareTheme(ThemeMode.DARK) {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    AmountSheet(food = skyr, mode = AmountMode.LOG, remainingKcal = 1240.0, onConfirm = {}, onDismiss = {}, onConfirmAndNext = {})
                }
            }
        }
    }

    @Test fun progress() {
        seed(ThemeMode.DARK)
        shoot("progress_dark", waitFor = "kg") { tab(ThemeMode.DARK) { ProgressScreen(PaddingValues(bottom = 24.dp)) } }
    }

    @Test fun library() {
        seed(ThemeMode.LIGHT)
        shoot("library_light", waitFor = "Chicken rice bowl") { tab(ThemeMode.LIGHT) { LibraryScreen(PaddingValues(bottom = 24.dp)) } }
    }

    /** A home tab on its own, without the bottom bar. */
    @Composable
    private fun tab(theme: ThemeMode, content: @Composable () -> Unit) {
        TareTheme(theme) {
            val backStack = rememberNavBackStack(Route.Home)
            CompositionLocalProvider(LocalAppGraph provides graph, LocalNavigator provides Navigator(backStack)) {
                Box(Modifier.fillMaxSize().background(Tare.colors.background)) { content() }
            }
        }
    }

    @Test fun targetsSheet() {
        seed(ThemeMode.DARK)
        shoot("targets_sheet_dark", waitFor = "the rest") { tab(ThemeMode.DARK) { TargetsSheet(onDismiss = {}) } }
    }

    @Test fun settings() {
        seed(ThemeMode.DARK)
        shoot("settings_dark", waitFor = "kcal per day") { TareRoot(graph, start = Route.Settings) }
    }

    @Test fun describeResult() {
        seed(ThemeMode.DARK, withDiary = false)
        fun ai(key: Int, name: String, grams: Double, per100: Nutrients, low: Boolean = false) =
            EstimateRow(key, food("ai:$key", name, per100, source = FoodSource.AI), grams, low)
        val state = DescribeState.Ready(
            title = "Breakfast",
            note = "Assumed medium eggs fried in a little butter.",
            rows = listOf(
                ai(0, "Fried egg", 100.0, Nutrients(196.0, 13.6, 0.8, 15.0)),
                ai(1, "Volkoren brood", 70.0, Nutrients(247.0, 10.0, 41.0, 3.4, 7.0)),
                ai(2, "Peanut butter", 16.0, Nutrients(588.0, 25.0, 20.0, 50.0, 6.0)),
                ai(3, "Flat white", 240.0, Nutrients(45.0, 2.6, 3.8, 2.2), low = true),
            ),
        )
        shoot("describe_result_dark", waitFor = "Log all") {
            tab(ThemeMode.DARK) {
                Box(Modifier.padding(16.dp)) { EstimateResult(state, onEdit = {}, onRemove = {}, onLogAll = {}) }
            }
        }
    }

    @Test fun mealEditor() {
        seed(ThemeMode.LIGHT)
        val id = runBlocking { graph.meals.observeAll().first().first().meal.id }
        shoot("meal_editor_light", waitFor = "Broccoli") { TareRoot(graph, start = Route.MealEditor(id)) }
    }

    /** A many-ingredient meal from Describe with Claude, logged as one group. */
    private fun seedAiMeal() = runBlocking {
        fun ai(name: String, per100: Nutrients, grams: Double) =
            Triple(food("ai:${name.lowercase()}", name, per100, source = FoodSource.AI), grams, null)
        val items = listOf(
            ai("Penne, cooked", Nutrients(158.0, 5.8, 30.9, 0.9, 1.8), 250.0),
            ai("Chicken thigh, grilled", Nutrients(177.0, 24.0, 0.0, 8.6), 150.0),
            ai("Green pesto", Nutrients(450.0, 5.0, 6.0, 45.0, 2.0), 40.0),
            ai("Parmesan", Nutrients(392.0, 36.0, 3.2, 26.0), 15.0),
            ai("Cherry tomatoes", Nutrients(18.0, 0.9, 3.9, 0.2, 1.2), 80.0),
            ai("Spinach", Nutrients(23.0, 2.9, 3.6, 0.4, 2.2), 40.0),
            ai("Olive oil", Nutrients(884.0, 0.0, 0.0, 100.0), 10.0),
            ai("Pine nuts", Nutrients(673.0, 13.7, 13.1, 68.4, 3.7), 10.0),
        )
        graph.db.foodDao().upsertAll(items.map { it.first })
        graph.log.logBatch(items, today, "Pesto pasta with chicken", LocalTime.of(13, 10))
    }

    @Test fun mealBreakdown() {
        seed(ThemeMode.DARK)
        seedAiMeal()
        compose.setContent { TareRoot(graph) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Rolled oats")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Chicken rice bowl"))
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/today_meal_dark.png")
        compose.onNode(hasText("Pesto pasta with chicken")).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("% protein", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/breakdown_meal_dark.png")
    }

    @Test fun dayBreakdownByProtein() {
        seed(ThemeMode.LIGHT)
        seedAiMeal()
        compose.setContent { TareRoot(graph) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Rolled oats")).fetchSemanticsNodes().isNotEmpty() }
        // The hero's protein bar opens the day ranked by protein.
        compose.onAllNodes(hasText("Protein")).onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("% protein", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/breakdown_day_light.png")
    }
}
