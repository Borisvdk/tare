package app.tare.data.health

import app.tare.data.db.WeightDao
import app.tare.data.db.WeightEntry
import app.tare.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Weigh-ins (from a scale app such as Renpho) and calories burned (from Garmin Connect, say),
 * read through Health Connect. Weights are imported into Tare's own table so the trend works
 * offline; calories burned are read when needed. Tare never writes to Health Connect.
 */
class HealthRepository(
    private val source: HealthSource,
    private val weights: WeightDao,
    private val settings: SettingsRepository,
    private val now: () -> Long,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val syncing = Mutex()

    fun status(): HealthStatus = source.status()

    suspend fun granted(): Set<String> = runCatching { source.granted() }.getOrDefault(emptySet())

    val importedWeighIns: Flow<Int> = weights.observeCount(WeightEntry.HEALTH_CONNECT)

    val lastSync: Flow<Long?> = settings.lastHealthSync

    /**
     * Imports weigh-ins since the last sync (a year back the first time, if history access was
     * given). The first weigh-in of a day counts, like a morning weigh-in; a weight typed in
     * Tare for that day is left alone. Returns how many days were added or updated.
     */
    suspend fun syncWeights(force: Boolean = false): Int = syncing.withLock {
        withContext(Dispatchers.IO) {
            val granted = granted()
            if (HealthPermissions.WEIGHT !in granted) return@withContext 0
            val nowAt = Instant.ofEpochMilli(now())
            val last = settings.lastHealthSync.first()
            // A year back once history access is there (it may arrive after the first import),
            // then just the days since the last import.
            val fullHistory = HealthPermissions.HISTORY in granted && !settings.healthHistoryImported.first()
            // Every resume would be wasteful; a few minutes apart is plenty.
            if (!force && !fullHistory && last != null && nowAt.toEpochMilli() - last < Duration.ofMinutes(10).toMillis()) return@withContext 0
            val from = when {
                fullHistory -> nowAt.minus(Duration.ofDays(365))
                last != null -> Instant.ofEpochMilli(last).minus(Duration.ofDays(2))
                else -> nowAt.minus(Duration.ofDays(30))
            }
            val samples = runCatching { source.weights(from, nowAt) }.getOrElse { return@withContext 0 }
            val firstOfDay = samples
                .filter { it.kg in 20.0..400.0 }
                .groupBy { it.time.atZone(zone()).toLocalDate() }
                .mapValues { (_, list) -> list.minBy { it.time } }
            if (firstOfDay.isEmpty()) {
                settings.setLastHealthSync(nowAt.toEpochMilli())
                return@withContext 0
            }
            val days = firstOfDay.keys.map(LocalDate::toEpochDay)
            val existing = weights.between(days.min(), days.max()).associateBy { it.day }
            val updates = firstOfDay.mapNotNull { (day, sample) ->
                val current = existing[day.toEpochDay()]
                val kg = (sample.kg * 100).roundToLong() / 100.0
                when {
                    current?.source == WeightEntry.MANUAL -> null
                    current != null && current.kg == kg -> null
                    else -> WeightEntry(day.toEpochDay(), kg, sample.time.toEpochMilli(), WeightEntry.HEALTH_CONNECT)
                }
            }
            weights.upsertAll(updates)
            settings.setLastHealthSync(nowAt.toEpochMilli())
            if (fullHistory) settings.setHealthHistoryImported()
            updates.size
        }
    }

    /** Calories burned per day, or nothing when that isn't shared with Tare. */
    suspend fun caloriesBurned(from: LocalDate, to: LocalDate): Map<LocalDate, Double> = withContext(Dispatchers.IO) {
        if (HealthPermissions.CALORIES_BURNED !in granted()) return@withContext emptyMap()
        runCatching { source.caloriesBurned(from, to) }.getOrDefault(emptyMap())
    }
}
