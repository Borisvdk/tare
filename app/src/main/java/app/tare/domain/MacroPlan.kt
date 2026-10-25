package app.tare.domain

import kotlin.math.roundToInt

/**
 * How the macro targets follow the calorie target. Protein comes from body weight (g per kg)
 * or is a fixed amount; fat is a share of calories or a fixed amount; carbs fill the rest.
 * Null means "fixed grams": the number typed in stays put when calories change.
 */
data class MacroPlan(val proteinPerKg: Double? = 1.8, val fatPercent: Int? = 30) {
    companion object {
        /** 1.6–2.2 g/kg keeps muscle; the top end matters most while eating less. */
        val PROTEIN_PER_KG = listOf(1.6, 1.8, 2.0, 2.2)

        /** Below ~20% hormones and satiety suffer; above ~35% crowds out carbs and protein. */
        val FAT_PERCENT = listOf(25, 30, 35)

        /** For targets saved before plans existed: protein stays as typed, fat keeps its share. */
        fun inferFrom(targets: Targets): MacroPlan =
            MacroPlan(proteinPerKg = null, fatPercent = if (targets.kcal > 0) (targets.fat * 9 * 100.0 / targets.kcal).roundToInt() else 30)
    }
}

/** Protein for [weightKg] at [perKg], rounded to 5 g. */
fun proteinGrams(weightKg: Double, perKg: Double): Int = ((weightKg * perKg) / 5).roundToInt() * 5

fun fatGrams(kcal: Int, percent: Int): Int = (kcal * percent / 900.0).roundToInt()

/** Whatever calories protein and fat leave, as carbs (never negative). */
fun carbsGrams(kcal: Int, protein: Int, fat: Int): Int = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)

/**
 * Targets for [kcal] under [plan]. [weightKg] feeds g/kg protein; [protein] and [fat] are the
 * typed amounts used where the plan says "fixed".
 */
fun planTargets(kcal: Int, plan: MacroPlan, weightKg: Double?, protein: Int, fat: Int): Targets {
    val p = plan.proteinPerKg?.takeIf { weightKg != null }?.let { proteinGrams(weightKg!!, it) } ?: protein
    val f = plan.fatPercent?.let { fatGrams(kcal, it) } ?: fat
    return Targets(kcal = kcal, protein = p, carbs = carbsGrams(kcal, p, f), fat = f)
}
