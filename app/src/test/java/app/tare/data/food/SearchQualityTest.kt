package app.tare.data.food

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.tare.data.db.TareDatabase
import com.google.common.truth.Truth.assertWithMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Search quality against the real bundled dataset: for everyday queries (Dutch and English),
 * the food you mean must be the first result. Add a case whenever a search disappoints.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SearchQualityTest {
    private val expectations = mapOf(
        "banaan" to "Banana",
        "banana" to "Banana",
        "havermout" to "Rolled oats",
        "kipfilet" to "Chicken breast",
        "rijst" to "rice",
        "kwark" to "Greek yogurt",
        "pindakaas" to "Peanut butter",
        "aardappel" to "Potato",
        "appel" to "Apple",
        "melk" to "Milk",
        "ei" to "Egg",
        "eieren" to "Egg",
        "egg" to "Egg",
        "zalm" to "Salmon",
        "broccoli" to "Broccoli",
        "olijfolie" to "Olive oil",
        "olive oil" to "Olive oil",
        "boter" to "Butter",
        "gehakt" to "Ground beef",
        "spinazie" to "Spinach",
        "tomaat" to "Tomato",
        "komkommer" to "Cucumber",
        "avocado" to "Avocado",
        "amandelen" to "Almonds",
        "honing" to "Honey",
        "kaas" to "cheese",
        "brood" to "bread",
        "chicken breast" to "Chicken breast",
        "white rice" to "White rice",
        "sweet potato" to "Sweet potato",
        "yoghurt" to "yogurt",
        "volkoren brood" to "Whole",
        "witte rijst" to "White rice",
        "kipdij" to "Chicken thigh",
        "biefstuk" to "Beef",
        "tonijn" to "Tuna",
        "paprika" to "pepper",
        "ui" to "Onion",
        "knoflook" to "Garlic",
        "pasta" to "Pasta",
        "spaghetti" to "Pasta",
        "couscous" to "Couscous",
        "linzen" to "Lentils",
        "kikkererwten" to "Chickpeas",
        "mango" to "Mango",
        "blauwe bessen" to "Blueberries",
        "walnoten" to "Walnuts",
        "cashewnoten" to "Cashew",
        "suiker" to "Sugar",
        "jam" to "Jam",
        "pure chocolade" to "chocolate",
        "sinaasappelsap" to "Orange juice",
        "koffie" to "Coffee",
        "thee" to "Tea",
        "bier" to "Beer",
        "wijn" to "wine",
        "sla" to "Lettuce",
        "wortel" to "Carrot",
        "champignons" to "Mushroom",
        "quinoa" to "Quinoa",
    )

    // Phone-keyboard typos: a missed, doubled or swapped letter must still find the food.
    private val typos = mapOf(
        "bnaan" to "Banana",
        "kipfilt" to "Chicken breast",
        "havremout" to "Rolled oats",
        "pindakas" to "Peanut butter",
        "brocolli" to "Broccoli",
        "avocdo" to "Avocado",
        "chiken breast" to "Chicken breast",
        "komkomer" to "Cucumber",
        "aardapel" to "Potato",
        "spinazei" to "Spinach",
        "sinasappelsap" to "Orange juice",
        "yoghrt" to "yogurt",
    )

    @Test
    fun everydayQueriesFindTheRightFoodFirst() = runTest {
        assertTopResults(expectations)
    }

    @Test
    fun typosStillFindTheRightFoodFirst() = runTest {
        assertTopResults(typos)
    }

    private suspend fun assertTopResults(cases: Map<String, String>) {
        val db = TareDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val file = listOf("src/main/assets/generic_foods.tsv", "app/src/main/assets/generic_foods.tsv").map(::File).first { it.exists() }
        file.inputStream().use { parseGenericFoods(it, now = 0) }.chunked(500).forEach { db.foodDao().upsertAll(it) }
        val foods = FoodRepository(db.foodDao(), OpenFoodFactsClient(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }))) { 0L }
        val failures = cases.mapNotNull { (query, expected) ->
            val top = foods.searchLocal(query, limit = 3).map { it.name }
            if (top.firstOrNull()?.contains(expected, ignoreCase = true) == true) null else "\"$query\" → $top (expected \"$expected\")"
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }
}
