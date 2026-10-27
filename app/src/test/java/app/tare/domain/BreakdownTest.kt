package app.tare.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BreakdownTest {
    @Test
    fun energySplitWeighsFatAtNineKcalPerGram() {
        // 30 g protein, 50 g carbs, 20 g fat: 120 + 200 + 180 = 500 kcal from macros.
        val split = Nutrients(kcal = 510.0, protein = 30.0, carbs = 50.0, fat = 20.0).energySplit()!!
        assertThat(split.protein).isWithin(1e-9).of(0.24)
        assertThat(split.carbs).isWithin(1e-9).of(0.40)
        assertThat(split.fat).isWithin(1e-9).of(0.36)
    }

    @Test
    fun caloriesOnlyHaveNoSplit() {
        assertThat(Nutrients(kcal = 450.0).energySplit()).isNull()
    }

    @Test
    fun wholePercentsAlwaysAddUpToHundred() {
        assertThat(wholePercents(listOf(1.0, 1.0, 1.0))).containsExactly(34, 33, 33).inOrder()
        assertThat(wholePercents(listOf(0.244, 0.4, 0.356))).containsExactly(24, 40, 36).inOrder()
        assertThat(wholePercents(listOf(0.666, 0.334, 0.0))).containsExactly(67, 33, 0).inOrder()
        assertThat(wholePercents(listOf(0.0, 0.0))).containsExactly(0, 0).inOrder()
    }

    @Test
    fun sharesAreRankedBiggestFirstWithFractionsOfTheTotal() {
        val ranked = rankShares(listOf("rice" to 6.0, "chicken" to 50.0, "water" to 0.0, "broccoli" to 4.0)) { it.second }
        assertThat(ranked.map { it.item.first }).containsExactly("chicken", "rice", "broccoli", "water").inOrder()
        assertThat(ranked.first().fraction).isWithin(1e-9).of(50.0 / 60.0)
        assertThat(ranked.last().fraction).isEqualTo(0.0)
    }

    @Test
    fun sharesOfNothingAreZero() {
        val ranked = rankShares(listOf(1, 2)) { 0.0 }
        assertThat(ranked.map { it.fraction }).containsExactly(0.0, 0.0)
        assertThat(ranked.map { it.item }).containsExactly(1, 2).inOrder()
    }
}
