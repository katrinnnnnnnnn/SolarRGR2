package ua.kpi.solarrgr.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class HourlySolar(
    val time: List<String> = emptyList(),
    @SerializedName("shortwave_radiation") val radiation: List<Double?> = emptyList()
)

data class SolarResponse(val hourly: HourlySolar? = null)

/** Віддалене джерело даних: публічний сервіс Open-Meteo (без API-ключа). */
interface SolarApi {
    @GET("v1/forecast")
    suspend fun hourly(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") field: String = "shortwave_radiation",
        @Query("forecast_days") days: Int = 2,
        @Query("timezone") tz: String = "auto"
    ): SolarResponse
}

object ApiFactory {
    fun create(): SolarApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SolarApi::class.java)
    }
}
