package app.tare.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The four blocks a day is shown in. No breakfast/lunch/dinner labels: when you ate decides
 * the block, so there's nothing to pick in the common case.
 */
enum class DayPart(val label: String, val defaultTime: LocalTime) {
    MORNING("Morning", LocalTime.of(8, 0)),
    AFTERNOON("Afternoon", LocalTime.of(12, 30)),
    EVENING("Evening", LocalTime.of(18, 30)),
    NIGHT("Night", LocalTime.of(22, 0)),
    ;

    companion object {
        fun of(time: LocalTime): DayPart = when (time.hour) {
            in 4..10 -> MORNING
            in 11..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}

/**
 * When to log something meant for [part] of [day]: right now if that's the part of today
 * we're in, otherwise the part's usual time (breakfast logged at lunch lands in the morning).
 * Null means "now".
 */
fun logTimeFor(part: DayPart, day: LocalDate, now: ZonedDateTime): LocalTime? =
    if (day == now.toLocalDate() && DayPart.of(now.toLocalTime()) == part) null else part.defaultTime
