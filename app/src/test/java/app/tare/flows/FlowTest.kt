package app.tare.flows

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.tare.AppGraph
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.data.db.TareDatabase
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.buildSearchText
import app.tare.ui.navigation.Route
import app.tare.ui.root.TareRoot
import com.google.common.truth.Truth.assertThat
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

/**
 * The core logging flows driven through the real UI (taps, typing, the number pad, sheets)
 * against an in-memory database. If one of these breaks, logging food is broken.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class FlowTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 10, 25)
    private val now = today.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()
    private lateinit var graph: AppGraph

    private val banana = Food(
        id = "usda:banana",
        source = FoodSource.GENERIC,
        name = "Banana",
        aliases = "banaan",
        searchText = buildSearchText("Banana", null, "banaan"),
        per100 = Nutrients(89.0, 1.1, 22.8, 0.3, 2.6),
        portions = listOf(Portion("medium", 118.0), Portion("large", 136.0)),
        popularity = 98,
        createdAt = 0,
        updatedAt = 0,
    )

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        graph = AppGraph(
            ApplicationProvider.getApplicationContext(),
            now = { now },
            databaseFactory = { TareDatabase.inMemory(it) },
            httpFactory = { HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }) },
        )
        runBlocking { graph.db.foodDao().upsert(banana) }
    }

    private fun entries(): List<LogEntry> = runBlocking { graph.log.observeDay(today).first() }

    private fun SemanticsNodeInteractionsProvider.waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForEntries(count: Int) = compose.waitUntil(5_000) { entries().size == count }

    @Test
    fun searchPickAmountAndLog() {
        compose.setContent { TareRoot(graph, start = Route.LogFood(today.toEpochDay())) }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("banaan")
        compose.waitForText("Banana")
        compose.onAllNodesWithText("Banana").onFirst().performClick()

        // Defaults to one medium banana; switch to two.
        compose.waitForText("Log · 105 kcal")
        compose.onNodeWithTag("pad-2").performClick()
        compose.onNodeWithText("Log · 210 kcal").performClick()

        waitForEntries(1)
        val entry = entries().single()
        assertThat(entry.grams).isEqualTo(236.0)
        assertThat(entry.portionLabel).isEqualTo("medium")
        assertThat(entry.nutrients.kcal).isWithin(0.1).of(210.0)
        compose.waitForText("1 added")
    }

    @Test
    fun saveTypedAmountAsOwnPortion() {
        compose.setContent { TareRoot(graph, start = Route.LogFood(today.toEpochDay())) }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("banana")
        compose.waitForText("Banana")
        compose.onAllNodesWithText("Banana").onFirst().performClick()

        compose.waitForText("Log · 105 kcal")
        compose.onNodeWithText("g").performClick()
        listOf("pad-3", "pad-5", "pad-0").forEach { compose.onNodeWithTag(it).performClick() }
        compose.onNodeWithText("+ Save 350 g").performClick()
        compose.waitForText("Name 350 g, e.g. my bowl")
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("my bowl")
        compose.onNodeWithText("Save").performClick()

        compose.waitForText("my bowl · 350 g")
        compose.onNodeWithText("Log · 312 kcal").performClick()
        waitForEntries(1)
        assertThat(entries().single().portionLabel).isEqualTo("my bowl")
        val stored = runBlocking { graph.db.foodDao().get(banana.id)!! }
        assertThat(stored.portions).contains(Portion("my bowl", 350.0, custom = true))
    }

    @Test
    fun usualFoodRelogsTheUsualAmountForThatPartOfTheDay() {
        runBlocking {
            // Two bananas at lunch on two days; one in the evening most recently.
            listOf(3L, 2L).forEach { graph.log.logFood(banana, 236.0, banana.portions.first(), today.minusDays(it), LocalTime.of(12, 0)) }
            graph.log.logFood(banana, 118.0, banana.portions.first(), today.minusDays(1), LocalTime.of(19, 0))
        }
        compose.setContent { TareRoot(graph, start = Route.LogFood(today.toEpochDay())) }

        compose.waitForText("USUALLY IN THE AFTERNOON")
        compose.onNodeWithContentDescription("Log Banana").performClick()
        waitForEntries(1)
        assertThat(entries().single().grams).isEqualTo(236.0)

        // Once it's logged this afternoon it leaves the list.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("USUALLY IN THE AFTERNOON")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun oneTapRelogAndUndo() {
        runBlocking { graph.log.logFood(banana, 118.0, banana.portions.first(), today.minusDays(1)) }
        compose.setContent { TareRoot(graph, start = Route.LogFood(today.toEpochDay())) }

        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Log Banana")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Log Banana").performClick()
        waitForEntries(1)

        compose.waitForText("Undo")
        compose.onNodeWithText("Undo").performClick()
        waitForEntries(0)
    }

    @Test
    fun quickAddWithTheNumberPad() {
        compose.setContent { TareRoot(graph, start = Route.LogFood(today.toEpochDay())) }
        compose.onNodeWithText("Quick add").performClick()
        compose.waitForText("Log · 0 kcal")
        listOf("pad-4", "pad-5", "pad-0").forEach { compose.onNodeWithTag(it).performClick() }
        compose.onNodeWithText("Log · 450 kcal").performClick()

        waitForEntries(1)
        val entry = entries().single()
        assertThat(entry.source).isEqualTo(FoodSource.QUICK)
        assertThat(entry.nutrients.kcal).isEqualTo(450.0)
    }

    @Test
    fun loggingIntoAnEarlierPartOfTheDay() {
        compose.setContent { TareRoot(graph, start = Route.LogFood(today.toEpochDay())) }
        compose.onNodeWithText("Morning").performClick()
        compose.onNodeWithText("Quick add").performClick()
        listOf("pad-3", "pad-0", "pad-0").forEach { compose.onNodeWithTag(it).performClick() }
        compose.onNodeWithText("Log · 300 kcal").performClick()

        waitForEntries(1)
        val time = java.time.Instant.ofEpochMilli(entries().single().loggedAt).atZone(zone).toLocalTime()
        assertThat(time).isEqualTo(LocalTime.of(8, 0))
    }

    @Test
    fun editAnEntryFromToday() {
        runBlocking { graph.log.logFood(banana, 118.0, banana.portions.first(), today, LocalTime.of(9, 0)) }
        compose.setContent { TareRoot(graph) }
        compose.waitForText("Banana")
        compose.onNodeWithText("Banana").performClick()

        compose.waitForText("Save")
        compose.onNodeWithTag("pad-3").performClick()
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil(5_000) { entries().single().grams == 354.0 }
        assertThat(entries().single().nutrients.kcal).isWithin(0.1).of(315.06)
    }

    @Test
    fun copyYesterdayToToday() {
        runBlocking {
            graph.log.logFood(banana, 118.0, banana.portions.first(), today.minusDays(1), LocalTime.of(8, 0))
            graph.log.logQuick("Pizza", Nutrients(800.0), today.minusDays(1), LocalTime.of(19, 0))
        }
        compose.setContent { TareRoot(graph) }
        compose.waitForText("24")
        compose.onNodeWithText("24").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Copy this day to today")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Copy this day to today").performClick()

        waitForEntries(2)
        assertThat(entries().map { it.name }).containsExactly("Banana", "Pizza")
    }

    private fun isFavorite() = runBlocking { graph.db.foodDao().get(banana.id)!!.favorite }

    @Test
    fun holdAFoodInTheLogHubToStarIt() {
        runBlocking { graph.log.logFood(banana, 118.0, banana.portions.first(), today.minusDays(1)) }
        compose.setContent { TareRoot(graph, start = Route.LogFood(today.toEpochDay())) }

        compose.waitForText("Hold a food to star it")
        // The last "Banana" is the Recent row (the first is yesterday's card).
        compose.onAllNodesWithText("Banana").onLast().performTouchInput { longClick() }
        compose.waitForText("Banana added to favourites")
        compose.waitUntil(5_000) { isFavorite() }
        compose.waitForText("FAVOURITES")

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { !isFavorite() }
    }

    @Test
    fun starLoggedEntriesFromToday() {
        runBlocking { graph.log.logFood(banana, 118.0, banana.portions.first(), today, LocalTime.of(9, 0)) }
        compose.setContent { TareRoot(graph) }
        compose.waitForText("Banana")
        compose.onNodeWithText("Banana").performTouchInput { longClick() }

        compose.onNodeWithContentDescription("Add to favourites").performClick()
        compose.waitForText("Added to favourites")
        compose.waitUntil(5_000) { isFavorite() }
    }

    @Test
    fun changingCaloriesKeepsMacrosAddingUp() {
        runBlocking { graph.weights.log(today, 82.0) }
        compose.setContent { TareRoot(graph, start = Route.Settings) }
        compose.waitForText("kcal per day", substring = true)
        compose.onNodeWithText("kcal per day", substring = true).performClick()
        compose.waitForText("the rest", substring = true)

        // 1.8 g/kg protein at 82 kg and 30% fat, then calories down to 1800.
        compose.onNodeWithText("1.8 g/kg").performClick()
        compose.onNodeWithText("30%").performClick()
        listOf("pad-1", "pad-8", "pad-0", "pad-0").forEach { compose.onNodeWithTag(it).performClick() }
        compose.onNodeWithText("Save targets").performClick()

        compose.waitUntil(5_000) { runBlocking { graph.settings.targets.first() }.kcal == 1800 }
        val saved = runBlocking { graph.settings.targets.first() }
        assertThat(saved.protein).isEqualTo(150)
        assertThat(saved.fat).isEqualTo(60)
        assertThat(saved.carbs).isEqualTo(165)
    }

    @Test
    fun saveSelectedEntriesAsAMeal() {
        runBlocking { graph.log.logFood(banana, 118.0, banana.portions.first(), today, LocalTime.of(9, 0)) }
        compose.setContent { TareRoot(graph) }
        compose.waitForText("Banana")
        compose.onNodeWithText("Banana").performTouchInput { longClick() }

        compose.onNodeWithContentDescription("Save as meal").performClick()
        compose.waitForText("Save meal")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Snack")
        compose.onNodeWithText("Save meal").performClick()

        compose.waitUntil(5_000) { runBlocking { graph.meals.observeAll().first() }.isNotEmpty() }
        val meal = runBlocking { graph.meals.observeAll().first() }.single()
        assertThat(meal.meal.name).isEqualTo("Snack")
        assertThat(meal.nutrients.kcal).isWithin(0.1).of(105.02)
    }

    @Test
    fun buildAMealInTheEditor() {
        compose.setContent { TareRoot(graph, start = Route.MealEditor()) }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Fruit")
        compose.onNodeWithText("Add food").performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("banana")
        compose.waitForText("Banana")
        compose.onAllNodesWithText("Banana").onFirst().performClick()
        compose.waitForText("Add · 105 kcal")
        compose.onNodeWithText("Add · 105 kcal").performClick()

        compose.waitForText("Save meal")
        compose.onNodeWithText("Save meal").performClick()
        compose.waitUntil(5_000) { runBlocking { graph.meals.observeAll().first() }.isNotEmpty() }
        assertThat(runBlocking { graph.meals.observeAll().first() }.single().items).hasSize(1)
    }
}
