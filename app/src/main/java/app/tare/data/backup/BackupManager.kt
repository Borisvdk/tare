package app.tare.data.backup

import androidx.room.withTransaction
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.data.db.Meal
import app.tare.data.db.MealItem
import app.tare.data.db.RecipeIngredient
import app.tare.data.db.TareDatabase
import app.tare.data.db.WeightEntry
import app.tare.data.settings.SettingsRepository
import app.tare.domain.Targets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Everything the user created, as one JSON file. Bundled generic foods are only included
 * when they carry usage stats; the catalog itself ships with the app.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val targets: Targets,
    val foods: List<Food>,
    val entries: List<LogEntry>,
    val meals: List<Meal>,
    val mealItems: List<MealItem>,
    val recipeIngredients: List<RecipeIngredient>,
    val weights: List<WeightEntry>,
) {
    companion object {
        const val FORMAT = "tare-backup"
    }
}

data class BackupSummary(val entries: Int, val foods: Int, val meals: Int, val weights: Int)

class BackupManager(
    private val db: TareDatabase,
    private val settings: SettingsRepository,
    private val now: () -> Long,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun export(out: OutputStream): BackupSummary = withContext(Dispatchers.IO) {
        val file = BackupFile(
            exportedAt = now(),
            targets = settings.targets.first(),
            foods = db.foodDao().allForBackup(),
            entries = db.logDao().all(),
            meals = db.mealDao().allMeals(),
            mealItems = db.mealDao().allItems(),
            recipeIngredients = db.recipeDao().allIngredients(),
            weights = db.weightDao().all(),
        )
        out.bufferedWriter().use { it.write(json.encodeToString(BackupFile.serializer(), file)) }
        file.summary()
    }

    /** Replaces the diary, meals and weights with the backup's; foods are merged by id. */
    suspend fun import(input: InputStream): BackupSummary = withContext(Dispatchers.IO) {
        val text = input.bufferedReader().use { it.readText() }
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.format == BackupFile.FORMAT) { "Not a Tare backup file" }
        db.withTransaction {
            db.logDao().clear()
            db.mealDao().clear()
            db.recipeDao().clear()
            db.weightDao().clear()
            db.foodDao().upsertAll(file.foods)
            db.logDao().insertAll(file.entries)
            file.meals.forEach { db.mealDao().upsertMeal(it) }
            db.mealDao().insertItems(file.mealItems)
            db.recipeDao().insertIngredients(file.recipeIngredients)
            db.weightDao().insertAll(file.weights)
        }
        settings.setTargets(file.targets)
        file.summary()
    }

    private fun BackupFile.summary() = BackupSummary(entries.size, foods.size, meals.size, weights.size)
}
