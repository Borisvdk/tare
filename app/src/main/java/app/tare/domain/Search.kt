package app.tare.domain

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9%]+")

/**
 * Lowercases, strips diacritics and collapses punctuation so "Crème fraîche, 30%" and
 * "creme fraiche 30%" compare equal. Used both for stored search text and for queries.
 */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace(NON_ALPHANUMERIC, " ")
        .trim()

fun searchTokens(query: String): List<String> =
    normalizeForSearch(query).split(' ').filter { it.isNotBlank() }

fun buildSearchText(name: String, brand: String?, aliases: String?): String =
    normalizeForSearch(listOfNotNull(name, brand, aliases?.replace('|', ' ')).joinToString(" "))

private val MEAL_CONNECTORS = setOf("and", "with", "en", "met", "plus")

/**
 * True when a search reads like a description of a meal ("2 eggs and toast", "boterham met
 * kaas") rather than the name of one food, so Describe is the better first suggestion.
 */
fun looksLikeAMeal(query: String): Boolean {
    val tokens = searchTokens(query)
    if (tokens.size < 2) return false
    val hasNumber = tokens.any { t -> t.any(Char::isDigit) }
    val hasConnector = tokens.any { it in MEAL_CONNECTORS } || query.contains(',')
    return tokens.size >= 5 || (hasConnector && tokens.size >= 3) || (hasNumber && tokens.size >= 3)
}

/**
 * Typos tolerated in a token. None up to four letters: "liga", "hero" and "lays" are brands,
 * and one edit away from lima, hert and lams.
 */
fun typoBudget(token: String): Int = when {
    token.any(Char::isDigit) -> 0
    token.length >= 8 -> 2
    token.length >= 5 -> 1
    else -> 0
}

/**
 * Edit distance counting a swap of two neighbouring letters as one edit ("brocolli"), or
 * [max] + 1 as soon as it's clear the distance is larger.
 */
fun editDistance(a: String, b: String, max: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > max) return max + 1
    var prevPrev = IntArray(b.length + 1)
    var prev = IntArray(b.length + 1) { it }
    var row = IntArray(b.length + 1)
    for (i in 1..a.length) {
        row[0] = i
        var best = row[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var d = minOf(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d = minOf(d, prevPrev[j - 2] + 1)
            row[j] = d
            if (d < best) best = d
        }
        if (best > max) return max + 1
        val recycled = prevPrev
        prevPrev = prev
        prev = row
        row = recycled
    }
    return prev[b.length]
}

/**
 * Fixes the typos in a search that found nothing: every token that appears in no word of
 * [vocabulary] (word → how many foods use it) is replaced by the closest whole word within
 * its [typoBudget]. A matching first letter breaks ties (typos rarely hit it), then the more
 * common word wins. Returns null when there's nothing to fix or no fix is close enough.
 */
fun correctTypos(tokens: List<String>, vocabulary: Map<String, Int>): List<String>? {
    var changed = false
    val corrected = tokens.map { token ->
        if (vocabulary.keys.any { it.contains(token) }) return@map token
        val budget = typoBudget(token)
        if (budget == 0) return null
        var best: String? = null
        var bestScore = Double.MAX_VALUE
        for ((word, count) in vocabulary) {
            val distance = editDistance(token, word, budget)
            if (distance > budget) continue
            val score = distance + (if (word[0] != token[0]) 0.5 else 0.0) - minOf(count, 50) / 1000.0
            if (score < bestScore) {
                bestScore = score
                best = word
            }
        }
        best?.also { changed = true } ?: return null
    }
    return corrected.takeIf { changed }
}

/** True when [searchText] contains every token, the same test local search applies in SQL. */
fun matchesAll(searchText: String, tokens: List<String>): Boolean = tokens.all { searchText.contains(it) }
