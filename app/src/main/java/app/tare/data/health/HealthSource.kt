package app.tare.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period

enum class HealthStatus { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

data class WeightSample(val time: Instant, val kg: Double)

/** Read-only permissions Tare asks Health Connect for. */
object HealthPermissions {
    val WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
    val CALORIES_BURNED = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

    /** Without it only the last 30 days are readable; older Renpho weigh-ins need it. */
    const val HISTORY = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    val ALL = setOf(WEIGHT, CALORIES_BURNED, HISTORY)
}

/** What Tare reads from Health Connect. An interface so tests don't need the real service. */
interface HealthSource {
    fun status(): HealthStatus
    suspend fun granted(): Set<String>
    suspend fun weights(from: Instant, to: Instant): List<WeightSample>

    /**
     * Calories burned per day from [from] to [to] inclusive (local days). Days no app wrote
     * calories for are left out rather than filled with Health Connect's own estimate.
     */
    suspend fun caloriesBurned(from: LocalDate, to: LocalDate): Map<LocalDate, Double>
}

class HealthConnectSource(private val context: Context) : HealthSource {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override fun status(): HealthStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_UPDATE
        else -> HealthStatus.UNAVAILABLE
    }

    override suspend fun granted(): Set<String> =
        if (status() == HealthStatus.AVAILABLE) client.permissionController.getGrantedPermissions() else emptySet()

    override suspend fun weights(from: Instant, to: Instant): List<WeightSample> {
        val samples = mutableListOf<WeightSample>()
        var page: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = page,
                ),
            )
            response.records.mapTo(samples) { WeightSample(it.time, it.weight.inKilograms) }
            page = response.pageToken
        } while (page != null)
        return samples
    }

    override suspend fun caloriesBurned(from: LocalDate, to: LocalDate): Map<LocalDate, Double> {
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        // No origin means Health Connect filled the day with its own resting estimate (or an
        // app isn't in its priority list yet): not a burn worth comparing against.
        return groups
            .filter { it.result.dataOrigins.isNotEmpty() }
            .mapNotNull { group -> group.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let { group.startTime.toLocalDate() to it.inKilocalories } }
            .toMap()
    }
}
