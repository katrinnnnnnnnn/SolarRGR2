package ua.kpi.solarrgr.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ForecastDao {
    @Query("SELECT * FROM forecast ORDER BY time")
    fun observeAll(): Flow<List<ForecastEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(items: List<ForecastEntity>)

    @Query("DELETE FROM forecast")
    suspend fun clear()
}

@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY name")
    fun observeAll(): Flow<List<StationEntity>>

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<StationEntity>)

    @Query("UPDATE stations SET online = :online WHERE id = :id")
    suspend fun setOnline(id: Long, online: Boolean)
}

@Database(entities = [ForecastEntity::class, StationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun forecastDao(): ForecastDao
    abstract fun stationDao(): StationDao
}
