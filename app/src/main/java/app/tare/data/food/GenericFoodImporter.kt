package app.tare.data.food

import android.content.res.AssetManager
import androidx.room.withTransaction
import app.tare.data.db.Food
import app.tare.data.db.TareDatabase
import app.tare.data.settings.SettingsRepository
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.buildSearchText
import app.tare.domain.mergeCustomPortions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Loads the bundled USDA dataset (assets/generic_foods.tsv, built by tools/generic-foods)
 * into the food table. Runs once per app version; usage stats on foods the user already
 * ate are preserved across re-imports.
 */
class GenericFoodImporter(
    private val assets: AssetManager,
    private val db: TareDatabase,
    private val settings: SettingsRepository,
    private val datasetVersion: Int,
    private val now: () -> Long,
) {
    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        val dao = db.foodDao()
        if (settings.genericDatasetVersion() == datasetVersion && dao.genericCount() > 0) return@withContext
        val stream = try {
            assets.open(ASSET)
        } catch (_: Exception) {
            return@withContext
        }
        val parsed = stream.use { parseGenericFoods(it, now()) }
        // Read usage inside the transaction so a log made during the import isn't overwritten.
        db.withTransaction {
            val used = dao.usedGeneric().associateBy { it.id }
            val foods = parsed.map { food ->
                used[food.id]?.let {
                    food.copy(
                        portions = mergeCustomPortions(food.portions, it.portions),
                        useCount = it.useCount,
                        lastUsedAt = it.lastUsedAt,
                        lastGrams = it.lastGrams,
                        lastPortionLabel = it.lastPortionLabel,
                        favorite = it.favorite,
                        hidden = it.hidden,
                        createdAt = it.createdAt,
                    )
                } ?: food
            }
            foods.chunked(500).forEach { dao.upsertAll(it) }
        }
        settings.setGenericDatasetVersion(datasetVersion)
    }

    companion object {
        const val ASSET = "generic_foods.tsv"
    }
}

private val LEADING_AMOUNT = Regex("^(\\d+(?:\\.\\d+)?)\\s+(.+)$")

/** Parses the TSV described in tools/generic-foods/README.md. Malformed rows are skipped. */
internal fun parseGenericFoods(input: InputStream, now: Long): List<Food> {
    val lines = input.bufferedReader().readLines()
    if (lines.isEmpty()) return emptyList()
    val header = lines.first().split('\t').withIndex().associate { (i, name) -> name.trim() to i }
    fun List<String>.col(name: String): String = header[name]?.let { getOrNull(it) }?.trim().orEmpty()

    return lines.drop(1).mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val cells = line.split('\t')
        val id = cells.col("id").ifEmpty { return@mapNotNull null }
        val name = cells.col("name").ifEmpty { return@mapNotNull null }
        val kcal = cells.col("kcal").toDoubleOrNull() ?: return@mapNotNull null
        val aliases = cells.col("aliases").ifEmpty { null }
        Food(
            id = id,
            source = FoodSource.GENERIC,
            name = name,
            aliases = aliases,
            category = cells.col("category").ifEmpty { null },
            searchText = buildSearchText(name, null, aliases),
            per100 = Nutrients(
                kcal = kcal,
                protein = cells.col("protein").toDoubleOrNull() ?: 0.0,
                carbs = cells.col("carbs").toDoubleOrNull() ?: 0.0,
                fat = cells.col("fat").toDoubleOrNull() ?: 0.0,
                fiber = cells.col("fiber").toDoubleOrNull() ?: 0.0,
            ),
            portions = parsePortions(cells.col("portions")),
            popularity = cells.col("popularity").toIntOrNull() ?: 0,
            createdAt = now,
            updatedAt = now,
        )
    }
}

/** "1 medium=118;0.5 cup, sliced=75" → [medium=118 g, cup, sliced=150 g] (per one unit). */
internal fun parsePortions(raw: String): List<Portion> =
    raw.split(';').mapNotNull { part ->
        val eq = part.lastIndexOf('=')
        if (eq <= 0) return@mapNotNull null
        val label = part.substring(0, eq).trim()
        val grams = part.substring(eq + 1).trim().toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        val match = LEADING_AMOUNT.find(label)
        if (match == null) {
            Portion(label, grams)
        } else {
            val amount = match.groupValues[1].toDouble().takeIf { it > 0 } ?: 1.0
            Portion(match.groupValues[2].trim(), grams / amount)
        }
    }.distinctBy { it.label }
