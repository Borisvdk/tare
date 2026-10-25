package app.tare.domain

import app.tare.data.db.Food
import kotlin.math.ln
import kotlin.math.max

private const val DAY_MS = 86_400_000.0

/**
 * Orders local search candidates. The goal is that the food you mean is the first row:
 * exact and prefix matches win, then foods you actually eat (use count, recency), then
 * curated popularity of bundled foods. Shorter names break ties ("Banana" over
 * "Banana, dehydrated, or banana powder").
 */
fun rankFoods(candidates: List<Food>, query: String, now: Long, limit: Int): List<Food> {
    val tokens = searchTokens(query)
    if (tokens.isEmpty()) return emptyList()
    val joined = tokens.joinToString(" ")
    return candidates
        .filter { food -> tokens.all { food.searchText.contains(it) } }
        .map { it to scoreFood(it, tokens, joined, now) }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
}

internal fun scoreFood(food: Food, tokens: List<String>, query: String, now: Long): Double {
    val name = normalizeForSearch(food.name)
    val aliases = food.aliases?.split('|')?.map(::normalizeForSearch).orEmpty()
    val words = name.split(' ')

    var score = when {
        name == query || aliases.any { it == query } -> 100.0
        name.startsWith(query) || aliases.any { it.startsWith(query) } -> 70.0
        words.firstOrNull()?.startsWith(tokens.first()) == true -> 55.0
        tokens.all { t -> words.any { it.startsWith(t) } } -> 40.0
        else -> 15.0
    }

    score += ln(1.0 + food.useCount) * 18.0
    food.lastUsedAt?.let { last ->
        val daysAgo = max(0.0, (now - last) / DAY_MS)
        score += 20.0 / (1.0 + daysAgo / 7.0)
    }
    if (food.favorite) score += 25.0
    score += when (food.source) {
        FoodSource.CUSTOM, FoodSource.RECIPE -> 12.0
        FoodSource.GENERIC -> 4.0 + food.popularity * 0.35
        FoodSource.OPEN_FOOD_FACTS -> 2.0
        FoodSource.AI, FoodSource.QUICK -> 0.0
    }
    score -= name.length * 0.15
    return score
}

/**
 * Re-ranks Open Food Facts hits, whose own relevance is noisy: prefer products sold in the
 * Netherlands, scanned by many people, whose name actually matches the query.
 */
fun rankRemote(products: List<Pair<Food, RemoteSignals>>, query: String): List<Food> {
    val tokens = searchTokens(query)
    val joined = tokens.joinToString(" ")
    return products
        .map { (food, signals) ->
            val name = normalizeForSearch(food.name)
            var score = when {
                name == joined -> 40.0
                name.startsWith(joined) -> 30.0
                tokens.all { food.searchText.contains(it) } -> 20.0
                else -> 0.0
            }
            score += ln(1.0 + signals.scans) * 6.0
            if (signals.soldInNetherlands) score += 15.0
            score -= name.length * 0.05
            food to score
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

data class RemoteSignals(val scans: Int, val soldInNetherlands: Boolean)
