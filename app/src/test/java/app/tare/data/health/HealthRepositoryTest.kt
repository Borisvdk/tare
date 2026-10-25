package app.tare.data.health

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.tare.AppGraph
import app.tare.data.db.TareDatabase
import app.tare.data.db.WeightEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Stands in for Health Connect in tests. */
class FakeHealthSource(
    var granted: Set<String> = HealthPermissions.ALL,
    var samples: List<WeightSample> = emptyList(),
    var burned: Map<LocalDate, Double> = emptyMap(),
) : HealthSource {
    var reads = 0
    override fun status() = HealthStatus.AVAILABLE
    override suspend fun granted() = granted
    override suspend fun weights(from: Instant, to: Instant): List<WeightSample> {
        reads++
        return samples.filter { it.time >= from && it.time <= to }
    }
    override suspend fun caloriesBurned(from: LocalDate, to: LocalDate) = burned.filterKeys { it in from..to }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class HealthRepositoryTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 25)
    private val now = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    private val source = FakeHealthSource()
    private val graph = AppGraph(
        ApplicationProvider.getApplicationContext(),
        now = { now },
        databaseFactory = { TareDatabase.inMemory(it) },
        healthFactory = { source },
    )

    private fun at(day: LocalDate, time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private suspend fun weights() = graph.db.weightDao().all().associate { LocalDate.ofEpochDay(it.day) to (it.kg to it.source) }

    @Test
    fun importsTheFirstWeighInOfEachDay() = runTest {
        source.samples = listOf(
            WeightSample(at(today.minusDays(1), "07:10"), 82.43),
            WeightSample(at(today.minusDays(1), "21:30"), 83.2),
            WeightSample(at(today, "07:05"), 82.1),
        )
        // force: the settings store outlives a single test, and with it the last sync time.
        assertThat(graph.health.syncWeights(force = true)).isEqualTo(2)
        assertThat(weights()).containsExactly(
            today.minusDays(1), 82.43 to WeightEntry.HEALTH_CONNECT,
            today, 82.1 to WeightEntry.HEALTH_CONNECT,
        )
    }

    @Test
    fun aWeightTypedInTareWinsItsDay() = runTest {
        graph.weights.log(today, 81.0)
        source.samples = listOf(WeightSample(at(today, "07:05"), 82.1))
        assertThat(graph.health.syncWeights(force = true)).isEqualTo(0)
        assertThat(weights()[today]).isEqualTo(81.0 to WeightEntry.MANUAL)
    }

    @Test
    fun nothingWithoutPermissionAndNotTooOften() = runTest {
        source.granted = emptySet()
        source.samples = listOf(WeightSample(at(today, "07:05"), 82.1))
        assertThat(graph.health.syncWeights()).isEqualTo(0)
        assertThat(source.reads).isEqualTo(0)

        source.granted = HealthPermissions.ALL
        graph.health.syncWeights(force = true)
        graph.health.syncWeights()
        assertThat(source.reads).isEqualTo(1)
        assertThat(graph.health.syncWeights(force = true)).isEqualTo(0)
        assertThat(source.reads).isEqualTo(2)
        assertThat(graph.health.importedWeighIns.first()).isEqualTo(1)
    }

    @Test
    fun caloriesBurnedNeedTheirOwnPermission() = runTest {
        source.burned = mapOf(today.minusDays(1) to 2650.0)
        assertThat(graph.health.caloriesBurned(today.minusDays(7), today)).containsExactly(today.minusDays(1), 2650.0)
        source.granted = setOf(HealthPermissions.WEIGHT)
        assertThat(graph.health.caloriesBurned(today.minusDays(7), today)).isEmpty()
    }
}
