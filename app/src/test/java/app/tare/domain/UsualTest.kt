package app.tare.domain

import app.tare.data.db.LogEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class UsualTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val start = LocalDate.of(2026, 10, 1)

    private fun entry(food: String, dayOffset: Long, time: String, grams: Double, portion: String? = null): LogEntry {
        val day = start.plusDays(dayOffset)
        return LogEntry(
            day = day.toEpochDay(),
            loggedAt = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli(),
            foodId = food,
            name = food,
            source = FoodSource.GENERIC,
            grams = grams,
            portionLabel = portion,
            nutrients = Nutrients.Zero,
        )
    }

    private val log = listOf(
        entry("oats", 0, "07:30", 60.0),
        entry("oats", 1, "08:10", 60.0),
        entry("oats", 2, "07:50", 80.0),
        entry("milk", 0, "07:30", 250.0),
        entry("milk", 1, "07:35", 250.0),
        entry("milk", 1, "19:00", 150.0),
        entry("milk", 2, "19:10", 150.0),
        entry("banana", 2, "10:00", 118.0, "medium"),
        entry("rice", 0, "18:30", 75.0),
    )

    @Test
    fun keepsFoodsFromAtLeastTwoDaysInThatPart() {
        assertThat(usualFoods(log, DayPart.MORNING, zone).map { it.foodId }).containsExactly("oats", "milk").inOrder()
        assertThat(usualFoods(log, DayPart.EVENING, zone).map { it.foodId }).containsExactly("milk")
        assertThat(usualFoods(log, DayPart.NIGHT, zone)).isEmpty()
    }

    @Test
    fun usesTheAmountYouHaveMostOftenInThatPart() {
        assertThat(usualFoods(log, DayPart.MORNING, zone).first { it.foodId == "milk" }.grams).isEqualTo(250.0)
        assertThat(usualFoods(log, DayPart.EVENING, zone).single().grams).isEqualTo(150.0)
        assertThat(usualFoods(log, DayPart.MORNING, zone).first { it.foodId == "oats" }.grams).isEqualTo(60.0)
    }

    @Test
    fun aTieGoesToTheLatestAmount() {
        val tied = listOf(entry("oats", 0, "08:00", 60.0), entry("oats", 1, "08:00", 80.0))
        assertThat(usualFoods(tied, DayPart.MORNING, zone).single().grams).isEqualTo(80.0)
    }

    @Test
    fun partsReadNaturallyInHeaders() {
        assertThat("Usually ${DayPart.MORNING.phrase}").isEqualTo("Usually in the morning")
        assertThat("Usually ${DayPart.NIGHT.phrase}").isEqualTo("Usually at night")
    }
}
