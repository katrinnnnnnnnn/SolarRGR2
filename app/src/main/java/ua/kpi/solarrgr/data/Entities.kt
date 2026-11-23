package ua.kpi.solarrgr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Погодинне значення сонячної радіації (Вт/м²), отримане з REST API й закешоване в Room. */
@Entity(tableName = "forecast")
data class ForecastEntity(
    @PrimaryKey val time: String,
    val radiation: Double,
    val updatedAt: Long
)

/** Сонячна електростанція (демонстраційні дані). */
@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val region: String,
    val type: String,          // "Наземна" | "Дахова" | "Плавуча"
    val capacityKw: Double,
    val online: Boolean,
    val lat: Double,
    val lon: Double
)
