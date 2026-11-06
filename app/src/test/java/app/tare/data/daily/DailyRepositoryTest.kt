package app.tare.data.daily

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.tare.data.db.Food
import app.tare.data.db.TareDatabase
import app.tare.data.log.LogRepository
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import app.tare.domain.buildSearchText
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DailyRepositoryTest {
    private lateinit var db: TareDatabase
    private lateinit var log: LogRepository
    private lateinit var daily: DailyRepository

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val monday = LocalDate.of(2026, 11, 2)
    private var clock = at(monday, 9, 0)

    private val cappuccino = food("custom:cappuccino", "Cappuccino", Nutrients(40.0, 2.2, 3.4, 1.9), listOf(Portion("cup", 200.0)))
    private val quark = food("off:quark", "Magere kwark", Nutrients(57.0, 9.2, 4.1, 0.1))

    private fun at(day: LocalDate, hour: Int, minute: Int) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun food(id: String, name: String, per100: Nutrients, portions: List<Portion> = emptyList()) = Food(
        id = id,
        source = FoodSource.CUSTOM,
        name = name,
        searchText = buildSearchText(name, null, null),
        per100 = per100,
        portions = portions,
        createdAt = 0,
        updatedAt = 0,
    )

    @Before
    fun setUp() = runTest {
        db = TareDatabase.inMemory(ApplicationProvider.getApplicationContext())
        log = LogRepository(db, { clock }, { zone })
        daily = DailyRepository(db, log, { clock }, { zone })
        db.foodDao().upsertAll(listOf(cappuccino, quark))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entriesOn(day: LocalDate) = log.observeDay(day).first()

    @Test
    fun startsTomorrowAndLogsOncePerDay() = runTest {
        val entry = log.logFood(cappuccino, 200.0, cappuccino.portions.first(), monday, LocalTime.of(8, 15))
        daily.set(entry, daily = true)

        // Today already has it: nothing new.
        assertThat(daily.logDue()).isEmpty()

        clock = at(monday.plusDays(1), 7, 0)
        val logged = daily.logDue()
        assertThat(logged).hasSize(1)
        val tuesday = entriesOn(monday.plusDays(1)).single()
        assertThat(tuesday.name).isEqualTo("Cappuccino")
        assertThat(tuesday.daily).isTrue()
        assertThat(tuesday.grams).isEqualTo(200.0)
        assertThat(tuesday.portionLabel).isEqualTo("cup")
        assertThat(tuesday.nutrients.kcal).isWithin(0.01).of(80.0)
        // At the habit's time, so it lands in the morning even when logged at 07:00.
        assertThat(Instant.ofEpochMilli(tuesday.loggedAt).atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(8, 15))

        // Resuming again the same day adds nothing.
        clock = at(monday.plusDays(1), 13, 0)
        assertThat(daily.logDue()).isEmpty()
        assertThat(entriesOn(monday.plusDays(1))).hasSize(1)
    }

    @Test
    fun deletedEntryStaysDeletedThatDay() = runTest {
        daily.save(quark, 250.0, null, LocalTime.of(8, 0))
        clock = at(monday.plusDays(1), 8, 30)
        val logged = daily.logDue()
        log.delete(logged)

        clock = at(monday.plusDays(1), 20, 0)
        assertThat(daily.logDue()).isEmpty()
        assertThat(entriesOn(monday.plusDays(1))).isEmpty()

        clock = at(monday.plusDays(2), 8, 30)
        assertThat(daily.logDue()).hasSize(1)
    }

    @Test
    fun skippedDaysAreNotBackfilled() = runTest {
        daily.save(quark, 250.0, null, LocalTime.of(8, 0))
        clock = at(monday.plusDays(4), 10, 0)
        daily.logDue()
        assertThat(entriesOn(monday.plusDays(1))).isEmpty()
        assertThat(entriesOn(monday.plusDays(4))).hasSize(1)
    }

    @Test
    fun alreadyLoggedByHandIsNotLoggedTwice() = runTest {
        daily.save(quark, 250.0, null, LocalTime.of(8, 0))
        val tuesday = monday.plusDays(1)
        clock = at(tuesday, 0, 10)
        // Logged just after midnight, before Tare resumed and filled the day in.
        log.logFood(quark, 150.0, null, tuesday, LocalTime.of(0, 10))
        assertThat(daily.logDue()).isEmpty()
        assertThat(entriesOn(tuesday).single().grams).isEqualTo(150.0)
    }

    @Test
    fun concurrentResumesLogOnce() = runTest {
        daily.save(quark, 250.0, null, LocalTime.of(8, 0))
        clock = at(monday.plusDays(1), 8, 0)
        (1..4).map { async { daily.logDue() } }.awaitAll()
        assertThat(entriesOn(monday.plusDays(1))).hasSize(1)
    }

    @Test
    fun changingTheAmountDoesNotLogAgainToday() = runTest {
        daily.save(quark, 250.0, null, LocalTime.of(8, 0))
        clock = at(monday.plusDays(1), 8, 0)
        daily.logDue()
        daily.save(quark, 300.0, null, LocalTime.of(8, 0))
        assertThat(daily.logDue()).isEmpty()
        clock = at(monday.plusDays(2), 8, 0)
        assertThat(daily.logDue().single().grams).isEqualTo(300.0)
    }

    @Test
    fun switchingOffStopsItAndUndoBringsItBack() = runTest {
        val entry = log.logFood(quark, 250.0, null, monday, LocalTime.of(8, 0))
        assertThat(daily.set(entry, daily = true)).isNotNull()
        assertThat(daily.set(entry, daily = true)).isNull()
        val off = daily.set(entry, daily = false)!!
        assertThat(daily.observeIds().first()).isEmpty()
        // Today's entry is still there.
        assertThat(entriesOn(monday)).hasSize(1)

        daily.undo(off)
        assertThat(daily.observeIds().first()).containsExactly(quark.id)
    }

    @Test
    fun hiddenFoodIsSkippedAndLeavesTheList() = runTest {
        daily.save(quark, 250.0, null, LocalTime.of(8, 0))
        db.foodDao().hide(quark.id, clock)
        assertThat(daily.observe().first()).isEmpty()
        clock = at(monday.plusDays(1), 8, 0)
        assertThat(daily.logDue()).isEmpty()
    }

    @Test
    fun forkedFoodTakesOver() = runTest {
        daily.save(quark, 250.0, null, LocalTime.of(8, 0))
        val fork = quark.copy(id = "custom:my-quark", name = "My quark")
        db.foodDao().upsert(fork)
        daily.replaceFood(quark.id, fork.id)
        clock = at(monday.plusDays(1), 8, 0)
        assertThat(daily.logDue().single().name).isEqualTo("My quark")
    }

    @Test
    fun copyingYesterdayLeavesOutWhatTodayAlreadyGotByItself() = runTest {
        daily.save(quark, 250.0, null, LocalTime.of(8, 0))
        val tuesday = monday.plusDays(1)
        val wednesday = monday.plusDays(2)
        clock = at(tuesday, 8, 0)
        daily.logDue()
        log.logFood(cappuccino, 200.0, null, tuesday, LocalTime.of(8, 5))
        clock = at(wednesday, 8, 0)
        daily.logDue()

        val copies = log.copyTo(entriesOn(tuesday), wednesday)
        assertThat(copies.map { it.name }).containsExactly("Cappuccino")
        assertThat(entriesOn(wednesday).map { it.name }).containsExactly("Magere kwark", "Cappuccino")
        // A copy is the user's own entry, not one Tare logged.
        assertThat(copies.single().daily).isFalse()
    }
}
