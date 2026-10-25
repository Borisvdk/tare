package app.tare.ui.food

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tare.AppGraph
import app.tare.data.ai.AiResult
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.buildSearchText
import app.tare.ui.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class Basis { PER_100, PER_SERVING }

/** Form state for creating or editing a custom food. Numbers are kept as typed text. */
class FoodEditorViewModel(
    private val graph: AppGraph,
    private val foodId: String?,
    barcode: String?,
    name: String,
) : ViewModel() {
    var name by mutableStateOf(name)
    var brand by mutableStateOf("")
    var barcode by mutableStateOf(barcode.orEmpty())
    var kcal by mutableStateOf("")
    var protein by mutableStateOf("")
    var carbs by mutableStateOf("")
    var fat by mutableStateOf("")
    var fiber by mutableStateOf("")
    var servingGrams by mutableStateOf("")
    var servingLabel by mutableStateOf("serving")
    var isLiquid by mutableStateOf(false)
    var basis by mutableStateOf(Basis.PER_100)

    var readingLabel by mutableStateOf(false)
        private set
    var labelError by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(foodId == null)
        private set
    var original: Food? = null
        private set

    init {
        if (foodId != null) {
            viewModelScope.launch {
                graph.foods.get(foodId)?.let(::fill)
                loaded = true
            }
        }
    }

    private fun fill(food: Food) {
        original = food
        name = food.name
        brand = food.brand.orEmpty()
        barcode = food.barcode.orEmpty()
        kcal = Fmt.amount(food.per100.kcal)
        protein = Fmt.amount(food.per100.protein)
        carbs = Fmt.amount(food.per100.carbs)
        fat = Fmt.amount(food.per100.fat)
        fiber = food.per100.fiber.takeIf { it > 0 }?.let(Fmt::amount).orEmpty()
        food.portions.firstOrNull()?.let {
            servingGrams = Fmt.amount(it.grams)
            servingLabel = it.label
        }
        isLiquid = food.isLiquid
        basis = Basis.PER_100
    }

    private fun num(text: String): Double? = text.replace(',', '.').trim().toDoubleOrNull()

    val serving: Double? get() = num(servingGrams)?.takeIf { it > 0 }

    /** What the user typed, converted to per-100 values. */
    val per100: Nutrients
        get() {
            val factor = if (basis == Basis.PER_SERVING) serving?.let { 100.0 / it } ?: 0.0 else 1.0
            val p = num(protein) ?: 0.0
            val c = num(carbs) ?: 0.0
            val f = num(fat) ?: 0.0
            val k = num(kcal) ?: (p * 4 + c * 4 + f * 9)
            return Nutrients(k, p, c, f, num(fiber) ?: 0.0) * factor
        }

    val canSave: Boolean
        get() = name.isNotBlank() && per100.kcal > 0 && (basis == Basis.PER_100 || serving != null)

    fun readLabel(photo: ByteArray) {
        readingLabel = true
        labelError = null
        viewModelScope.launch {
            when (val result = graph.ai.readLabel(photo)) {
                is AiResult.Success -> {
                    val l = result.value
                    if (name.isBlank() || name == original?.name) name = l.name
                    if (brand.isBlank()) brand = l.brand
                    basis = Basis.PER_100
                    kcal = Fmt.amount(l.kcalPer100g)
                    protein = Fmt.amount(l.proteinPer100g)
                    carbs = Fmt.amount(l.carbsPer100g)
                    fat = Fmt.amount(l.fatPer100g)
                    fiber = l.fiberPer100g.takeIf { it > 0 }?.let(Fmt::amount).orEmpty()
                    if (l.servingGrams > 0) servingGrams = Fmt.amount(l.servingGrams)
                    isLiquid = l.isLiquid
                }
                AiResult.NoApiKey -> labelError = "Add your Claude API key in Settings to read labels."
                is AiResult.Failed -> labelError = result.message
            }
            readingLabel = false
        }
    }

    suspend fun save(): Food {
        val at = graph.now()
        val portions = serving?.let { listOf(Portion(servingLabel.trim().ifBlank { "serving" }, it)) }.orEmpty()
        val base = original
        val draft = Food(
            id = base?.id.orEmpty(),
            source = base?.source ?: FoodSource.CUSTOM,
            name = name.trim(),
            brand = brand.trim().ifBlank { null },
            searchText = buildSearchText(name, brand, null),
            per100 = per100,
            portions = portions,
            barcode = barcode.trim().ifBlank { null },
            isLiquid = isLiquid,
            useCount = base?.useCount ?: 0,
            lastUsedAt = base?.lastUsedAt,
            favorite = base?.favorite ?: false,
            createdAt = base?.createdAt ?: at,
            updatedAt = at,
        )
        return graph.foods.saveCustom(draft).also { original = it }
    }

    suspend fun delete() {
        original?.let { graph.foods.hide(it.id) }
    }

    suspend fun log(food: Food, day: LocalDate, grams: Double, portion: Portion?, time: LocalTime?): LogEntry =
        graph.log.logFood(food, grams, portion, day, time)
}
