package ua.kpi.solarrgr

import android.app.Application
import androidx.room.Room
import ua.kpi.solarrgr.auth.AuthManager
import ua.kpi.solarrgr.data.ApiFactory
import ua.kpi.solarrgr.data.AppDatabase
import ua.kpi.solarrgr.data.DefaultSolarRepository
import ua.kpi.solarrgr.data.SolarRepository
import ua.kpi.solarrgr.domain.TelemetrySimulator
import ua.kpi.solarrgr.notify.Notifier
import java.io.File

/** Простий контейнер залежностей (ручний DI). */
class AppContainer(app: Application) {
    private val db = Room.databaseBuilder(app, AppDatabase::class.java, "solar.db").build()
    val repository: SolarRepository =
        DefaultSolarRepository(ApiFactory.create(), db.forecastDao(), db.stationDao())
    val auth = AuthManager()
    val telemetry = TelemetrySimulator()
    val filesDir: File = app.filesDir
}

class SolarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannel(this)
    }
}
