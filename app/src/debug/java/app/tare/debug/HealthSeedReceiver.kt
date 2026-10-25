package app.tare.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sin

/**
 * Debug builds only: fills Health Connect with 30 days of morning weigh-ins and daily
 * calories burned, standing in for Renpho and Garmin on the emulator.
 *
 *   adb shell pm grant app.tare.debug android.permission.health.WRITE_WEIGHT
 *   adb shell pm grant app.tare.debug android.permission.health.WRITE_TOTAL_CALORIES_BURNED
 *   adb shell am broadcast -n app.tare.debug/app.tare.debug.HealthSeedReceiver
 */
class HealthSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val records = (1L..30L).flatMap { back ->
                    val day = today.minusDays(back)
                    val weighIn = day.atTime(7, 15).atZone(zone)
                    val start = day.atStartOfDay(zone)
                    val end = day.plusDays(1).atStartOfDay(zone).minusSeconds(1)
                    listOf(
                        WeightRecord(
                            time = weighIn.toInstant(),
                            zoneOffset = weighIn.offset,
                            weight = Mass.kilograms(82.0 + back * 0.05 + 0.4 * sin(back * 1.7)),
                            metadata = Metadata.manualEntry(),
                        ),
                        TotalCaloriesBurnedRecord(
                            startTime = start.toInstant(),
                            startZoneOffset = start.offset,
                            endTime = end.toInstant(),
                            endZoneOffset = end.offset,
                            energy = Energy.kilocalories(2450 + 220 * sin(back * 1.3)),
                            metadata = Metadata.autoRecorded(Device(type = Device.TYPE_WATCH)),
                        ),
                    )
                }
                HealthConnectClient.getOrCreate(context).insertRecords(records)
                Log.i("TareSeed", "Inserted ${records.size} records")
            } catch (e: Exception) {
                Log.e("TareSeed", "Seeding Health Connect failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
