package app.tare.data.food

import app.tare.data.db.Food
import app.tare.data.db.FoodDao
import app.tare.domain.FoodSource
import app.tare.domain.RemoteSignals
import app.tare.domain.buildSearchText
import app.tare.domain.rankFoods
import app.tare.domain.rankRemote
import app.tare.domain.searchTokens
import androidx.sqlite.db.SimpleSQLiteQuery
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

    /**
     * The food an old entry should open: itself, or — if it was forked into a corrected custom
     * food (and hidden) — the visible food that took over its barcode.
     */
    suspend fun resolve(id: String): Food? {
        val food = dao.get(id) ?: return null
        if (!food.hidden) return food
        return food.barcode?.let { dao.byBarcode(it) } ?: food
    }

    fun recent(limit: Int = 40): Flow<List<Food>> = dao.observeRecent(limit)

    fun frequent(limit: Int = 40): Flow<List<Food>> = dao.observeFrequent(limit)

    fun favorites(): Flow<List<Food>> = dao.observeFavorites()

    fun customAndFavorites(): Flow<List<Food>> = dao.observeCustomAndFavorites()

    fun recipes(): Flow<List<Food>> = dao.observeRecipes()

    suspend fun searchLocal(query: String, limit: Int = 30): List<Food> = withContext(Dispatchers.IO) {
        val tokens = searchTokens(query)
        if (tokens.isEmpty()) return@withContext emptyList()
        // Every token must match in SQL; anchoring on one token and filtering afterwards drops
        // good rows when that token is common ("cooked" matches ~1,700 foods).
        val sql = "SELECT * FROM food WHERE hidden = 0 AND " +
            tokens.joinToString(" AND ") { "searchText LIKE ?" } +
            " ORDER BY useCount DESC, popularity DESC, length(name) LIMIT 400"
        val candidates = dao.search(SimpleSQLiteQuery(sql, tokens.map { "%$it%" }.toTypedArray()))
        rankFoods(candidates, query, now(), limit)
    }

    /** Open Food Facts text search; Dutch products first, worldwide when that comes up short. */
    suspend fun searchOnline(query: String): List<Food> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        val ranked = synchronized(remoteCache) { remoteCache[key] } ?: run {
            var hits = off.search(query, netherlandsOnly = true)
            if (hits.size < 8) {
                // Keep the Dutch hits even if the worldwide fallback fails.
                val world = runCatching { off.search(query, netherlandsOnly = false) }.getOrDefault(emptyList())
                hits = (hits + world).distinctBy { it.code }
            }
            val at = now()
            rankRemote(
                hits.mapNotNull { p -> p.toFood(at)?.let { it to RemoteSignals(p.uniqueScans ?: 0, p.soldInNetherlands) } },
                query,
            ).distinctBy { it.id }.take(25).also { synchronized(remoteCache) { remoteCache[key] = it } }
        }
        // A product the user corrected (forked to a custom food, original hidden) must not come
        // back from the network with the old values.
        val superseded = dao.byBarcodes(ranked.mapNotNull { it.barcode })
            .filter { it.hidden || it.source != FoodSource.OPEN_FOOD_FACTS }
            .mapNotNull { it.barcode }
            .toSet()
        ranked.filterNot { it.barcode in superseded }
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
                lastGrams = original.lastGrams,
                favorite = original.favorite,
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

    /** Undo for [hide]. */
    suspend fun unhide(id: String) = dao.unhide(id, now())
}
