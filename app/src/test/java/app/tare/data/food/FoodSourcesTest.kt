package app.tare.data.food

import app.tare.domain.FoodSource
import app.tare.domain.Portion
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

class OpenFoodFactsMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun product(body: String) = json.decodeFromString(OffProductResponse.serializer(), body).product!!

    @Test
    fun mapsProductApiResponse() {
        val p = product(
            """
            {"status":1,"product":{"code":"5449000000996","product_name":"Coca-Cola Original","brands":"COCA-COLA SERVICES SA/NV",
             "serving_size":"1 can (330 ml)","serving_quantity":330,"product_quantity":330,"product_quantity_unit":"ml",
             "nutriments":{"energy-kcal_100g":42.4,"proteins_100g":0,"carbohydrates_100g":10.6,"fat_100g":0}}}
            """,
        )
        val food = p.toFood(now = 1)!!
        assertThat(food.id).isEqualTo("off:5449000000996")
        assertThat(food.source).isEqualTo(FoodSource.OPEN_FOOD_FACTS)
        assertThat(food.brand).isEqualTo("Coca-Cola Services Sa/Nv")
        assertThat(food.isLiquid).isTrue()
        assertThat(food.per100.kcal).isEqualTo(42.4)
        assertThat(food.portions).containsExactly(Portion("can", 330.0))
    }

    @Test
    fun fallsBackToKilojoulesAndStringNumbers() {
        val p = product(
            """{"status":1,"product":{"code":"1","product_name":"Magere kwark","brands":["Jumbo"],
            "nutriments":{"energy-kj_100g":"209","proteins_100g":"9,0","carbohydrates_100g":4,"fat_100g":0.1}}}""",
        )
        val food = p.toFood(now = 1)!!
        assertThat(food.per100.kcal).isWithin(0.1).of(49.95)
        assertThat(food.per100.protein).isEqualTo(9.0)
        assertThat(food.brand).isEqualTo("Jumbo")
        assertThat(food.portions).isEmpty()
    }

    @Test
    fun productsWithoutNutritionAreRejected() {
        val p = product("""{"status":1,"product":{"code":"2","product_name":"Mystery","nutriments":{}}}""")
        assertThat(p.toFood(now = 1)).isNull()
        assertThat(p.displayName).isEqualTo("Mystery")
    }

    @Test
    fun allCapsNamesAreTidied() {
        val p = product("""{"status":1,"product":{"code":"3","product_name":"SKYR NATUREL","nutriments":{"energy-kcal_100g":60,"proteins_100g":11}}}""")
        assertThat(p.toFood(now = 1)!!.name).isEqualTo("Skyr naturel")
    }
}

class GenericFoodParserTest {
    @Test
    fun portionsAreNormalizedToOneUnit() {
        assertThat(parsePortions("1 medium=118;0.5 cup, sliced=75;slice=30;bad;x=0"))
            .containsExactly(Portion("medium", 118.0), Portion("cup, sliced", 150.0), Portion("slice", 30.0))
            .inOrder()
    }

    @Test
    fun parsesHeaderDrivenTsv() {
        val tsv = "id\tname\taliases\tcategory\tkcal\tprotein\tcarbs\tfat\tfiber\tportions\tpopularity\n" +
            "usda:1\tBanana\tbanaan\tFruits\t89.0\t1.09\t22.84\t0.33\t2.6\t1 medium=118\t98\n" +
            "usda:2\tBroken\t\t\tnot-a-number\t\t\t\t\t\t\n"
        val foods = parseGenericFoods(tsv.byteInputStream(), now = 0)
        assertThat(foods).hasSize(1)
        assertThat(foods[0].searchText).isEqualTo("banana banaan")
        assertThat(foods[0].popularity).isEqualTo(98)
    }

    @Test
    fun bundledDatasetIsSane() {
        val file = listOf("src/main/assets/generic_foods.tsv", "app/src/main/assets/generic_foods.tsv").map(::File).first { it.exists() }
        val foods = file.inputStream().use { parseGenericFoods(it, now = 0) }
        assertThat(foods.size).isGreaterThan(7000)
        assertThat(foods.map { it.id }.toSet()).hasSize(foods.size)
        val banana = foods.first { it.name == "Banana" }
        assertThat(banana.per100.kcal).isWithin(2.0).of(89.0)
        assertThat(banana.aliases).contains("banaan")
        // Energy should roughly agree with the macros (Atwater 4/4/9) for nearly every food.
        val plausible = foods.count { f ->
            val p = f.per100
            val macroKcal = p.protein * 4 + p.carbs * 4 + p.fat * 9
            p.kcal < 50 || kotlin.math.abs(macroKcal - p.kcal) / p.kcal < 0.35
        }
        assertThat(plausible.toDouble() / foods.size).isGreaterThan(0.97)
    }
}
