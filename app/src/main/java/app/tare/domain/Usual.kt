package app.tare.domain

import app.tare.data.db.LogEntry
import java.time.Instant
import java.time.ZoneId

/** A food you keep eating in one part of the day, with the amount you usually have. */
data class UsualFood(val foodId: String, val days: Int, val grams: Double, val portionLabel: String?)

/**
 * Foods logged in [part] on at least [minDays] different days among [entries], most days
 * first (then most recent). The amount is the one you log most often there, so the morning
 * milk can be 250 ml while the evening one is 150.
 */
fun usualFoods(entries: List<LogEntry>, part: DayPart, zone: ZoneId, minDays: Int = 2, limit: Int = 6): List<UsualFood> =
    entries
        .filter { it.foodId != null && it.grams != null && DayPart.of(Instant.ofEpochMilli(it.loggedAt).atZone(zone).toLocalTime()) == part }
        .groupBy { it.foodId!! }
        .mapNotNull { (foodId, logged) ->
            val days = logged.map { it.day }.toSet().size
            if (days < minDays) return@mapNotNull null
            // Most frequent amount; the latest one wins a tie.
            val amount = logged.groupBy { it.grams to it.portionLabel }
                .maxWith(compareBy<Map.Entry<Pair<Double?, String?>, List<LogEntry>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.loggedAt } })
                .key
            Triple(UsualFood(foodId, days, amount.first!!, amount.second), days, logged.maxOf { it.loggedAt })
        }
        .sortedWith(compareByDescending<Triple<UsualFood, Int, Long>> { it.second }.thenByDescending { it.third })
        .take(limit)
        .map { it.first }

/** "in the morning", "at night": for headers like "Usually in the morning". */
val DayPart.phrase: String
    get() = when (this) {
        DayPart.NIGHT -> "at night"
        else -> "in the ${label.lowercase()}"
    }
