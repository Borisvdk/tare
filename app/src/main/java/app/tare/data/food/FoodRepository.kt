package app.tare.data.food

import app.tare.data.db.Food
import app.tare.data.db.FoodDao
import app.tare.domain.FoodSource
import app.tare.domain.RemoteSignals
import app.tare.domain.buildSearchText
import app.tare.domain.rankFoods
import app.tare.domain.rankRemote
import app.tare.domain.searchTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface BarcodeResult {
    val barcode: String

    data class Found(override val barcode: String, val food: Food) : BarcodeResult

    /** Open Food Facts doesn't know this product. */
    data class NotFound(override val barcode: String) : BarcodeResult

    /** Known product without usable nutrition data. */
    data class Incomplete(override val barcode: String, val name: String?) : BarcodeResult

    data class Failed(override val barcode: String, val message: String) : BarcodeResult
}

class FoodRepository(
    private val dao: FoodDao,
    private val off: OpenFoodFactsClient,
    private val now: () -> Long,
) {
    private val remoteCache = object : LinkedHashMap<String, List<Food>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Food>>) = size > 40
    }

    fun observe(id: String): Flow<Food?> = dao.observe(id)

    suspend fun get(id: String): Food? = dao.get(id)

    fun recent(limit: Int = 40): Flow<List<Food>> = dao.observeRecent(limit)

    fun frequent(limit: Int = 40): Flow<List<Food>> = dao.observeFrequent(limit)

    fun customAndFavorites(): Flow<List<Food>> = dao.observeCustomAndFavorites()

    fun recipes(): Flow<List<Food>> = dao.observeRecipes()

    suspend fun searchLocal(query: String, limit: Int = 30): List<Food> = withContext(Dispatchers.IO) {
        val tokens = searchTokens(query)
        if (tokens.isEmpty()) return@withContext emptyList()
        val anchor = tokens.maxBy { it.length }
        rankFoods(dao.searchCandidates(anchor, limit = 400), query, now(), limit)
    }

    /** Open Food Facts text search; Dutch products first, worldwide when that comes up short. */
    suspend fun searchOnline(query: String): List<Food> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        synchronized(remoteCache) { remoteCache[key] }?.let { return@withContext it }
        var hits = off.search(query, netherlandsOnly = true)
        if (hits.size < 8) hits = (hits + off.search(query, netherlandsOnly = false)).distinctBy { it.code }
        val at = now()
        val ranked = rankRemote(
            hits.mapNotNull { p -> p.toFood(at)?.let { it to RemoteSignals(p.uniqueScans ?: 0, p.soldInNetherlands) } },
            query,
        ).distinctBy { it.id }.take(25)
        synchronized(remoteCache) { remoteCache[key] = ranked }
        ranked
    }

    suspend fun lookupBarcode(barcode: String): BarcodeResult = withContext(Dispatchers.IO) {
        dao.byBarcode(barcode)?.let { return@withContext BarcodeResult.Found(barcode, it) }
        val product = try {
            off.product(barcode)
        } catch (e: Exception) {
            return@withContext BarcodeResult.Failed(barcode, e.message ?: "Network error")
        } ?: return@withContext BarcodeResult.NotFound(barcode)
        val food = product.toFood(now())
            ?: return@withContext BarcodeResult.Incomplete(barcode, product.displayName)
        BarcodeResult.Found(barcode, saveRemote(food))
    }

    /** Persists a food that came from the network (or AI), keeping any usage stats it already has. */
    suspend fun saveRemote(food: Food): Food = withContext(Dispatchers.IO) {
        val existing = dao.get(food.id)
        val merged = if (existing == null) {
            food
        } else {
            food.copy(
                useCount = existing.useCount,
                lastUsedAt = existing.lastUsedAt,
                lastGrams = existing.lastGrams,
                lastPortionLabel = existing.lastPortionLabel,
                favorite = existing.favorite,
                hidden = existing.hidden,
                createdAt = existing.createdAt,
            )
        }
        dao.upsert(merged)
        merged
    }

    /**
     * Creates or updates a hand-made food. Editing a non-custom food (e.g. a scanned product
     * with wrong data) forks it into a custom food that takes over its barcode.
     */
    suspend fun saveCustom(draft: Food): Food = withContext(Dispatchers.IO) {
        val at = now()
        val original = dao.get(draft.id)
        val food = if (original == null || original.source == FoodSource.CUSTOM) {
            draft.copy(
                id = original?.id ?: draft.id.ifBlank { "custom:${UUID.randomUUID()}" },
                source = FoodSource.CUSTOM,
                searchText = buildSearchText(draft.name, draft.brand, draft.aliases),
                createdAt = original?.createdAt ?: at,
                updatedAt = at,
            )
        } else {
            dao.hide(original.id, at)
            draft.copy(
                id = "custom:${UUID.randomUUID()}",
                source = FoodSource.CUSTOM,
                searchText = buildSearchText(draft.name, draft.brand, draft.aliases),
                useCount = original.useCount,
                lastUsedAt = original.lastUsedAt,
                hidden = false,
                createdAt = at,
                updatedAt = at,
            )
        }
        dao.upsert(food)
        food
    }

    suspend fun markUsed(id: String, grams: Double?, portionLabel: String?) =
        dao.markUsed(id, now(), grams, portionLabel)

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun hide(id: String) = dao.hide(id, now())
}
