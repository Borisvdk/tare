package app.tare.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Upsert
    suspend fun upsert(food: Food)

    @Upsert
    suspend fun upsertAll(foods: List<Food>)

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun get(id: String): Food?

    @Query("SELECT * FROM food WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<Food>

    @Query("SELECT * FROM food WHERE id = :id")
    fun observe(id: String): Flow<Food?>

    @Query("SELECT * FROM food WHERE barcode = :barcode AND hidden = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun byBarcode(barcode: String): Food?

    /**
     * Candidate rows containing [token]; ranking happens in Kotlin. Pre-sorted by usage and
     * popularity so a common token ("rice") still keeps the rows most likely to win.
     */
    @Query(
        """
        SELECT * FROM food
        WHERE hidden = 0 AND searchText LIKE '%' || :token || '%'
        ORDER BY useCount DESC, popularity DESC, length(name)
        LIMIT :limit
        """,
    )
    suspend fun searchCandidates(token: String, limit: Int): List<Food>

    @Query("SELECT * FROM food WHERE hidden = 0 AND lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Food>>

    @Query("SELECT * FROM food WHERE hidden = 0 AND useCount > 0 ORDER BY useCount DESC, lastUsedAt DESC LIMIT :limit")
    fun observeFrequent(limit: Int): Flow<List<Food>>

    @Query("SELECT * FROM food WHERE hidden = 0 AND (source = 'CUSTOM' OR favorite = 1) ORDER BY name COLLATE NOCASE")
    fun observeCustomAndFavorites(): Flow<List<Food>>

    @Query("SELECT * FROM food WHERE hidden = 0 AND source = 'RECIPE' ORDER BY lastUsedAt IS NULL, lastUsedAt DESC, name COLLATE NOCASE")
    fun observeRecipes(): Flow<List<Food>>

    @Query(
        """
        UPDATE food SET useCount = useCount + 1, lastUsedAt = :at, lastGrams = :grams,
        lastPortionLabel = :portionLabel WHERE id = :id
        """,
    )
    suspend fun markUsed(id: String, at: Long, grams: Double?, portionLabel: String?)

    @Query("UPDATE food SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE food SET hidden = 1, updatedAt = :at WHERE id = :id")
    suspend fun hide(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM food WHERE source = 'GENERIC'")
    suspend fun genericCount(): Int

    @Query("SELECT * FROM food WHERE source = 'GENERIC' AND (useCount > 0 OR favorite = 1)")
    suspend fun usedGeneric(): List<Food>

    @Query("SELECT * FROM food WHERE source != 'GENERIC' OR useCount > 0 OR favorite = 1")
    suspend fun allForBackup(): List<Food>
}

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entry WHERE day = :day ORDER BY loggedAt, id")
    fun observeDay(day: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entry WHERE day = :day ORDER BY loggedAt, id")
    suspend fun entriesForDay(day: Long): List<LogEntry>

    @Query(
        """
        SELECT day, SUM(n_kcal) AS kcal, SUM(n_protein) AS protein, SUM(n_carbs) AS carbs,
        SUM(n_fat) AS fat, SUM(n_fiber) AS fiber
        FROM log_entry WHERE day BETWEEN :from AND :to GROUP BY day ORDER BY day
        """,
    )
    fun observeTotals(from: Long, to: Long): Flow<List<DayTotals>>

    @Query("SELECT * FROM log_entry WHERE id = :id")
    suspend fun get(id: Long): LogEntry?

    @Query("SELECT * FROM log_entry WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<LogEntry>

    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Insert
    suspend fun insertAll(entries: List<LogEntry>): List<Long>

    @Update
    suspend fun update(entry: LogEntry)

    @Delete
    suspend fun delete(entries: List<LogEntry>)

    @Query("SELECT * FROM log_entry ORDER BY day, loggedAt")
    suspend fun all(): List<LogEntry>

    @Query("SELECT IFNULL(MAX(id), 0) FROM log_entry")
    suspend fun maxId(): Long

    @Query("DELETE FROM log_entry")
    suspend fun clear()
}

@Dao
interface MealDao {
    @Transaction
    @Query("SELECT * FROM meal ORDER BY lastUsedAt IS NULL, lastUsedAt DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM meal WHERE id = :id")
    suspend fun get(id: String): MealWithItems?

    @Transaction
    @Query("SELECT * FROM meal WHERE searchText LIKE '%' || :token || '%' ORDER BY useCount DESC LIMIT 20")
    suspend fun searchCandidates(token: String): List<MealWithItems>

    @Upsert
    suspend fun upsertMeal(meal: Meal)

    @Insert
    suspend fun insertItems(items: List<MealItem>)

    @Query("DELETE FROM meal_item WHERE mealId = :mealId")
    suspend fun deleteItems(mealId: String)

    @Transaction
    suspend fun save(meal: Meal, items: List<MealItem>) {
        upsertMeal(meal)
        deleteItems(meal.id)
        insertItems(items)
    }

    @Query("DELETE FROM meal WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE meal SET useCount = useCount + 1, lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: String, at: Long)

    @Query("SELECT * FROM meal")
    suspend fun allMeals(): List<Meal>

    @Query("SELECT * FROM meal_item")
    suspend fun allItems(): List<MealItem>

    @Query("DELETE FROM meal")
    suspend fun clear()
}

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun get(id: String): RecipeWithIngredients?

    @Upsert
    suspend fun upsertFood(food: Food)

    @Insert
    suspend fun insertIngredients(items: List<RecipeIngredient>)

    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: String)

    @Transaction
    suspend fun save(recipe: Food, ingredients: List<RecipeIngredient>) {
        upsertFood(recipe)
        deleteIngredients(recipe.id)
        insertIngredients(ingredients)
    }

    @Query("SELECT * FROM recipe_ingredient")
    suspend fun allIngredients(): List<RecipeIngredient>

    @Query("DELETE FROM recipe_ingredient")
    suspend fun clear()
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight ORDER BY day")
    fun observeAll(): Flow<List<WeightEntry>>

    @Upsert
    suspend fun upsert(entry: WeightEntry)

    @Query("DELETE FROM weight WHERE day = :day")
    suspend fun delete(day: Long)

    @Query("SELECT * FROM weight ORDER BY day")
    suspend fun all(): List<WeightEntry>

    @Insert
    suspend fun insertAll(entries: List<WeightEntry>)

    @Query("DELETE FROM weight")
    suspend fun clear()
}
