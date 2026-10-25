package app.tare.ui.log

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.tare.AppGraph
import app.tare.data.db.Food
import app.tare.data.db.TareDatabase
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.buildSearchText
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Typo guesses in the shared search pipeline, and when Open Food Facts overrules them. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class FoodSearchTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bacon = Food(
        id = "usda:bacon",
        source = FoodSource.GENERIC,
        name = "Bacon, raw",
        aliases = "ontbijtspek",
        searchText = buildSearchText("Bacon, raw", null, "ontbijtspek"),
        per100 = Nutrients(417.0, 13.0, 1.4, 40.0),
        createdAt = 0,
        updatedAt = 0,
    )

    @After
    fun tearDown() = scope.cancel()

    private fun search(online: String?): FoodSearch {
        val graph = AppGraph(
            ApplicationProvider.getApplicationContext(),
            now = { 0L },
            databaseFactory = { TareDatabase.inMemory(it) },
            httpFactory = {
                HttpClient(MockEngine {
                    if (online == null) {
                        respondError(HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(online, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
                }
            },
        )
        runBlocking { graph.db.foodDao().upsert(bacon) }
        return FoodSearch(graph, scope, includeMeals = false)
    }

    private fun FoodSearch.settled(query: String): SearchResults = runBlocking {
        this@settled.query.value = query
        withTimeout(5_000) { results.first { it.query == query && it.remote !is RemoteState.Idle && it.remote !is RemoteState.Loading } }
    }

    @Test
    fun offlineTypoShowsTheGuessWithWhatItSearched() {
        val results = search(online = null).settled("ontbijtkoek")
        assertThat(results.local.map { it.id }).containsExactly("usda:bacon")
        assertThat(results.fixedQuery).isEqualTo("ontbijtspek")
    }

    @Test
    fun productsMatchingTheTypedWordDropTheGuess() {
        val ontbijtkoek = """{"hits":[{"code":"8710","product_name":"Ontbijtkoek","brands":["Peijnenburg"],"countries_tags":["en:netherlands"],"nutriments":{"energy-kcal_100g":320,"proteins_100g":4}}]}"""
        val results = search(online = ontbijtkoek).settled("ontbijtkoek")
        assertThat(results.local).isEmpty()
        assertThat(results.fixedQuery).isNull()
        assertThat((results.remote as RemoteState.Loaded).foods.map { it.name }).containsExactly("Ontbijtkoek")
    }
}
