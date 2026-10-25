package app.tare

import android.content.Context
import app.tare.data.ai.FoodAi
import app.tare.data.backup.BackupManager
import app.tare.data.db.TareDatabase
import app.tare.data.food.FoodRepository
import app.tare.data.food.GenericFoodImporter
import app.tare.data.food.OpenFoodFactsClient
import app.tare.data.health.HealthConnectSource
import app.tare.data.update.UpdateRepository
import app.tare.data.update.Updater
import app.tare.data.health.HealthRepository
import app.tare.data.health.HealthSource
import app.tare.data.log.LogRepository
import app.tare.data.meal.MealRepository
import app.tare.data.meal.RecipeRepository
import app.tare.data.settings.SecretStore
import app.tare.data.settings.SettingsRepository
import app.tare.data.settings.secretsDataStore
import app.tare.data.settings.settingsDataStore
import app.tare.data.weight.WeightRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Manual dependency graph: one instance per process, created in [TareApp]. Everything is
 * lazy so tests and previews only build what they touch.
 */
class AppGraph(
    private val context: Context,
    val now: () -> Long = System::currentTimeMillis,
    private val databaseFactory: (Context) -> TareDatabase = TareDatabase::build,
    /** Tests pass a mock-engine client so nothing reaches the network. */
    private val httpFactory: (() -> HttpClient)? = null,
    /** Tests pass a fake so nothing talks to Health Connect. */
    private val healthFactory: ((Context) -> HealthSource)? = null,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: TareDatabase by lazy { databaseFactory(context) }

    val http: HttpClient by lazy {
        httpFactory?.invoke() ?: HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 8_000
            }
            defaultRequest {
                // Open Food Facts asks every app to identify itself.
                header(HttpHeaders.UserAgent, "Tare/${BuildConfig.VERSION_NAME} (Android; github.com/Borisvdk/tare)")
            }
        }
    }

    val settings: SettingsRepository by lazy { SettingsRepository(context.settingsDataStore) }
    val secrets: SecretStore by lazy { SecretStore(context.secretsDataStore) }
    val foods: FoodRepository by lazy { FoodRepository(db.foodDao(), OpenFoodFactsClient(http), now) }
    val log: LogRepository by lazy { LogRepository(db, now) }
    val meals: MealRepository by lazy { MealRepository(db, now) }
    val recipes: RecipeRepository by lazy { RecipeRepository(db, now) }
    val weights: WeightRepository by lazy { WeightRepository(db.weightDao(), now) }
    val backup: BackupManager by lazy { BackupManager(db, settings, now) }
    val health: HealthRepository by lazy {
        HealthRepository(healthFactory?.invoke(context) ?: HealthConnectSource(context), db.weightDao(), settings, now)
    }
    val updates: UpdateRepository by lazy { UpdateRepository(http, token = { secrets.githubToken() }, currentVersionCode = BuildConfig.VERSION_CODE) }
    val updater: Updater by lazy { Updater(context, updates, appScope, now, enabled = !BuildConfig.DEBUG) }
    val ai: FoodAi by lazy { FoodAi(apiKey = { secrets.apiKey() }, model = { settings.aiModel.first() }) }

    /** Reads a content Uri (e.g. a photo shared from the gallery); null if it's gone or unreadable. */
    fun readUri(uri: String): ByteArray? =
        runCatching { context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() } }.getOrNull()

    val genericImporter: GenericFoodImporter by lazy {
        GenericFoodImporter(context.assets, db, settings, BuildConfig.GENERIC_DATASET_VERSION, now)
    }
}
