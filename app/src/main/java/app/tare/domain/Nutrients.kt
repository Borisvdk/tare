package app.tare.domain

import kotlinx.serialization.Serializable

/**
 * Energy and macronutrients. Foods store these per 100 g (or 100 ml); log entries store
 * absolute totals for the amount eaten.
 */
@Serializable
data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
        fiber = fiber + other.fiber,
    )

    operator fun minus(other: Nutrients) = this + other * -1.0

    operator fun times(factor: Double) = Nutrients(
        kcal = kcal * factor,
        protein = protein * factor,
        carbs = carbs * factor,
        fat = fat * factor,
        fiber = fiber * factor,
    )

    /** Treats this value as per-100 g and returns the totals for [grams]. */
    fun forGrams(grams: Double): Nutrients = this * (grams / 100.0)

    companion object {
        val Zero = Nutrients()
    }
}

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.Zero) { acc, n -> acc + n }

/** A household measure for a food, e.g. "slice" = 35 g. [grams] is the weight of one unit. */
@Serializable
data class Portion(val label: String, val grams: Double)

enum class FoodSource {
    /** Bundled USDA whole foods. */
    GENERIC,

    /** Open Food Facts product, usually from a barcode. */
    OPEN_FOOD_FACTS,

    /** Created by hand in the food editor. */
    CUSTOM,

    /** Built from ingredients in the recipe editor. */
    RECIPE,

    /** Estimated by Claude from a description or photo. */
    AI,

    /** Raw calories/macros typed in without a food. Only used on log entries. */
    QUICK,
}

@Serializable
data class Targets(
    val kcal: Int = 2200,
    val protein: Int = 150,
    val carbs: Int = 230,
    val fat: Int = 75,
) {
    /** Calories implied by the macro targets (4/4/9). */
    val macroKcal: Int get() = protein * 4 + carbs * 4 + fat * 9
}
