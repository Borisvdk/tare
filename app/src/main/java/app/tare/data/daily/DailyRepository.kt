package app.tare.data.daily

import androidx.room.withTransaction
import app.tare.data.db.DailyFood
import app.tare.data.db.DailyFoodWithFood
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.data.db.TareDatabase
import app.tare.data.log.LogRepository
import app.tare.domain.Portion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What [DailyRepository.set] changed: [before] is the row to put back, null if there was none. */
data class DailyChange(val foodId: String, val before: DailyFood?, val daily: Boolean)

/**
 * Foods the user has every day (a morning cappuccino). Each one is logged once per day, when
 * Tare first opens that day, and is then an ordinary entry: delete it on a day you skip it.
 *
 * Only today is filled in. A day the app never opened stays empty rather than half-logged, and
 * a new every-day food starts tomorrow, because today you've just logged it (or chose not to).
 */
class DailyRepository(
    private val db: TareDatabase,
    private val log: LogRepository,
    private val now: () -> Long,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val dao = db.dailyDao()
    private val logDao = db.logDao()
    private val foodDao = db.foodDao()

    /** Resume can fire from several places at once; one pass at a time keeps it to one entry each. */
    private val logging = Mutex()

    /** Every-day foods whose food still exists, in the order they're eaten. */
    fun observe(): Flow<List<DailyFoodWithFood>> =
        dao.observeAll().map { list -> list.filter { it.food != null && !it.food.hidden } }

    fun observeIds(): Flow<Set<String>> = dao.observeIds().map { it.toSet() }

    /** Makes the food behind [entry] an every-day food at the entry's amount and time. */
    suspend fun add(entry: LogEntry) {
        val foodId = entry.foodId ?: return
        val time = Instant.ofEpochMilli(entry.loggedAt).atZone(zone()).toLocalTime()
        save(foodId, entry.grams ?: return, entry.portionLabel, time)
    }

    /** Adds or replaces [food] as an every-day food. It's first logged tomorrow. */
    suspend fun save(food: Food, grams: Double, portion: Portion?, time: LocalTime) =
        save(food.id, grams, portion?.label, time)

    private suspend fun save(foodId: String, grams: Double, portionLabel: String?, time: LocalTime) {
        val existing = dao.get(foodId)
        dao.upsert(
            DailyFood(
                foodId = foodId,
                grams = grams,
                portionLabel = portionLabel,
                minuteOfDay = time.hour * 60 + time.minute,
                // Changing the amount of one that's already running mustn't log it again today.
                lastLoggedDay = maxOf(existing?.lastLoggedDay ?: Long.MIN_VALUE, today().toEpochDay()),
                createdAt = existing?.createdAt ?: now(),
            ),
        )
    }

    /**
     * Turns the food behind [entry] into an every-day food (at the entry's amount and time) or
     * back. Returns what changed, for [undo]; null when nothing did.
     */
    suspend fun set(entry: LogEntry, daily: Boolean): DailyChange? {
        val foodId = entry.foodId ?: return null
        val before = dao.get(foodId)
        if ((before != null) == daily) return null
        if (daily) add(entry) else dao.delete(foodId)
        return DailyChange(foodId, before, daily)
    }

    suspend fun undo(change: DailyChange) {
        if (change.before != null) dao.upsert(change.before) else dao.delete(change.foodId)
    }

    /** Removes it and hands it back for undo; today's entry, if any, stays. */
    suspend fun remove(foodId: String): DailyFood? = dao.get(foodId)?.also { dao.delete(foodId) }

    suspend fun restore(daily: DailyFood) = dao.upsert(daily)

    /** A scanned product edited into the user's own version: the copy takes over. */
    suspend fun replaceFood(oldId: String, newId: String) = db.withTransaction {
        val daily = dao.get(oldId) ?: return@withTransaction
        dao.delete(oldId)
        dao.upsert(daily.copy(foodId = newId))
    }

    /**
     * Logs every every-day food that hasn't been logged today yet and returns the new entries.
     * A food that's already on today by hand isn't logged twice.
     */
    suspend fun logDue(): List<LogEntry> = logging.withLock {
        val today = today()
        val day = today.toEpochDay()
        db.withTransaction {
            val due = dao.dueOn(day)
            if (due.isEmpty()) return@withTransaction emptyList()
            val already = logDao.foodIdsOn(day).toSet()
            val entries = due.mapNotNull { daily ->
                if (daily.foodId in already) return@mapNotNull null
                val food = foodDao.get(daily.foodId)?.takeIf { !it.hidden } ?: return@mapNotNull null
                val portion = food.portions.firstOrNull { it.label == daily.portionLabel }
                val time = LocalTime.of(daily.minuteOfDay / 60, daily.minuteOfDay % 60)
                log.dailyEntry(food, daily.grams, portion, today, time)
            }
            // Marked even when skipped, so a food deleted today (or hidden) doesn't come back later.
            dao.markLogged(due.map { it.foodId }, day)
            val ids = logDao.insertAll(entries)
            entries.zip(ids) { e, id -> e.copy(id = id) }
        }
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate()
}
