package ua.kpi.solarrgr.domain

import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Орієнтовна потужність станції: P = (G / 1000) · P_nom · PR, де G — радіація Вт/м². */
object SolarMath {
    const val DEFAULT_PR = 0.8

    fun powerKw(radiationWm2: Double, capacityKw: Double, pr: Double = DEFAULT_PR): Double {
        require(capacityKw >= 0) { "Встановлена потужність не може бути від'ємною" }
        return (radiationWm2.coerceAtLeast(0.0) / 1000.0) * capacityKw * pr
    }

    /** Енергія за погодинні значення потужності (кВт · 1 год = кВт·год). */
    fun energyKwh(hourlyPowerKw: List<Double>): Double = hourlyPowerKw.sum()
}

data class Reading(val timestamp: Long, val powerKw: Double, val voltage: Double, val tempC: Double)

/** Імітація телеметрії станції (навчальні дані, не реальні вимірювання). */
class TelemetrySimulator(private val random: Random = Random.Default) {
    fun stream(capacityKw: Double, periodMs: Long = 1000): Flow<Reading> = flow {
        var i = 0
        while (true) {
            val sun = (sin(i / 15.0) + 1) / 2
            val noise = random.nextDouble(-0.04, 0.04) * capacityKw
            val power = (capacityKw * 0.9 * sun + noise).coerceIn(0.0, capacityKw * 1.1)
            val voltage = 230 + random.nextDouble(-6.0, 6.0)
            val temp = 25 + 40 * sun + random.nextDouble(-1.5, 1.5)
            emit(Reading(System.currentTimeMillis(), power, voltage, temp))
            i++
            delay(periodMs)
        }
    }
}

object AlertRules {
    /** Повертає текст попередження або null, якщо показники в нормі. */
    fun evaluate(r: Reading, thresholdKw: Double, maxTempC: Double = 60.0): String? = when {
        r.powerKw > thresholdKw -> "Перевищено поріг потужності: %.1f кВт > %.1f кВт".format(r.powerKw, thresholdKw)
        r.tempC > maxTempC -> "Висока температура панелей: %.1f °C".format(r.tempC)
        else -> null
    }
}

data class StationFilter(
    val query: String = "",
    val type: String = ALL,
    val onlineOnly: Boolean = false,
    val sort: StationSort = StationSort.NAME
) {
    companion object { const val ALL = "Всі" }
}

enum class StationSort { NAME, POWER_DESC, POWER_ASC }

object StationFiltering {
    fun apply(items: List<ua.kpi.solarrgr.data.StationEntity>, f: StationFilter) =
        items.filter { s ->
            (f.query.isBlank() || s.name.contains(f.query, true) || s.region.contains(f.query, true)) &&
                (f.type == StationFilter.ALL || s.type == f.type) &&
                (!f.onlineOnly || s.online)
        }.let { list ->
            when (f.sort) {
                StationSort.NAME -> list.sortedBy { it.name }
                StationSort.POWER_DESC -> list.sortedByDescending { it.capacityKw }
                StationSort.POWER_ASC -> list.sortedBy { it.capacityKw }
            }
        }
}
