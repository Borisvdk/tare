package app.tare.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import app.tare.data.settings.AiModel
import app.tare.domain.Nutrients
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.Duration
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class EstimatedItem(
    val name: String,
    val grams: Double,
    val isLiquid: Boolean = false,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double = 0.0,
    /** "high" | "medium" | "low" */
    val confidence: String = "medium",
    /** Ref of one of the user's own foods ("f3") when the item is that food, else empty. */
    val myFood: String = "",
)

/** One of the user's frequent foods, offered to Claude under a short [ref]. */
data class KnownFood(val ref: String, val name: String, val brand: String?, val per100: Nutrients, val unit: String)

@Serializable
data class MealEstimate(
    /** Short name for the whole meal, used as the log group name. */
    val title: String,
    val items: List<EstimatedItem>,
    /** One short sentence about assumptions worth checking, or empty. */
    val note: String = "",
)

@Serializable
data class LabelReading(
    val name: String,
    val brand: String = "",
    val isLiquid: Boolean = false,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double = 0.0,
    /** Grams (or ml) per serving if the label states one, else 0. */
    val servingGrams: Double = 0.0,
)

sealed interface AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>
    data object NoApiKey : AiResult<Nothing>
    data class Failed(val message: String) : AiResult<Nothing>
}

/**
 * Turns free text ("2 eggs and toast with peanut butter") or a photo of a plate/label into
 * structured nutrition estimates with Claude. Output is constrained to a JSON schema, so the
 * response is parsed, not scraped.
 */
class FoodAi(
    private val apiKey: suspend () -> String?,
    private val model: suspend () -> AiModel,
    /** Override for tests (mock server); null uses the real API. */
    private val baseUrl: String? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Volatile private var cached: Pair<String, AnthropicClient>? = null

    suspend fun estimateMeal(
        description: String,
        photo: ByteArray?,
        myFoods: List<KnownFood> = emptyList(),
    ): AiResult<MealEstimate> {
        val text = buildString {
            if (myFoods.isNotEmpty()) {
                append("My usual foods (ref | name | brand | per 100 g or ml):\n")
                myFoods.forEach { f ->
                    val n = f.per100
                    append("${f.ref} | ${f.name} | ${f.brand.orEmpty()} | ")
                    append("${n.kcal.roundToInt()} kcal, P ${n.protein.roundToInt()}, C ${n.carbs.roundToInt()}, F ${n.fat.roundToInt()} per 100 ${f.unit}\n")
                }
                append("\n")
            }
            if (description.isNotBlank()) append("What I ate: ").append(description.trim())
            if (photo != null) {
                if (description.isNotBlank()) append("\n\n")
                append("The photo shows what I ate. Estimate every item visible on the plate.")
            }
        }
        return call(MEAL_SYSTEM, text, photo, MEAL_SCHEMA) { json.decodeFromString(MealEstimate.serializer(), it) }
    }

    suspend fun readLabel(photo: ByteArray): AiResult<LabelReading> =
        call(LABEL_SYSTEM, "Read this nutrition label.", photo, LABEL_SCHEMA) {
            json.decodeFromString(LabelReading.serializer(), it)
        }

    private suspend fun <T> call(
        system: String,
        userText: String,
        photo: ByteArray?,
        schema: Map<String, Any>,
        parse: (String) -> T,
    ): AiResult<T> = withContext(Dispatchers.IO) {
        val key = apiKey() ?: return@withContext AiResult.NoApiKey
        val model = model()
        val client = clientFor(key)

        val blocks = buildList {
            photo?.let { add(ContentBlockParam.ofImage(imageBlock(it))) }
            add(ContentBlockParam.ofText(TextBlockParam.builder().text(userText).build()))
        }
        val params = MessageCreateParams.builder()
            .model(model.id)
            .maxTokens(16000L)
            .system(system)
            .addUserMessageOfContentBlockParams(blocks)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.MEDIUM)
                    .format(JsonOutputFormat.builder().schema(schemaOf(schema)).build())
                    .build(),
            )
            .apply {
                // Opus/Sonnet: if a safety classifier declines, let the API retry on a
                // suitable fallback model instead of failing the request.
                if (model != AiModel.HAIKU) {
                    putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()

        try {
            val response = client.messages().create(params)
            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                return@withContext AiResult.Failed("Claude declined this request. Try describing it differently.")
            }
            val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            if (text.isBlank()) return@withContext AiResult.Failed("Empty response from Claude")
            AiResult.Success(parse(text))
        } catch (e: UnauthorizedException) {
            AiResult.Failed("Your Claude API key was rejected. Check it in Settings.")
        } catch (e: RateLimitException) {
            AiResult.Failed("Claude is rate limiting requests. Try again in a minute.")
        } catch (e: AnthropicServiceException) {
            AiResult.Failed("Claude error (${e.statusCode()}): ${e.message}")
        } catch (e: kotlinx.serialization.SerializationException) {
            AiResult.Failed("Couldn't read Claude's answer. Try again.")
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Couldn't reach Claude. Check your connection.")
        }
    }

    private fun clientFor(key: String): AnthropicClient {
        cached?.takeIf { it.first == key }?.let { return it.second }
        val client = AnthropicOkHttpClient.builder()
            .apiKey(key)
            .timeout(Duration.ofSeconds(120))
            .maxRetries(2)
            .apply { baseUrl?.let { baseUrl(it) } }
            .build()
        cached = key to client
        return client
    }

    private fun imageBlock(bytes: ByteArray): ImageBlockParam {
        val jpeg = runCatching { downscaleJpeg(bytes) }.getOrDefault(bytes)
        return ImageBlockParam.builder()
            .source(
                Base64ImageSource.builder()
                    .data(Base64.encodeToString(jpeg, Base64.NO_WRAP))
                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                    .build(),
            )
            .build()
    }

    /** Claude doesn't benefit from images beyond ~1568 px on the long edge; smaller is faster. */
    private fun downscaleJpeg(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return bytes
        val scale = MAX_EDGE.toFloat() / max(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    private fun schemaOf(schema: Map<String, Any>): JsonOutputFormat.Schema {
        val builder = JsonOutputFormat.Schema.builder()
        schema.forEach { (k, v) -> builder.putAdditionalProperty(k, JsonValue.from(v)) }
        return builder.build()
    }

    private companion object {
        const val MAX_EDGE = 1568

        val MEAL_SYSTEM = """
            You estimate nutrition for a personal food diary. The user lives in the Netherlands
            and uses metric units.

            Split what they ate into the separate foods they would weigh or count, e.g.
            "toast with peanut butter" becomes bread and peanut butter as two items. For each
            item give the amount eaten in grams (millilitres for drinks, with isLiquid true) and
            nutrition per 100 g of that food as eaten (cooked values for cooked food).

            Use reliable reference values (NEVO, USDA). When a brand or product is named, use
            that product's typical label values. When the user gives an amount, use it exactly;
            otherwise assume a typical Dutch adult portion and set confidence to "medium" or
            "low". Count cooking oil, butter and sauces only when they are mentioned or clearly
            visible.

            Write item names short and specific, in the language the user wrote in
            ("Volkoren brood", "Peanut butter"). The title is a 1–4 word name for the whole
            meal. Use the note for one short sentence about an assumption worth checking, or
            leave it empty.

            The user may list their usual foods with a short ref. When an item clearly is one
            of them (same food, and the same brand if one is given), set myFood to its ref and
            copy its per-100 g values exactly; still estimate the grams eaten. Otherwise leave
            myFood empty.
        """.trimIndent()

        val LABEL_SYSTEM = """
            You read nutrition labels from photos for a food diary. Report the values per 100 g
            (or per 100 ml for drinks, with isLiquid true) exactly as printed. If the label only
            gives per-serving values, convert them to per 100 g using the stated serving size.
            Use 0 for values that are not printed. If the product name or brand is visible,
            include it; otherwise give a short descriptive name.
        """.trimIndent()

        private fun number() = mapOf("type" to "number")

        val MEAL_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("title", "items", "note"),
            "properties" to mapOf(
                "title" to mapOf("type" to "string"),
                "note" to mapOf("type" to "string"),
                "items" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to listOf(
                            "name", "grams", "isLiquid", "kcalPer100g", "proteinPer100g",
                            "carbsPer100g", "fatPer100g", "fiberPer100g", "confidence", "myFood",
                        ),
                        "properties" to mapOf(
                            "name" to mapOf("type" to "string"),
                            "grams" to number(),
                            "isLiquid" to mapOf("type" to "boolean"),
                            "kcalPer100g" to number(),
                            "proteinPer100g" to number(),
                            "carbsPer100g" to number(),
                            "fatPer100g" to number(),
                            "fiberPer100g" to number(),
                            "confidence" to mapOf("type" to "string", "enum" to listOf("high", "medium", "low")),
                            "myFood" to mapOf("type" to "string"),
                        ),
                    ),
                ),
            ),
        )

        val LABEL_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf(
                "name", "brand", "isLiquid", "kcalPer100g", "proteinPer100g", "carbsPer100g",
                "fatPer100g", "fiberPer100g", "servingGrams",
            ),
            "properties" to mapOf(
                "name" to mapOf("type" to "string"),
                "brand" to mapOf("type" to "string"),
                "isLiquid" to mapOf("type" to "boolean"),
                "kcalPer100g" to number(),
                "proteinPer100g" to number(),
                "carbsPer100g" to number(),
                "fatPer100g" to number(),
                "fiberPer100g" to number(),
                "servingGrams" to number(),
            ),
        )
    }
}
