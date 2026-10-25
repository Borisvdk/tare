package app.tare.domain

import kotlin.math.pow

data class WeightPoint(val day: Long, val kg: Double)

data class TrendPoint(val day: Long, val kg: Double, val trendKg: Double)

/**
 * Exponentially smoothed weight trend (the "Hacker's Diet" moving average). Daily weight
 * swings by a kilo or more from water and food volume; the trend shows the real direction.
 * Gaps between weigh-ins are handled by compounding the smoothing factor per missed day.
 */
fun weightTrend(points: List<WeightPoint>, dailyAlpha: Double = 0.1): List<TrendPoint> {
    if (points.isEmpty()) return emptyList()
    val sorted = points.sortedBy { it.day }
    var trend = sorted.first().kg
    var lastDay = sorted.first().day
    return sorted.map { point ->
        val gap = (point.day - lastDay).coerceAtLeast(1).toDouble()
        val alpha = 1.0 - (1.0 - dailyAlpha).pow(gap)
        if (point.day != sorted.first().day) trend += alpha * (point.kg - trend)
        lastDay = point.day
        TrendPoint(point.day, point.kg, trend)
    }
}
