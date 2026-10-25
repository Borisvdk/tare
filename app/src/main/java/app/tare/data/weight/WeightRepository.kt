package app.tare.data.weight

import app.tare.data.db.WeightDao
import app.tare.data.db.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class WeightRepository(
    private val dao: WeightDao,
    private val now: () -> Long,
) {
    fun observeAll(): Flow<List<WeightEntry>> = dao.observeAll()

    /** One weigh-in per day; logging again replaces that day's value. */
    suspend fun log(day: LocalDate, kg: Double) = dao.upsert(WeightEntry(day.toEpochDay(), kg, now()))

    suspend fun delete(day: LocalDate) = dao.delete(day.toEpochDay())
}
