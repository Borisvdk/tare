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
