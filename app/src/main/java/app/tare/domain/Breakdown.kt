package app.tare.domain

import kotlin.math.floor

/** Where calories come from, as fractions of the energy in protein, carbs and fat (4/4/9 kcal per g). */
data class EnergySplit(val protein: Double, val carbs: Double, val fat: Double) {
    val fractions: List<Double> get() = listOf(protein, carbs, fat)
}

/** Null when there are no macros to split (quick adds with only calories). */
fun Nutrients.energySplit(): EnergySplit? {
    val p = protein.coerceAtLeast(0.0) * 4
    val c = carbs.coerceAtLeast(0.0) * 4
    val f = fat.coerceAtLeast(0.0) * 9
    val total = p + c + f
    if (total <= 0.0) return null
    return EnergySplit(p / total, c / total, f / total)
}

/**
 * Whole percentages that add up to exactly 100 (largest remainder), so a split shown side by
 * side never reads "34% 33% 32%".
 */
fun wholePercents(fractions: List<Double>): List<Int> {
    val total = fractions.sum()
    if (total <= 0.0) return fractions.map { 0 }
    val exact = fractions.map { it / total * 100 }
    val floors = exact.map { floor(it).toInt() }
    val missing = 100 - floors.sum()
    val bumped = exact.indices.sortedByDescending { exact[it] - floors[it] }.take(missing).toSet()
    return floors.mapIndexed { i, f -> if (i in bumped) f + 1 else f }
}

data class Share<T>(val item: T, val value: Double, val fraction: Double)

/** [items] biggest first by [value], each with its fraction of the total. Ties keep their order. */
fun <T> rankShares(items: List<T>, value: (T) -> Double): List<Share<T>> {
    val values = items.map { value(it).coerceAtLeast(0.0) }
    val total = values.sum()
    return items.zip(values) { item, v -> Share(item, v, if (total > 0.0) v / total else 0.0) }
        .sortedByDescending { it.value }
}
