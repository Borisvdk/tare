package app.tare.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import kotlinx.serialization.Serializable

/**
 * Anything that can be eaten: bundled generic foods, cached Open Food Facts products,
 * custom foods, recipes and AI estimates. Nutrients are per 100 g (per 100 ml if [isLiquid]).
 *
 * Ids are namespaced strings ("usda:173944", "off:5449000000996", "custom:<uuid>") so that
 * re-importing or re-fetching a food upserts the same row instead of duplicating it.
 */
@Serializable
@Entity(
    tableName = "food",
    indices = [Index("barcode"), Index("lastUsedAt"), Index("source")],
)
data class Food(
    @PrimaryKey val id: String,
    val source: FoodSource,
    val name: String,
    val brand: String? = null,
    /** `|`-separated search synonyms, e.g. Dutch names for generic foods. */
    val aliases: String? = null,
    val category: String? = null,
    /** Normalized name + brand + aliases; see [app.tare.domain.buildSearchText]. */
    val searchText: String,
    @Embedded(prefix = "per100_") val per100: Nutrients,
    val portions: List<Portion> = emptyList(),
    val barcode: String? = null,
    val imageUrl: String? = null,
    val isLiquid: Boolean = false,
    /** 0–100 prior for search ranking (bundled data only). */
    val popularity: Int = 0,
    val useCount: Int = 0,
    val lastUsedAt: Long? = null,
    val lastGrams: Double? = null,
    val lastPortionLabel: String? = null,
    val favorite: Boolean = false,
    /** Soft delete: hidden foods stay resolvable for old meals/recipes but leave search. */
    val hidden: Boolean = false,
    /** Recipes only: total cooked weight and the number of servings it makes. */
    val recipeYieldGrams: Double? = null,
    val recipeServings: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val unit: String get() = if (isLiquid) "ml" else "g"
}

/**
 * One thing eaten on a day. Name and nutrient totals are snapshotted at log time so that
 * editing or deleting a food never rewrites history.
 */
@Serializable
@Entity(
    tableName = "log_entry",
    indices = [Index("day"), Index("foodId"), Index("groupId")],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [java.time.LocalDate.toEpochDay] of the day this counts towards. */
    val day: Long,
    /** Epoch millis; drives time-of-day grouping on the Today screen. */
    val loggedAt: Long,
    val foodId: String?,
    val name: String,
    val brand: String? = null,
    val source: FoodSource,
    /** Amount eaten in g/ml; null for quick-add entries. */
    val grams: Double?,
    val isLiquid: Boolean = false,
    val portionLabel: String? = null,
    val portionGrams: Double? = null,
    @Embedded(prefix = "n_") val nutrients: Nutrients,
    /** Entries logged together from a saved meal or AI batch share a group. */
    val groupId: String? = null,
    val groupName: String? = null,
)

@Serializable
@Entity(tableName = "meal")
data class Meal(
    @PrimaryKey val id: String,
    val name: String,
    val searchText: String,
    val useCount: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "meal_item",
    primaryKeys = ["mealId", "position"],
    foreignKeys = [
        ForeignKey(entity = Meal::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("foodId")],
)
data class MealItem(
    val mealId: String,
    val position: Int,
    val foodId: String,
    val grams: Double,
    val portionLabel: String? = null,
)

@Serializable
@Entity(
    tableName = "recipe_ingredient",
    primaryKeys = ["recipeId", "position"],
    foreignKeys = [
        ForeignKey(entity = Food::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("foodId")],
)
data class RecipeIngredient(
    val recipeId: String,
    val position: Int,
    val foodId: String,
    val grams: Double,
    val portionLabel: String? = null,
)

@Serializable
@Entity(tableName = "weight")
data class WeightEntry(
    @PrimaryKey val day: Long,
    val kg: Double,
    val loggedAt: Long,
)

data class MealItemWithFood(
    @Embedded val item: MealItem,
    @Relation(parentColumn = "foodId", entityColumn = "id") val food: Food?,
) {
    val nutrients: Nutrients get() = food?.per100?.forGrams(item.grams) ?: Nutrients.Zero
}

data class MealWithItems(
    @Embedded val meal: Meal,
    @Relation(entity = MealItem::class, parentColumn = "id", entityColumn = "mealId")
    val items: List<MealItemWithFood>,
) {
    val sortedItems: List<MealItemWithFood> get() = items.sortedBy { it.item.position }
    val nutrients: Nutrients get() = items.fold(Nutrients.Zero) { acc, i -> acc + i.nutrients }
}

data class IngredientWithFood(
    @Embedded val ingredient: RecipeIngredient,
    @Relation(parentColumn = "foodId", entityColumn = "id") val food: Food?,
) {
    val nutrients: Nutrients get() = food?.per100?.forGrams(ingredient.grams) ?: Nutrients.Zero
}

data class RecipeWithIngredients(
    @Embedded val recipe: Food,
    @Relation(entity = RecipeIngredient::class, parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientWithFood>,
) {
    val sortedIngredients: List<IngredientWithFood> get() = ingredients.sortedBy { it.ingredient.position }
}

/** Per-day sums from [LogDao.observeTotals]. */
data class DayTotals(
    val day: Long,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
) {
    val nutrients: Nutrients get() = Nutrients(kcal, protein, carbs, fat, fiber)
}
