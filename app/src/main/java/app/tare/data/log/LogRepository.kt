package app.tare.data.log

import androidx.room.withTransaction
import app.tare.data.db.DayTotals
import app.tare.data.db.Food
import app.tare.data.db.LogEntry
import app.tare.data.db.MealWithItems
import app.tare.data.db.TareDatabase
import app.tare.domain.FoodSource
import app.tare.domain.Nutrients
import app.tare.domain.Portion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class LogRepository(
    private val db: TareDatabase,
    private val now: () -> Long,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val dao = db.logDao()
    private val foodDao = db.foodDao()
    private val mealDao = db.mealDao()

    fun observeDay(day: LocalDate): Flow<List<LogEntry>> = dao.observeDay(day.toEpochDay())

    fun observeTotals(from: LocalDate, to: LocalDate): Flow<Map<Long, Nutrients>> =
        dao.observeTotals(from.toEpochDay(), to.toEpochDay()).map { rows -> rows.associate { it.day to it.nutrients } }

    fun observeTotalsList(from: LocalDate, to: LocalDate): Flow<List<DayTotals>> =
        dao.observeTotals(from.toEpochDay(), to.toEpochDay())

    suspend fun get(id: Long): LogEntry? = dao.get(id)

    /** Highest entry id so far; anything logged later has a larger id. */
    suspend fun maxId(): Long = dao.maxId()

    suspend fun logFood(
        food: Food,
        grams: Double,
        portion: Portion?,
        day: LocalDate,
        time: LocalTime? = null,
        groupId: String? = null,
        groupName: String? = null,
    ): LogEntry {
        val entry = entryFor(food, grams, portion, day, time, groupId, groupName)
        val id = db.withTransaction {
            foodDao.markUsed(food.id, now(), grams, portion?.label)
            dao.insert(entry)
        }
        return entry.copy(id = id)
    }

    suspend fun logQuick(name: String, nutrients: Nutrients, day: LocalDate, time: LocalTime? = null): LogEntry {
        val entry = LogEntry(
            day = day.toEpochDay(),
            loggedAt = timestamp(day, time),
            foodId = null,
            name = name.ifBlank { "Quick add" },
            source = FoodSource.QUICK,
            grams = null,
            nutrients = nutrients,
        )
        return entry.copy(id = dao.insert(entry))
    }

    /** Logs every item of a saved meal as its own entry, grouped under the meal's name. */
    suspend fun logMeal(meal: MealWithItems, scale: Double, day: LocalDate, time: LocalTime? = null): List<LogEntry> {
        val groupId = UUID.randomUUID().toString()
        val entries = meal.sortedItems.mapNotNull { item ->
            val food = item.food ?: return@mapNotNull null
            val portion = food.portions.firstOrNull { it.label == item.item.portionLabel }
            entryFor(food, item.item.grams * scale, portion, day, time, groupId, meal.meal.name)
        }
        val ids = db.withTransaction {
            mealDao.markUsed(meal.meal.id, now())
            dao.insertAll(entries)
        }
        return entries.zip(ids) { e, id -> e.copy(id = id) }
    }

    /** Logs a batch (e.g. confirmed AI estimates) as one group. */
    suspend fun logBatch(
        items: List<Triple<Food, Double, Portion?>>,
        day: LocalDate,
        groupName: String?,
        time: LocalTime? = null,
    ): List<LogEntry> {
        val groupId = if (items.size > 1) UUID.randomUUID().toString() else null
        val entries = items.map { (food, grams, portion) ->
            entryFor(food, grams, portion, day, time, groupId, groupName.takeIf { groupId != null })
        }
        val ids = db.withTransaction {
            items.forEach { (food, grams, portion) -> foodDao.markUsed(food.id, now(), grams, portion?.label) }
            dao.insertAll(entries)
        }
        return entries.zip(ids) { e, id -> e.copy(id = id) }
    }

    suspend fun updateAmount(entry: LogEntry, grams: Double, portion: Portion?) {
        val food = entry.foodId?.let { foodDao.get(it) }
        val nutrients = when {
            food != null -> food.per100.forGrams(grams)
            entry.grams != null && entry.grams > 0 -> entry.nutrients * (grams / entry.grams)
            else -> entry.nutrients
        }
        dao.update(
            entry.copy(
                grams = grams,
                portionLabel = portion?.label,
                portionGrams = portion?.grams,
                nutrients = nutrients,
            ),
        )
        if (food != null) foodDao.markUsed(food.id, now(), grams, portion?.label)
    }

    suspend fun update(entry: LogEntry) = dao.update(entry)

    suspend fun setTime(entries: List<LogEntry>, time: LocalTime) = db.withTransaction {
        entries.forEach { dao.update(it.copy(loggedAt = timestamp(LocalDate.ofEpochDay(it.day), time))) }
    }

    /** Deletes and returns the removed rows so the caller can offer undo via [restore]. */
    suspend fun delete(entries: List<LogEntry>): List<LogEntry> {
        dao.delete(entries)
        return entries
    }

    suspend fun restore(entries: List<LogEntry>) {
        dao.insertAll(entries)
    }

    /** Copies entries to [day] at the same time of day. Returns the new rows (for undo). */
    suspend fun copyTo(entries: List<LogEntry>, day: LocalDate): List<LogEntry> {
        val groupIds = entries.mapNotNull { it.groupId }.distinct().associateWith { UUID.randomUUID().toString() }
        val copies = entries.map { e ->
            val time = Instant.ofEpochMilli(e.loggedAt).atZone(zone()).toLocalTime()
            e.copy(id = 0, day = day.toEpochDay(), loggedAt = timestamp(day, time), groupId = e.groupId?.let(groupIds::get))
        }
        val ids = dao.insertAll(copies)
        return copies.zip(ids) { e, id -> e.copy(id = id) }
    }

    private fun entryFor(
        food: Food,
        grams: Double,
        portion: Portion?,
        day: LocalDate,
        time: LocalTime?,
        groupId: String?,
        groupName: String?,
    ) = LogEntry(
        day = day.toEpochDay(),
        loggedAt = timestamp(day, time),
        foodId = food.id,
        name = food.name,
        brand = food.brand,
        source = food.source,
        grams = grams,
        isLiquid = food.isLiquid,
        portionLabel = portion?.label,
        portionGrams = portion?.grams,
        nutrients = food.per100.forGrams(grams),
        groupId = groupId,
        groupName = groupName,
    )

    /** The given day at [time], or at the current time of day when logging without one. */
    private fun timestamp(day: LocalDate, time: LocalTime?): Long {
        val zone = zone()
        val t = time ?: Instant.ofEpochMilli(now()).atZone(zone).toLocalTime()
        return LocalDateTime.of(day, t).atZone(zone).toInstant().toEpochMilli()
    }
}
