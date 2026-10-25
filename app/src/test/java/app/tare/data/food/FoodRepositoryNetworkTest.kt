package app.tare.data.food

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.tare.data.db.TareDatabase
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Barcode lookups and online search against canned Open Food Facts responses. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class FoodRepositoryNetworkTest {
    private lateinit var db: TareDatabase
    private val requests = mutableListOf<String>()

    @Before
    fun setUp() {
        db = TareDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    private fun repo(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): FoodRepository {
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            handler(request)
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
        return FoodRepository(db.foodDao(), OpenFoodFactsClient(client)) { 0L }
    }

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun foundProductIsCachedForInstantRescans() = runTest {
        val foods = repo {
            json("""{"status":1,"product":{"code":"871","product_name":"Magere kwark","brands":"Jumbo","nutriments":{"energy-kcal_100g":50,"proteins_100g":9}}}""")
        }
        val first = foods.lookupBarcode("871")
        assertThat(first).isInstanceOf(BarcodeResult.Found::class.java)
        assertThat((first as BarcodeResult.Found).food.name).isEqualTo("Magere kwark")

        foods.lookupBarcode("871")
        assertThat(requests).hasSize(1) // second scan served from the database
    }

    @Test
    fun unknownIncompleteAndOfflineAreDistinguished() = runTest {
        assertThat(repo { json("""{"status":0,"status_verbose":"product not found"}""") }.lookupBarcode("1"))
            .isEqualTo(BarcodeResult.NotFound("1"))
        assertThat(repo { json("""{"status":1,"product":{"code":"2","product_name":"Mystery","nutriments":{}}}""") }.lookupBarcode("2"))
            .isEqualTo(BarcodeResult.Incomplete("2", "Mystery"))
        assertThat(repo { respondError(HttpStatusCode.ServiceUnavailable) }.lookupBarcode("3"))
            .isInstanceOf(BarcodeResult.Failed::class.java)
    }

    @Test
    fun searchPrefersDutchProductsAndDropsIncompleteOnes() = runTest {
        val foods = repo { request ->
            val dutchOnly = request.url.parameters["q"]!!.contains("netherlands")
            if (dutchOnly) {
                json(
                    """{"hits":[
                    {"code":"a","product_name":"Skyr","brands":["Arla"],"countries_tags":["en:netherlands"],"unique_scans_n":40,"nutriments":{"energy-kcal_100g":60,"proteins_100g":11}},
                    {"code":"b","product_name":"Skyr","brands":["Nobody"],"countries_tags":["en:netherlands"],"nutriments":{}}
                    ]}""",
                )
            } else {
                json("""{"hits":[{"code":"c","product_name":"Skyr","brands":["Siggi's"],"countries_tags":["en:france"],"unique_scans_n":500,"nutriments":{"energy-kcal_100g":70,"proteins_100g":10}}]}""")
            }
        }
        val results = foods.searchOnline("skyr")
        assertThat(results.map { it.id }).containsExactly("off:a", "off:c").inOrder()
    }

    @Test
    fun dutchHitsSurviveAFailingWorldwideFallback() = runTest {
        val foods = repo { request ->
            if (request.url.parameters["q"]!!.contains("netherlands")) {
                json("""{"hits":[{"code":"a","product_name":"Hagelslag","countries_tags":["en:netherlands"],"nutriments":{"energy-kcal_100g":480,"proteins_100g":5}}]}""")
            } else {
                respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        assertThat(foods.searchOnline("hagelslag").map { it.id }).containsExactly("off:a")
    }
}
