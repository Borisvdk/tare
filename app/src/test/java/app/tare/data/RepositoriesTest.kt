package app.tare.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.tare.data.backup.BackupManager
import app.tare.data.db.Food
import app.tare.data.db.TareDatabase
import app.tare.data.food.FoodRepository
import app.tare.data.food.OpenFoodFactsClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import app.tare.data.log.LogRepository
import app.tare.data.meal.DraftItem
import app.tare.data.meal.MealRepository
import app.tare.data.meal.RecipeRepository
import app.tare.data.settings.SettingsRepository
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.Targets
import app.tare.domain.buildSearchText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class RepositoriesTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: TareDatabase
    private var clock = 1_760_000_000_000L
    private val now = { clock }
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val day = LocalDate.of(2026, 10, 25)

    private lateinit var log: LogRepository
    private lateinit var meals: MealRepository
    private lateinit var recipes: RecipeRepository

    private val rice = food("usda:rice", "White rice, cooked", Nutrients(130.0, 2.7, 28.0, 0.3, 0.4))
    private val chicken = food("usda:chicken", "Chicken breast, roasted", Nutrients(165.0, 31.0, 0.0, 3.6), listOf(Portion("breast", 172.0)))

    private fun food(id: String, name: String, per100: Nutrients, portions: List<Portion> = emptyList()) = Food(
        id = id,
        source = FoodSource.GENERIC,
        name = name,
        searchText = buildSearchText(name, null, null),
        per100 = per100,
        portions = portions,
        createdAt = 0,
        updatedAt = 0,
    )

    @Before
    fun setUp() = runTest {
        db = TareDatabase.inMemory(ApplicationProvider.getApplicationContext())
        db.foodDao().upsertAll(listOf(rice, chicken))
        log = LogRepository(db, now) { zone }
        meals = MealRepository(db, now)
        recipes = RecipeRepository(db, now)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun loggingSnapshotsNutrientsAndUpdatesUsage() = runTest {
        val entry = log.logFood(rice, 200.0, null, day, LocalTime.of(12, 30))
        assertThat(entry.nutrients.kcal).isWithin(0.01).of(260.0)
        assertThat(log.observeDay(day).first()).hasSize(1)

        val used = db.foodDao().get(rice.id)!!
        assertThat(used.useCount).isEqualTo(1)
        assertThat(used.lastGrams).isEqualTo(200.0)

        // Editing the food afterwards must not rewrite history.
        db.foodDao().upsert(rice.copy(per100 = Nutrients(999.0)))
        assertThat(log.observeDay(day).first().single().nutrients.kcal).isWithin(0.01).of(260.0)
    }

    @Test
    fun updatingAmountScalesTheSnapshot() = runTest {
        val entry = log.logFood(chicken, 172.0, chicken.portions.first(), day)
        log.updateAmount(entry, 86.0, chicken.portions.first())
        val updated = log.get(entry.id)!!
        assertThat(updated.nutrients.protein).isWithin(0.01).of(26.66)
        assertThat(updated.portionLabel).isEqualTo("breast")
    }

    @Test
    fun editingAnOldEntryIgnoresLaterChangesToTheFood() = runTest {
        val entry = log.logFood(rice, 200.0, null, day) // 260 kcal
        db.foodDao().upsert(rice.copy(per100 = Nutrients(200.0))) // e.g. re-estimated or edited
        log.updateAmount(log.get(entry.id)!!, 100.0, null)
        assertThat(log.get(entry.id)!!.nutrients.kcal).isWithin(0.01).of(130.0)
    }

    @Test
    fun searchMatchesEveryTokenEvenWhenOneIsCommon() = runTest {
        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })), now)
        // 450 popular "cooked" foods would have pushed the beans out of a 400-row candidate list.
        db.foodDao().upsertAll(
            (1..450).map { i -> food("usda:c$i", "Something $i, cooked", Nutrients(100.0)).copy(popularity = 50) } +
                food("usda:beans", "Beans, navy, mature seeds, cooked, boiled", Nutrients(140.0)),
        )
        assertThat(foods.searchLocal("beans cooked").map { it.id }).contains("usda:beans")
    }

    @Test
    fun customPortionsPersistThroughProductRefreshes() = runTest {
        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })), now)
        val product = food("off:42", "Skyr", Nutrients(60.0, 11.0)).copy(source = FoodSource.OPEN_FOOD_FACTS, portions = listOf(Portion("serving", 150.0)))
        // Adding a portion to a product that only came from search saves it first.
        foods.addPortion(product, Portion("my bowl", 250.0))
        // A later refresh from Open Food Facts must not drop it.
        foods.saveRemote(product)
        assertThat(db.foodDao().get("off:42")!!.portions)
            .containsExactly(Portion("serving", 150.0), Portion("my bowl", 250.0, custom = true))
        // And it shows up in backups even if the food was never logged.
        assertThat(db.foodDao().allForBackup().map { it.id }).contains("off:42")
    }

    @Test
    fun genericFoodsWithOwnPortionsAreKeptForReimport() = runTest {
        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })), now)
        foods.addPortion(rice, Portion("my bowl", 300.0))
        assertThat(db.foodDao().usedGeneric().map { it.id }).contains(rice.id)
    }

    @Test
    fun correctedProductsResolveToTheFork() = runTest {
        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })), now)
        val product = food("off:871", "Kwark", Nutrients(50.0)).copy(source = FoodSource.OPEN_FOOD_FACTS, barcode = "871")
        db.foodDao().upsert(product)
        val fork = foods.saveCustom(product.copy(per100 = Nutrients(60.0, 10.0)))
        assertThat(fork.id).startsWith("custom:")
        assertThat(db.foodDao().get(product.id)!!.hidden).isTrue()
        assertThat(foods.resolve(product.id)!!.id).isEqualTo(fork.id)
        assertThat(foods.lookupBarcode("871")).isEqualTo(app.tare.data.food.BarcodeResult.Found("871", db.foodDao().get(fork.id)!!))
    }

    @Test
    fun deleteAndRestoreRoundTrips() = runTest {
        val entry = log.logFood(rice, 100.0, null, day)
        val removed = log.delete(listOf(entry))
        assertThat(log.observeDay(day).first()).isEmpty()
        log.restore(removed)
        assertThat(log.observeDay(day).first().map { it.id }).containsExactly(entry.id)
    }

    @Test
    fun mealsLogAsAScaledGroup() = runTest {
        val id = meals.save(null, "Rice bowl", listOf(DraftItem(rice, 200.0), DraftItem(chicken, 150.0)))
        val meal = meals.get(id)!!
        assertThat(meal.nutrients.kcal).isWithin(0.01).of(260.0 + 247.5)

        val entries = log.logMeal(meal, scale = 0.5, day = day)
        assertThat(entries).hasSize(2)
        assertThat(entries.map { it.groupId }.distinct()).hasSize(1)
        assertThat(entries.sumOf { it.nutrients.kcal }).isWithin(0.01).of((260.0 + 247.5) / 2)
        assertThat(meals.get(id)!!.meal.useCount).isEqualTo(1)
        assertThat(meals.search("bowl").map { it.meal.id }).containsExactly(id)
    }

    @Test
    fun deletedMealsCanBeRestored() = runTest {
        val id = meals.save(null, "Rice bowl", listOf(DraftItem(rice, 200.0), DraftItem(chicken, 150.0)))
        val snapshot = meals.delete(id)!!
        assertThat(meals.get(id)).isNull()
        meals.restore(snapshot)
        val restored = meals.get(id)!!
        assertThat(restored.items).hasSize(2)
        assertThat(restored.nutrients.kcal).isWithin(0.01).of(260.0 + 247.5)
    }

    @Test
    fun quickAddEntriesSurviveBeingSavedAsMeal() = runTest {
        val quick = log.logQuick("Pizza slice", Nutrients(285.0, 12.0, 36.0, 10.0), day)
        val riceEntry = log.logFood(rice, 150.0, null, day)
        val id = meals.saveFromEntries("Friday", listOf(quick, riceEntry))
        val meal = meals.get(id)!!
        assertThat(meal.items).hasSize(2)
        assertThat(meal.nutrients.kcal).isWithin(0.01).of(285.0 + 195.0)
        // The snapshot food is hidden from search.
        assertThat(db.foodDao().searchCandidates("pizza", 10)).isEmpty()
    }

    @Test
    fun recipeUsesCookedWeight() = runTest {
        // 100 g dry-ish ingredients that weigh 250 g after cooking: per-100 g values shrink by 2.5.
        val recipe = recipes.save(null, "Rice pot", listOf(DraftItem(rice, 100.0), DraftItem(chicken, 100.0)), yieldGrams = 500.0, servings = 4.0)
        assertThat(recipe.per100.kcal).isWithin(0.01).of((130.0 + 165.0) / 5)
        assertThat(recipe.portions.first()).isEqualTo(Portion("serving", 125.0))
        assertThat(recipes.get(recipe.id)!!.ingredients).hasSize(2)
    }

    @Test
    fun copyToAnotherDayKeepsTimeAndRegroups() = runTest {
        val meal = meals.get(meals.save(null, "Lunch", listOf(DraftItem(rice, 100.0), DraftItem(chicken, 100.0))))!!
        val original = log.logMeal(meal, 1.0, day, LocalTime.of(13, 0))
        val tomorrow = day.plusDays(1)
        log.copyTo(original, tomorrow)
        val copied = log.observeDay(tomorrow).first()
        assertThat(copied).hasSize(2)
        assertThat(copied.map { it.groupId }.distinct().single()).isNotEqualTo(original.first().groupId)
    }

    @Test
    fun backupRoundTripRestoresEverything() = runTest {
        val settings = SettingsRepository(PreferenceDataStoreFactory.create { tmp.newFile("settings.preferences_pb") })
        settings.setTargets(Targets(2500, 180, 250, 80))
        log.logFood(rice, 100.0, null, day)
        meals.save(null, "Rice", listOf(DraftItem(rice, 100.0)))
        db.weightDao().upsert(app.tare.data.db.WeightEntry(day.toEpochDay(), 80.5, clock))

        val backup = BackupManager(db, settings, now)
        val bytes = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

        db.logDao().clear()
        db.mealDao().clear()
        db.weightDao().clear()
        settings.setTargets(Targets())

        val summary = backup.import(bytes.inputStream())
        assertThat(summary.entries).isEqualTo(1)
        assertThat(db.mealDao().allMeals()).hasSize(1)
        assertThat(db.weightDao().all().single().kg).isEqualTo(80.5)
        assertThat(settings.targets.first()).isEqualTo(Targets(2500, 180, 250, 80))
    }
}
