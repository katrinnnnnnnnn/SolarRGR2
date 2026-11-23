package ua.kpi.solarrgr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.kpi.solarrgr.auth.AuthManager
import ua.kpi.solarrgr.auth.Session
import ua.kpi.solarrgr.data.ForecastEntity
import ua.kpi.solarrgr.data.SolarRepository
import ua.kpi.solarrgr.data.StationEntity
import ua.kpi.solarrgr.domain.AlertRules
import ua.kpi.solarrgr.domain.Reading
import ua.kpi.solarrgr.domain.SolarMath
import ua.kpi.solarrgr.domain.StationFilter
import ua.kpi.solarrgr.domain.StationFiltering
import ua.kpi.solarrgr.domain.StationSort
import ua.kpi.solarrgr.domain.TelemetrySimulator
import ua.kpi.solarrgr.export.PdfReport
import ua.kpi.solarrgr.export.ReportData
import ua.kpi.solarrgr.export.ReportRow
import java.io.File

// ---------- Завдання 1: прогноз (REST + Room) ----------
data class ForecastUi(val loading: Boolean = false, val message: String = "")

class ForecastViewModel(private val repo: SolarRepository) : ViewModel() {
    val items: StateFlow<List<ForecastEntity>> =
        repo.forecast.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _ui = MutableStateFlow(ForecastUi())
    val ui: StateFlow<ForecastUi> = _ui.asStateFlow()

    fun refresh(lat: Double = 50.45, lon: Double = 30.52) {
        viewModelScope.launch {
            _ui.value = ForecastUi(loading = true, message = "Завантаження…")
            val r = repo.refreshForecast(lat, lon)
            _ui.value = ForecastUi(
                loading = false,
                message = r.fold(
                    { "Оновлено записів: $it" },
                    { "Помилка: ${it.message ?: it::class.simpleName}. Показано збережений кеш." }
                )
            )
        }
    }

    fun clearCache() = viewModelScope.launch { repo.clearForecastCache(); _ui.value = ForecastUi(message = "Кеш очищено") }
}

// ---------- Завдання 3: станції (Flow combine, пошук, фільтри) ----------
@OptIn(FlowPreview::class)
class StationsViewModel(private val repo: SolarRepository, private val auth: AuthManager) : ViewModel() {
    private val query = MutableStateFlow("")
    private val type = MutableStateFlow(StationFilter.ALL)
    private val onlineOnly = MutableStateFlow(false)
    private val sort = MutableStateFlow(StationSort.NAME)

    val filter: StateFlow<StationFilter> = combine(query, type, onlineOnly, sort) { q, t, o, s ->
        StationFilter(q, t, o, s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StationFilter())

    val stations: StateFlow<List<StationEntity>> = combine(
        repo.stations, query.debounce(250), type, onlineOnly, sort
    ) { all, q, t, o, s -> StationFiltering.apply(all, StationFilter(q, t, o, s)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { viewModelScope.launch { repo.seedStationsIfEmpty() } }

    fun setQuery(v: String) { query.value = v }
    fun setType(v: String) { type.value = v }
    fun setOnlineOnly(v: Boolean) { onlineOnly.value = v }
    fun setSort(v: StationSort) { sort.value = v }

    /** Керування станцією дозволене лише ролі ADMIN. Повертає false, якщо доступ заборонено. */
    fun toggleOnline(s: StationEntity): Boolean {
        if (!auth.isAdmin()) return false
        viewModelScope.launch { repo.setStationOnline(s.id, !s.online) }
        return true
    }
}

// ---------- Завдання 4: моніторинг у реальному часі ----------
data class MonitorUi(
    val running: Boolean = false,
    val history: List<Reading> = emptyList(),
    val thresholdKw: Double = 100.0,
    val alert: String? = null
)

class MonitorViewModel(private val sim: TelemetrySimulator) : ViewModel() {
    private val _ui = MutableStateFlow(MonitorUi())
    val ui: StateFlow<MonitorUi> = _ui.asStateFlow()
    private val _alerts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val alerts: SharedFlow<String> = _alerts
    private var job: Job? = null
    private var lastAlertAt = 0L

    fun setThreshold(v: Double) = _ui.update { it.copy(thresholdKw = v) }

    fun start(capacityKw: Double = 150.0) {
        if (job?.isActive == true) return
        _ui.update { it.copy(running = true) }
        job = viewModelScope.launch {
            sim.stream(capacityKw).collect { r ->
                val msg = AlertRules.evaluate(r, _ui.value.thresholdKw)
                _ui.update { it.copy(history = (it.history + r).takeLast(60), alert = msg) }
                if (msg != null && r.timestamp - lastAlertAt > 10_000) {
                    lastAlertAt = r.timestamp
                    _alerts.tryEmit(msg)
                }
            }
        }
    }

    fun stop() { job?.cancel(); _ui.update { it.copy(running = false) } }
    override fun onCleared() { job?.cancel() }
}

// ---------- Завдання 5: експорт CSV / JSON / PDF ----------
class ReportViewModel(
    private val repo: SolarRepository,
    private val auth: AuthManager,
    private val dir: File
) : ViewModel() {
    private val _status = MutableStateFlow("Оберіть формат експорту")
    val status: StateFlow<String> = _status.asStateFlow()

    private suspend fun rows(capacityKw: Double): List<ReportRow> {
        val data = repo.forecast.first()
        return data.map { ReportRow(it.time, it.radiation, SolarMath.powerKw(it.radiation, capacityKw)) }
    }

    fun export(format: String, capacityKw: Double = 150.0) {
        if (!auth.isAdmin()) { _status.value = "Експорт доступний лише адміністратору"; return }
        viewModelScope.launch {
            try {
                val list = rows(capacityKw)
                if (list.isEmpty()) { _status.value = "Немає даних: спершу завантажте прогноз"; return@launch }
                val file = withContext(Dispatchers.IO) {
                    when (format) {
                        "csv" -> File(dir, "solar_report.csv").also { it.writeText(ReportData.toCsv(list)) }
                        "json" -> File(dir, "solar_report.json").also { it.writeText(ReportData.toJson(list)) }
                        else -> File(dir, "solar_report.pdf").also { PdfReport.create(it, "Звіт сонячної станції", list) }
                    }
                }
                _status.value = "Збережено: ${file.absolutePath} (${list.size} записів)"
            } catch (e: Exception) {
                _status.value = "Помилка експорту: ${e.message}"
            }
        }
    }
}

// ---------- Вхід ----------
class LoginViewModel(private val auth: AuthManager) : ViewModel() {
    val session: StateFlow<Session?> = auth.session
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun login(email: String, password: String) {
        _error.value = auth.login(email, password).exceptionOrNull()?.message
    }
    fun logout() = auth.logout()
}
