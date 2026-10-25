package app.tare.domain

import app.tare.data.db.Food
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class NutrientsTest {
    @Test
    fun forGramsScalesPer100Values() {
        val banana = Nutrients(kcal = 89.0, protein = 1.1, carbs = 22.8, fat = 0.3)
        val medium = banana.forGrams(118.0)
        assertThat(medium.kcal).isWithin(0.01).of(105.02)
        assertThat(medium.carbs).isWithin(0.01).of(26.9)
    }

    @Test
    fun sumAddsEveryField() {
        val total = listOf(Nutrients(100.0, 10.0, 5.0, 2.0, 1.0), Nutrients(50.0, 1.0, 1.0, 1.0, 0.5)).sum()
        assertThat(total).isEqualTo(Nutrients(150.0, 11.0, 6.0, 3.0, 1.5))
    }

    @Test
    fun macroKcalUsesAtwaterFactors() {
        assertThat(Targets(kcal = 2000, protein = 150, carbs = 200, fat = 70).macroKcal).isEqualTo(150 * 4 + 200 * 4 + 70 * 9)
    }
}

class SearchTextTest {
    @Test
    fun normalizationStripsAccentsCaseAndPunctuation() {
        assertThat(normalizeForSearch("Crème fraîche, 30%")).isEqualTo("creme fraiche 30%")
        assertThat(searchTokens("  Kip-FILET  ")).containsExactly("kip", "filet").inOrder()
    }

    @Test
    fun searchTextIncludesAliases() {
        assertThat(buildSearchText("Banana", null, "banaan|bananen")).isEqualTo("banana banaan bananen")
    }
}

class RankingTest {
    private fun food(id: String, name: String, popularity: Int = 0, useCount: Int = 0, aliases: String? = null, source: FoodSource = FoodSource.GENERIC) =
        Food(
            id = id,
            source = source,
            name = name,
            aliases = aliases,
            searchText = buildSearchText(name, null, aliases),
            per100 = Nutrients(100.0),
            popularity = popularity,
            useCount = useCount,
            createdAt = 0,
            updatedAt = 0,
        )

    @Test
    fun exactAndShortNamesBeatLongVariants() {
        val candidates = listOf(
            food("a", "Bananas, dehydrated, or banana powder"),
            food("b", "Banana", popularity = 98),
            food("c", "Banana bread"),
        )
        assertThat(rankFoods(candidates, "banana", now = 0, limit = 10).map { it.id }).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun dutchAliasMatchesAsExact() {
        val candidates = listOf(food("kip", "Chicken breast, roasted", popularity = 95, aliases = "kipfilet|kip"), food("x", "Kipper, smoked"))
        assertThat(rankFoods(candidates, "kipfilet", now = 0, limit = 10).map { it.id }).containsExactly("kip")
    }

    @Test
    fun foodsYouEatOftenRiseAbovePopularOnes() {
        val candidates = listOf(food("popular", "Greek yogurt, plain", popularity = 80), food("mine", "Greek yogurt, honey", useCount = 25))
        assertThat(rankFoods(candidates, "greek yogurt", now = 0, limit = 10).first().id).isEqualTo("mine")
    }

    @Test
    fun everyTokenMustMatch() {
        val candidates = listOf(food("a", "White rice, cooked"), food("b", "Brown rice, cooked"))
        assertThat(rankFoods(candidates, "rice white", now = 0, limit = 10).map { it.id }).containsExactly("a")
    }
}

class TrendTest {
    @Test
    fun trendStartsAtFirstWeighInAndMovesTenPercentPerDay() {
        val trend = weightTrend(listOf(WeightPoint(0, 80.0), WeightPoint(1, 81.0)))
        assertThat(trend[0].trendKg).isEqualTo(80.0)
        assertThat(trend[1].trendKg).isWithin(1e-9).of(80.1)
    }

    @Test
    fun gapsCompoundTheSmoothing() {
        // Two days without weighing → alpha = 1 - 0.9² = 0.19.
        val trend = weightTrend(listOf(WeightPoint(0, 80.0), WeightPoint(2, 81.0)))
        assertThat(trend[1].trendKg).isWithin(1e-9).of(80.19)
    }

    @Test
    fun unsortedInputIsSorted() {
        val trend = weightTrend(listOf(WeightPoint(5, 79.0), WeightPoint(1, 80.0)))
        assertThat(trend.map { it.day }).containsExactly(1L, 5L).inOrder()
    }
}

class DayPartTest {
    private val zone = java.time.ZoneId.of("Europe/Amsterdam")
    private val today = java.time.LocalDate.of(2026, 10, 25)
    private fun at(hour: Int, minute: Int = 0) = today.atTime(hour, minute).atZone(zone)

    @Test
    fun currentPartOfTodayLogsNow() {
        assertThat(logTimeFor(DayPart.AFTERNOON, today, at(13, 10))).isNull()
    }

    @Test
    fun breakfastLoggedAtLunchGoesToTheMorning() {
        assertThat(logTimeFor(DayPart.MORNING, today, at(13, 10))).isEqualTo(java.time.LocalTime.of(8, 0))
    }

    @Test
    fun pastDaysAlwaysUseThePartsUsualTime() {
        assertThat(logTimeFor(DayPart.AFTERNOON, today.minusDays(1), at(13, 10))).isEqualTo(java.time.LocalTime.of(12, 30))
    }

    @Test
    fun partBoundaries() {
        assertThat(DayPart.of(java.time.LocalTime.of(3, 59))).isEqualTo(DayPart.NIGHT)
        assertThat(DayPart.of(java.time.LocalTime.of(4, 0))).isEqualTo(DayPart.MORNING)
        assertThat(DayPart.of(java.time.LocalTime.of(11, 0))).isEqualTo(DayPart.AFTERNOON)
        assertThat(DayPart.of(java.time.LocalTime.of(17, 0))).isEqualTo(DayPart.EVENING)
        assertThat(DayPart.of(java.time.LocalTime.of(22, 0))).isEqualTo(DayPart.NIGHT)
    }
}

class LooksLikeAMealTest {
    @Test
    fun mealDescriptions() {
        listOf("2 eggs and toast", "boterham met kaas", "chicken, rice, broccoli", "200 g chicken breast", "a big bowl of yogurt with granola")
            .forEach { assertWithMessage(it).that(looksLikeAMeal(it)).isTrue() }
    }

    @Test
    fun foodNames() {
        listOf("banaan", "greek yogurt", "chicken breast roasted", "skyr 0%", "white rice")
            .forEach { assertWithMessage(it).that(looksLikeAMeal(it)).isFalse() }
    }
}

class CustomPortionsTest {
    @Test
    fun customPortionsSurviveAndWinOnClashes() {
        val fresh = listOf(Portion("medium", 118.0), Portion("cup", 150.0))
        val existing = listOf(Portion("medium", 118.0), Portion("my bowl", 350.0, custom = true), Portion("Cup", 200.0, custom = true))
        assertThat(mergeCustomPortions(fresh, existing)).containsExactly(
            Portion("medium", 118.0),
            Portion("my bowl", 350.0, custom = true),
            Portion("Cup", 200.0, custom = true),
        ).inOrder()
    }
}

class ConvertersTest {
    @Test
    fun portionsFromNewerVersionsStillParse() {
        val parsed = app.tare.data.db.Converters().portionsFromJson("""[{"label":"cup","grams":150.0,"futureField":"x"}]""")
        assertThat(parsed).containsExactly(Portion("cup", 150.0))
    }
}
