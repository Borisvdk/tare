package app.tare.data.food

import app.tare.data.db.Food
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.buildSearchText
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Open Food Facts (ODbL). Barcode lookups use the v2 product API; text search uses
 * search-a-licious (Elasticsearch, ~200 ms) restricted to products sold in the Netherlands
 * with a worldwide fallback.
 */
class OpenFoodFactsClient(private val http: HttpClient) {

    suspend fun product(barcode: String): OffProduct? {
        val response = http.get("$PRODUCT_URL/$barcode") {
            parameter("fields", FIELDS)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        val body = response.body<OffProductResponse>()
        return body.product?.takeIf { body.status == 1 }
    }

    suspend fun search(query: String, netherlandsOnly: Boolean): List<OffProduct> {
        val cleaned = query.replace(LUCENE_SPECIAL, " ").trim()
        if (cleaned.isEmpty()) return emptyList()
        val q = if (netherlandsOnly) "$cleaned countries_tags:\"en:netherlands\"" else cleaned
        return http.get(SEARCH_URL) {
            parameter("q", q)
            parameter("langs", "nl,en")
            parameter("page_size", 40)
            parameter("fields", FIELDS)
        }.body<OffSearchResponse>().hits
    }

    companion object {
        private const val PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product"
        private const val SEARCH_URL = "https://search.openfoodfacts.org/search"
        private const val FIELDS =
            "code,product_name,product_name_nl,product_name_en,generic_name,brands,quantity," +
                "serving_size,serving_quantity,product_quantity,product_quantity_unit,nutriments," +
                "image_front_small_url,countries_tags,unique_scans_n"
        private val LUCENE_SPECIAL = Regex("[+\\-&|!(){}\\[\\]^\"~*?:\\\\/]")
    }
}

@Serializable
data class OffProductResponse(val status: Int = 0, val product: OffProduct? = null)

@Serializable
data class OffSearchResponse(val hits: List<OffProduct> = emptyList(), val count: Int = 0)

@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_nl") val productNameNl: String? = null,
    @SerialName("product_name_en") val productNameEn: String? = null,
    @SerialName("generic_name") val genericName: String? = null,
    /** A string from the product API, an array from search. */
    val brands: JsonElement? = null,
    val quantity: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @SerialName("serving_quantity") val servingQuantity: JsonElement? = null,
    @SerialName("product_quantity") val productQuantity: JsonElement? = null,
    @SerialName("product_quantity_unit") val productQuantityUnit: String? = null,
    val nutriments: Map<String, JsonElement>? = null,
    @SerialName("image_front_small_url") val imageUrl: String? = null,
    @SerialName("countries_tags") val countriesTags: List<String>? = null,
    @SerialName("unique_scans_n") val uniqueScans: Int? = null,
) {
    val soldInNetherlands: Boolean get() = countriesTags?.contains("en:netherlands") == true

    val displayName: String?
        get() = listOf(productName, productNameNl, productNameEn, genericName)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?.let(::tidyCase)

    val brand: String?
        get() {
            val raw = when (val b = brands) {
                is JsonArray -> b.firstOrNull()?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> b.contentOrNull?.split(',')?.firstOrNull()
                else -> null
            }
            return raw?.trim()?.takeIf { it.isNotEmpty() }?.let(::tidyBrand)
        }

    /** Per-100 g nutrients, or null when the product has no usable energy value. */
    val per100: Nutrients?
        get() {
            val n = nutriments ?: return null
            fun num(key: String): Double? = (n[key] as? JsonPrimitive)?.let {
                it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
            }
            val kcal = num("energy-kcal_100g")
                ?: num("energy-kj_100g")?.div(KJ_PER_KCAL)
                ?: num("energy_100g")?.div(KJ_PER_KCAL)
                ?: return null
            val protein = num("proteins_100g")
            val carbs = num("carbohydrates_100g")
            val fat = num("fat_100g")
            if (protein == null && carbs == null && fat == null && kcal > 0) return null
            return Nutrients(
                kcal = kcal,
                protein = protein ?: 0.0,
                carbs = carbs ?: 0.0,
                fat = fat ?: 0.0,
                fiber = num("fiber_100g") ?: 0.0,
            )
        }

    val isLiquid: Boolean
        get() = productQuantityUnit?.lowercase() in LIQUID_UNITS ||
            quantity?.let { LIQUID_QUANTITY.containsMatchIn(it.lowercase()) } == true

    val portions: List<Portion>
        get() = buildList {
            val serving = servingQuantity.asDouble()
            if (serving != null && serving > 0) add(Portion(servingLabel(servingSize), serving))
            val pack = productQuantity.asDouble()
            if (pack != null && pack in 1.0..MAX_SINGLE_PACK && (serving == null || pack != serving)) {
                add(Portion("package", pack))
            }
        }

    fun toFood(now: Long): Food? {
        val barcode = code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = displayName ?: return null
        val nutrients = per100 ?: return null
        val brand = brand
        return Food(
            id = "off:$barcode",
            source = FoodSource.OPEN_FOOD_FACTS,
            name = name,
            brand = brand,
            searchText = buildSearchText(name, brand, null),
            per100 = nutrients,
            portions = portions,
            barcode = barcode,
            imageUrl = imageUrl,
            isLiquid = isLiquid,
            popularity = (uniqueScans ?: 0).coerceAtMost(100),
            createdAt = now,
            updatedAt = now,
        )
    }
}

private const val KJ_PER_KCAL = 4.184
private const val MAX_SINGLE_PACK = 600.0
private val LIQUID_UNITS = setOf("ml", "cl", "l")
private val LIQUID_QUANTITY = Regex("\\d\\s*(ml|cl|l)\\b")
private val SERVING_WORD = Regex("^\\s*[\\d.,/]*\\s*([a-zA-Z][a-zA-Z ]{1,18}?)\\s*\\(")
private val UNIT_WORDS = setOf("g", "gr", "gram", "grams", "ml", "cl", "l", "portion", "portie", "serving")

private fun servingLabel(servingSize: String?): String {
    val word = servingSize?.let { SERVING_WORD.find(it)?.groupValues?.get(1)?.trim()?.lowercase() }
    return if (word.isNullOrEmpty() || word in UNIT_WORDS) "serving" else word
}

private fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.let {
    it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
}

/** "COCA-COLA SERVICES" → "Coca-Cola Services"; short all-caps brands ("AH") stay. */
private fun tidyBrand(text: String): String =
    if (text.length > 3 && text == text.uppercase() && text.any { it.isLetter() }) {
        text.lowercase().replace(WORD_START) { it.value.uppercase() }
    } else {
        text
    }

private val WORD_START = Regex("(^|[\\s\\-/])\\p{L}")

/** "MAGERE KWARK" → "Magere kwark"; leaves mixed-case names alone. */
private fun tidyCase(text: String): String =
    if (text.length > 3 && text == text.uppercase() && text.any { it.isLetter() }) {
        text.lowercase().replaceFirstChar { it.titlecase() }
    } else {
        text
    }
