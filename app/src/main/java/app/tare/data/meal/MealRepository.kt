package app.tare.data.meal

import androidx.room.withTransaction
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.data.db.Meal
import app.tare.data.db.MealItem
import app.tare.data.db.MealWithItems
import app.tare.data.db.RecipeIngredient
import app.tare.data.db.RecipeWithIngredients
import app.tare.data.db.TareDatabase
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.buildSearchText
import app.tare.domain.normalizeForSearch
import app.tare.domain.searchTokens
import app.tare.domain.sum
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** A food + amount inside a meal or recipe that is being edited. */
data class DraftItem(val food: Food, val grams: Double, val portion: Portion? = null) {
    val nutrients: Nutrients get() = food.per100.forGrams(grams)
}

class MealRepository(
    private val db: TareDatabase,
    private val now: () -> Long,
) {
    private val dao = db.mealDao()
    private val foodDao = db.foodDao()

    fun observeAll(): Flow<List<MealWithItems>> = dao.observeAll()

    suspend fun get(id: String): MealWithItems? = dao.get(id)

    suspend fun search(query: String): List<MealWithItems> {
        val tokens = searchTokens(query)
        if (tokens.isEmpty()) return emptyList()
        return dao.searchCandidates(tokens.maxBy { it.length })
            .filter { m -> tokens.all { m.meal.searchText.contains(it) } }
    }

    suspend fun save(id: String?, name: String, items: List<DraftItem>): String {
        val at = now()
        val existing = id?.let { dao.get(it) }?.meal
        val meal = Meal(
            id = existing?.id ?: "meal:${UUID.randomUUID()}",
            name = name.trim(),
            searchText = normalizeForSearch(name),
            useCount = existing?.useCount ?: 0,
            lastUsedAt = existing?.lastUsedAt,
            createdAt = existing?.createdAt ?: at,
            updatedAt = at,
        )
        dao.save(
            meal,
            items.mapIndexed { i, d -> MealItem(meal.id, i, d.food.id, d.grams, d.portion?.label) },
        )
        return meal.id
    }

    /**
     * Saves logged entries as a reusable meal. Entries without a backing food (quick adds)
     * get a hidden snapshot food so the meal can still reproduce them.
     */
    suspend fun saveFromEntries(name: String, entries: List<LogEntry>): String = db.withTransaction {
        val at = now()
        val items = entries.map { entry ->
            val food = entry.foodId?.let { foodDao.get(it) }
            if (food != null && entry.grams != null) {
                DraftItem(food, entry.grams, food.portions.firstOrNull { it.label == entry.portionLabel })
            } else {
                val grams = entry.grams?.takeIf { it > 0 } ?: 100.0
                val snapshot = Food(
                    id = "custom:${UUID.randomUUID()}",
                    source = FoodSource.CUSTOM,
                    name = entry.name,
                    brand = entry.brand,
                    searchText = buildSearchText(entry.name, entry.brand, null),
                    per100 = entry.nutrients * (100.0 / grams),
                    portions = listOf(Portion("serving", grams)),
                    isLiquid = entry.isLiquid,
                    hidden = true,
                    createdAt = at,
                    updatedAt = at,
                )
                foodDao.upsert(snapshot)
                DraftItem(snapshot, grams, snapshot.portions.first())
            }
        }
        save(null, name, items)
    }

    /** Deletes a meal and returns it, so the caller can offer undo via [restore]. */
    suspend fun delete(id: String): MealWithItems? {
        val snapshot = dao.get(id)
        dao.delete(id)
        return snapshot
    }

    suspend fun restore(snapshot: MealWithItems) = dao.save(snapshot.meal, snapshot.items.map { it.item })
}

class RecipeRepository(
    private val db: TareDatabase,
    private val now: () -> Long,
) {
    private val dao = db.recipeDao()

    suspend fun get(id: String): RecipeWithIngredients? = dao.get(id)

    /**
     * Saves a recipe as a food whose per-100 g values come from its ingredients divided by
     * the cooked weight ([yieldGrams]; defaults to the raw ingredient weight).
     */
    suspend fun save(
        id: String?,
        name: String,
        ingredients: List<DraftItem>,
        yieldGrams: Double?,
        servings: Double?,
    ): Food {
        val at = now()
        val existing = id?.let { dao.get(it) }?.recipe
        val total = ingredients.map { it.nutrients }.sum()
        val rawWeight = ingredients.sumOf { it.grams }
        val weight = (yieldGrams?.takeIf { it > 0 } ?: rawWeight).coerceAtLeast(1.0)
        val servingCount = servings?.takeIf { it > 0 }
        val portions = buildList {
            if (servingCount != null) add(Portion("serving", weight / servingCount))
            add(Portion("whole recipe", weight))
        }
        val recipe = Food(
            id = existing?.id ?: "recipe:${UUID.randomUUID()}",
            source = FoodSource.RECIPE,
            name = name.trim(),
            searchText = buildSearchText(name, null, null),
            per100 = total * (100.0 / weight),
            portions = portions,
            useCount = existing?.useCount ?: 0,
            lastUsedAt = existing?.lastUsedAt,
            lastGrams = existing?.lastGrams,
            lastPortionLabel = existing?.lastPortionLabel,
            favorite = existing?.favorite ?: false,
            recipeYieldGrams = yieldGrams?.takeIf { it > 0 },
            recipeServings = servingCount,
            createdAt = existing?.createdAt ?: at,
            updatedAt = at,
        )
        dao.save(
            recipe,
            ingredients.mapIndexed { i, d -> RecipeIngredient(recipe.id, i, d.food.id, d.grams, d.portion?.label) },
        )
        return recipe
    }
}
