package ua.kpi.solarrgr.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException

/** Єдина точка доступу до даних для ViewModel (шар Repository). */
interface SolarRepository {
    val forecast: Flow<List<ForecastEntity>>
    val stations: Flow<List<StationEntity>>
    suspend fun seedStationsIfEmpty()
    suspend fun setStationOnline(id: Long, online: Boolean)
    suspend fun refreshForecast(lat: Double, lon: Double): Result<Int>
    suspend fun clearForecastCache()
}

class DefaultSolarRepository(
    private val api: SolarApi,
    private val forecastDao: ForecastDao,
    private val stationDao: StationDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAttempts: Int = 3
) : SolarRepository {

    override val forecast: Flow<List<ForecastEntity>> = forecastDao.observeAll()
    override val stations: Flow<List<StationEntity>> = stationDao.observeAll()

    override suspend fun seedStationsIfEmpty() {
        if (stationDao.count() == 0) stationDao.insertAll(DemoStations.all)
    }

    override suspend fun setStationOnline(id: Long, online: Boolean) = stationDao.setOnline(id, online)

    override suspend fun clearForecastCache() = forecastDao.clear()

    /**
     * Завантажує прогноз із REST API. До [maxAttempts] спроб для IOException, HTTP 429 і 5xx.
     * Для інших 4xx повтор не виконується. Кеш оновлюється лише після успішної валідації.
     */
    override suspend fun refreshForecast(lat: Double, lon: Double): Result<Int> {
        var last: Throwable? = null
        for (attempt in 0 until maxAttempts) {
            try {
                val hourly = api.hourly(lat, lon).hourly ?: error("Порожня відповідь сервера")
                val now = clock()
                val records = hourly.time.zip(hourly.radiation).mapNotNull { (t, g) ->
                    if (g == null || g < 0) null else ForecastEntity(t, g, now)
                }
                if (records.isEmpty()) error("Сервер не повернув жодного значення")
                forecastDao.clear()
                forecastDao.putAll(records)
                return Result.success(records.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                last = e
            } catch (e: HttpException) {
                last = e
                if (e.code() < 500 && e.code() != 429) return Result.failure(e)
            } catch (e: Exception) {
                return Result.failure(e)
            }
            if (attempt < maxAttempts - 1) delay(500L * (attempt + 1))
        }
        return Result.failure(last ?: IllegalStateException("Невідома помилка"))
    }
}

object DemoStations {
    val all = listOf(
        StationEntity(1, "СЕС Київ-Північ", "Київська", "Наземна", 120.0, true, 50.45, 30.52),
        StationEntity(2, "СЕС Одеса-Порт", "Одеська", "Дахова", 95.0, false, 46.48, 30.72),
        StationEntity(3, "СЕС Харків-Схід", "Харківська", "Наземна", 145.0, true, 49.99, 36.23),
        StationEntity(4, "СЕС Черкаси", "Черкаська", "Плавуча", 75.0, true, 49.44, 32.06),
        StationEntity(5, "СЕС Вінниця", "Вінницька", "Наземна", 130.0, true, 49.23, 28.47),
        StationEntity(6, "СЕС Львів-Дах", "Львівська", "Дахова", 60.0, true, 49.84, 24.03),
        StationEntity(7, "СЕС Дніпро", "Дніпропетровська", "Наземна", 260.0, true, 48.46, 35.04),
        StationEntity(8, "СЕС Тернопіль", "Тернопільська", "Дахова", 40.0, false, 49.55, 25.59)
    )
}
