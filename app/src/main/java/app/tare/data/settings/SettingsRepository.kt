package app.tare.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tare.domain.MacroPlan
import app.tare.domain.Targets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Claude models offered in settings. The first entry is the default. */
enum class AiModel(val id: String, val label: String, val blurb: String) {
    OPUS("claude-opus-5-5", "Claude Opus 5.5", "Most accurate estimates"),
    SONNET("claude-sonnet-5-5", "Claude Sonnet 5.5", "Faster, about half the cost"),
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", "Fastest and cheapest"),
    ;

    companion object {
        fun fromId(id: String?): AiModel = entries.firstOrNull { it.id == id } ?: OPUS
    }
}

class SettingsRepository(private val store: DataStore<Preferences>) {

    val targets: Flow<Targets> = store.data.map { p ->
        val d = Targets()
        Targets(
            kcal = p[TARGET_KCAL] ?: d.kcal,
            protein = p[TARGET_PROTEIN] ?: d.protein,
            carbs = p[TARGET_CARBS] ?: d.carbs,
            fat = p[TARGET_FAT] ?: d.fat,
        )
    }

    /** How macros follow calories; null until targets are saved with a plan. */
    val macroPlan: Flow<MacroPlan?> = store.data.map { p ->
        if (p[PLAN_SAVED] != true) {
            null
        } else {
            MacroPlan(proteinPerKg = p[PLAN_PROTEIN_PER_KG]?.takeIf { it > 0 }, fatPercent = p[PLAN_FAT_PERCENT]?.takeIf { it > 0 })
        }
    }

    /** False until the user has confirmed targets once; drives the setup card on Today. */
    val targetsConfirmed: Flow<Boolean> = store.data.map { it[TARGETS_CONFIRMED] ?: false }

    val theme: Flow<ThemeMode> = store.data.map { p ->
        p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val aiModel: Flow<AiModel> = store.data.map { AiModel.fromId(it[AI_MODEL]) }

    /** Folder (SAF tree Uri) that receives the daily backup, or null when turned off. */
    val autoBackupFolder: Flow<String?> = store.data.map { it[AUTO_BACKUP_URI] }

    /** Epoch millis of the last weigh-in import from Health Connect. */
    val lastHealthSync: Flow<Long?> = store.data.map { it[LAST_HEALTH_SYNC] }

    suspend fun setLastHealthSync(at: Long) {
        store.edit { it[LAST_HEALTH_SYNC] = at }
    }

    /** True once a year of weigh-ins has been read (needs Health Connect's history access). */
    val healthHistoryImported: Flow<Boolean> = store.data.map { it[HEALTH_HISTORY_IMPORTED] ?: false }

    suspend fun setHealthHistoryImported() {
        store.edit { it[HEALTH_HISTORY_IMPORTED] = true }
    }

    /** Epoch millis of the last successful automatic backup. */
    val lastAutoBackup: Flow<Long?> = store.data.map { it[LAST_AUTO_BACKUP] }

    suspend fun setAutoBackupFolder(uri: String?) {
        store.edit { if (uri == null) it.remove(AUTO_BACKUP_URI) else it[AUTO_BACKUP_URI] = uri }
    }

    suspend fun setLastAutoBackup(at: Long) {
        store.edit { it[LAST_AUTO_BACKUP] = at }
    }

    suspend fun setTargets(targets: Targets, plan: MacroPlan? = null) {
        store.edit {
            it[TARGET_KCAL] = targets.kcal
            it[TARGET_PROTEIN] = targets.protein
            it[TARGET_CARBS] = targets.carbs
            it[TARGET_FAT] = targets.fat
            it[TARGETS_CONFIRMED] = true
            if (plan != null) {
                // 0 stands for "fixed grams".
                it[PLAN_SAVED] = true
                it[PLAN_PROTEIN_PER_KG] = plan.proteinPerKg ?: 0.0
                it[PLAN_FAT_PERCENT] = plan.fatPercent ?: 0
            }
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setAiModel(model: AiModel) {
        store.edit { it[AI_MODEL] = model.id }
    }

    suspend fun genericDatasetVersion(): Int = store.data.first()[GENERIC_VERSION] ?: 0

    suspend fun setGenericDatasetVersion(version: Int) {
        store.edit { it[GENERIC_VERSION] = version }
    }

    private companion object {
        val TARGET_KCAL = intPreferencesKey("target_kcal")
        val TARGET_PROTEIN = intPreferencesKey("target_protein")
        val TARGET_CARBS = intPreferencesKey("target_carbs")
        val TARGET_FAT = intPreferencesKey("target_fat")
        val TARGETS_CONFIRMED = booleanPreferencesKey("targets_confirmed")
        val THEME = stringPreferencesKey("theme")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val GENERIC_VERSION = intPreferencesKey("generic_dataset_version")
        val AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_uri")
        val LAST_AUTO_BACKUP = longPreferencesKey("last_auto_backup")
        val LAST_HEALTH_SYNC = longPreferencesKey("last_health_sync")
        val HEALTH_HISTORY_IMPORTED = booleanPreferencesKey("health_history_imported")
        val PLAN_SAVED = booleanPreferencesKey("macro_plan_saved")
        val PLAN_PROTEIN_PER_KG = doublePreferencesKey("macro_protein_per_kg")
        val PLAN_FAT_PERCENT = intPreferencesKey("macro_fat_percent")
    }
}
