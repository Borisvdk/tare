package app.tare.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MacroPlanTest {
    @Test
    fun caloriesDriveFatAndCarbsWhileProteinFollowsWeight() {
        val plan = MacroPlan(proteinPerKg = 1.8, fatPercent = 30)
        val at2200 = planTargets(2200, plan, weightKg = 82.0, protein = 0, fat = 0)
        assertThat(at2200).isEqualTo(Targets(kcal = 2200, protein = 150, carbs = 236, fat = 73))

        // Eating less: protein stays, fat keeps its share, carbs give way.
        val at1800 = planTargets(1800, plan, weightKg = 82.0, protein = 0, fat = 0)
        assertThat(at1800.protein).isEqualTo(150)
        assertThat(at1800.fat).isEqualTo(60)
        assertThat(at1800.carbs).isEqualTo(165)
        assertThat(at1800.macroKcal).isWithin(10).of(1800)
    }

    @Test
    fun typedAmountsStayPutWhenFixed() {
        val plan = MacroPlan(proteinPerKg = null, fatPercent = null)
        val targets = planTargets(2000, plan, weightKg = 80.0, protein = 170, fat = 60)
        assertThat(targets.protein).isEqualTo(170)
        assertThat(targets.fat).isEqualTo(60)
        assertThat(targets.carbs).isEqualTo((2000 - 170 * 4 - 60 * 9) / 4)
    }

    @Test
    fun withoutAWeightProteinStaysAsTyped() {
        val targets = planTargets(2000, MacroPlan(proteinPerKg = 2.0, fatPercent = 30), weightKg = null, protein = 140, fat = 0)
        assertThat(targets.protein).isEqualTo(140)
    }

    @Test
    fun carbsNeverGoNegative() {
        assertThat(carbsGrams(kcal = 1200, protein = 200, fat = 80)).isEqualTo(0)
    }

    @Test
    fun oldTargetsKeepTheirFatShare() {
        val plan = MacroPlan.inferFrom(Targets(kcal = 2200, protein = 150, carbs = 230, fat = 75))
        assertThat(plan.proteinPerKg).isNull()
        assertThat(plan.fatPercent).isEqualTo(31)
    }

    @Test
    fun proteinRoundsToFiveGrams() {
        assertThat(proteinGrams(77.3, 1.6)).isEqualTo(125)
        assertThat(proteinGrams(90.0, 2.2)).isEqualTo(200)
    }
}
