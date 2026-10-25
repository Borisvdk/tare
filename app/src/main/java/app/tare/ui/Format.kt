package app.tare.ui

import app.tare.data.db.LogEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Display formatting. English UI, metric units, "1,240" style grouping. */
object Fmt {
    private val locale = Locale.UK

    fun kcal(value: Double): String = String.format(locale, "%,d", value.roundToLong())

    fun kcal(value: Int): String = String.format(locale, "%,d", value)

    /** Grams of a macro: whole numbers, one decimal below 10 g ("4.5 g" but "32 g"). */
    fun grams(value: Double): String = if (abs(value) < 9.95 && value % 1.0 != 0.0) {
        String.format(locale, "%.1f", value)
    } else {
        value.roundToInt().toString()
    }

    /** An amount the user typed or picked: drops trailing zeros ("1.5", "2", "0.25"). */
    fun amount(value: Double): String {
        val rounded = (value * 100).roundToLong() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString().trimEnd('0').trimEnd('.')
    }

    fun items(count: Int): String = if (count == 1) "1 item" else "$count items"

    fun weight(kg: Double): String = String.format(locale, "%.1f", kg)

    fun signedWeight(kg: Double): String = (if (kg > 0) "+" else if (kg < 0) "−" else "±") + String.format(locale, "%.1f", abs(kg))

    /** "150 g", "1 × slice · 35 g", "2 × serving · 60 g". */
    fun entryAmount(entry: LogEntry): String {
        val grams = entry.grams ?: return "Quick add"
        val unit = if (entry.isLiquid) "ml" else "g"
        val portionGrams = entry.portionGrams
        val label = entry.portionLabel
        return if (label != null && portionGrams != null && portionGrams > 0) {
            "${amount(grams / portionGrams)} × $label · ${grams.roundToInt()} $unit"
        } else {
            "${amount(grams)} $unit"
        }
    }

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val shortDayFormat = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    fun dayHeadline(day: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(day, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        -1L -> "Tomorrow"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE", locale))
    }

    fun daySubtitle(day: LocalDate): String = day.format(dayFormat)

    fun shortDay(day: LocalDate): String = day.format(shortDayFormat)

    fun weekdayLetter(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("EEEEE", locale))
}
