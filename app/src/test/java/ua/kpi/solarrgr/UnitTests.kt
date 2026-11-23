package ua.kpi.solarrgr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ua.kpi.solarrgr.auth.AuthManager
import ua.kpi.solarrgr.auth.Role
import ua.kpi.solarrgr.data.DefaultSolarRepository
import ua.kpi.solarrgr.data.DemoStations
import ua.kpi.solarrgr.data.ForecastDao
import ua.kpi.solarrgr.data.ForecastEntity
import ua.kpi.solarrgr.data.HourlySolar
import ua.kpi.solarrgr.data.SolarApi
import ua.kpi.solarrgr.data.SolarRepository
import ua.kpi.solarrgr.data.SolarResponse
import ua.kpi.solarrgr.data.StationDao
import ua.kpi.solarrgr.data.StationEntity
import ua.kpi.solarrgr.domain.AlertRules
import ua.kpi.solarrgr.domain.Reading
import ua.kpi.solarrgr.domain.SolarMath
import ua.kpi.solarrgr.domain.StationFilter
import ua.kpi.solarrgr.domain.StationFiltering
import ua.kpi.solarrgr.domain.StationSort
import ua.kpi.solarrgr.export.ReportData
import ua.kpi.solarrgr.export.ReportRow
import ua.kpi.solarrgr.viewmodel.StationsViewModel
import java.io.IOException

class SolarMathTest {
    @Test fun `power at 1000 W per m2`() =
        assertEquals(96.0, SolarMath.powerKw(1000.0, 120.0), 0.001)
    @Test fun `negative radiation gives zero`() =
        assertEquals(0.0, SolarMath.powerKw(-50.0, 120.0), 0.0)
    @Test(expected = IllegalArgumentException::class) fun `negative capacity rejected`() {
        SolarMath.powerKw(500.0, -1.0)
    }
    @Test fun `energy is sum`() = assertEquals(6.0, SolarMath.energyKwh(listOf(1.0, 2.0, 3.0)), 0.0)
}

class AlertRulesTest {
    private fun r(p: Double, t: Double) = Reading(0, p, 230.0, t)
    @Test fun `normal state`() = assertNull(AlertRules.evaluate(r(50.0, 30.0), 100.0))
    @Test fun `power alert`() = assertNotNull(AlertRules.evaluate(r(120.0, 30.0), 100.0))
    @Test fun `temperature alert`() = assertNotNull(AlertRules.evaluate(r(50.0, 70.0), 100.0))
}

class FilteringTest {
    private val all = DemoStations.all
    @Test fun `filter by type and online`() {
        val res = StationFiltering.apply(all, StationFilter(type = "Дахова", onlineOnly = true))
        assertTrue(res.all { it.type == "Дахова" && it.online })
        assertEquals(1, res.size)
    }
    @Test fun `sort by power desc`() {
        val res = StationFiltering.apply(all, StationFilter(sort = StationSort.POWER_DESC))
        assertEquals(res.map { it.capacityKw }.sortedDescending(), res.map { it.capacityKw })
    }
    @Test fun `search by region is case insensitive`() =
        assertEquals(1, StationFiltering.apply(all, StationFilter(query = "львівська")).size)
}

class ReportDataTest {
    private val rows = listOf(ReportRow("2026-06-01T12:00", 800.0, 96.5), ReportRow("2026-06-01T13:00", 0.0, 0.0))
    @Test fun `csv round trip`() = assertEquals(rows, ReportData.parseCsv(ReportData.toCsv(rows)))
    @Test fun `json round trip`() = assertEquals(rows, ReportData.parseJson(ReportData.toJson(rows)))
    @Test(expected = IllegalArgumentException::class) fun `negative power rejected`() {
        ReportData.toCsv(listOf(ReportRow("t", 1.0, -1.0)))
    }
    @Test(expected = IllegalArgumentException::class) fun `broken csv rejected`() {
        ReportData.parseCsv("hour,radiation,powerKw\n10:00,abc,1")
    }
}

class AuthManagerTest {
    private var now = 0L
    private val auth = AuthManager { now }
    @Test fun `admin login`() {
        assertEquals(Role.ADMIN, auth.login("admin@solar.test", "solar1234").getOrThrow().role)
        assertTrue(auth.isAdmin())
    }
    @Test fun `viewer login`() {
        assertEquals(Role.VIEWER, auth.login("user@solar.test", "solar1234").getOrThrow().role)
        assertFalse(auth.isAdmin())
    }
    @Test fun `wrong password`() = assertTrue(auth.login("admin@solar.test", "bad").isFailure)
    @Test fun `bad email`() = assertTrue(auth.login("nope", "solar1234").isFailure)
    @Test fun `token expires`() {
        auth.login("admin@solar.test", "solar1234")
        now += AuthManager.TTL_MS + 1
        assertNull(auth.current())
    }
}

// ---- Fakes ----
class FakeForecastDao : ForecastDao {
    val data = MutableStateFlow<List<ForecastEntity>>(emptyList())
    override fun observeAll(): Flow<List<ForecastEntity>> = data
    override suspend fun putAll(items: List<ForecastEntity>) { data.value = items }
    override suspend fun clear() { data.value = emptyList() }
}

class FakeStationDao(initial: List<StationEntity> = emptyList()) : StationDao {
    val data = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<StationEntity>> = data
    override suspend fun count() = data.value.size
    override suspend fun insertAll(items: List<StationEntity>) { data.value = items }
    override suspend fun setOnline(id: Long, online: Boolean) {
        data.value = data.value.map { if (it.id == id) it.copy(online = online) else it }
    }
}

class RepositoryTest {
    private class ScriptedApi(private val steps: MutableList<() -> SolarResponse>) : SolarApi {
        var calls = 0
        override suspend fun hourly(lat: Double, lon: Double, field: String, days: Int, tz: String): SolarResponse {
            calls++
            return steps.removeAt(0)()
        }
    }
    private val ok = { SolarResponse(HourlySolar(listOf("t1", "t2"), listOf(100.0, null))) }
    private fun repo(api: SolarApi, dao: FakeForecastDao) =
        DefaultSolarRepository(api, dao, FakeStationDao(), clock = { 1L })

    @Test fun `success stores only valid values`() = runTest {
        val dao = FakeForecastDao()
        val r = repo(ScriptedApi(mutableListOf(ok)), dao).refreshForecast(50.0, 30.0)
        assertEquals(1, r.getOrThrow())
        assertEquals(1, dao.data.value.size)
    }
    @Test fun `retries on IOException then succeeds`() = runTest {
        val api = ScriptedApi(mutableListOf({ throw IOException("net") }, { throw IOException("net") }, ok))
        val r = repo(api, FakeForecastDao()).refreshForecast(50.0, 30.0)
        assertTrue(r.isSuccess)
        assertEquals(3, api.calls)
    }
    @Test fun `no retry on 404`() = runTest {
        val err = { throw HttpException(Response.error<Any>(404, "".toResponseBody())) }
        val api = ScriptedApi(mutableListOf(err, ok))
        val r = repo(api, FakeForecastDao()).refreshForecast(50.0, 30.0)
        assertTrue(r.isFailure)
        assertEquals(1, api.calls)
    }
    @Test fun `cache survives failure`() = runTest {
        val dao = FakeForecastDao().apply { data.value = listOf(ForecastEntity("old", 1.0, 0)) }
        val api = ScriptedApi(mutableListOf({ throw IOException() }, { throw IOException() }, { throw IOException() }))
        assertTrue(repo(api, dao).refreshForecast(1.0, 1.0).isFailure)
        assertEquals(1, dao.data.value.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StationsViewModelTest {
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(val dao: FakeStationDao) : SolarRepository {
        override val forecast: Flow<List<ForecastEntity>> = flowOf(emptyList())
        override val stations: Flow<List<StationEntity>> = dao.observeAll()
        override suspend fun seedStationsIfEmpty() { if (dao.count() == 0) dao.insertAll(DemoStations.all) }
        override suspend fun setStationOnline(id: Long, online: Boolean) = dao.setOnline(id, online)
        override suspend fun refreshForecast(lat: Double, lon: Double) = Result.success(0)
        override suspend fun clearForecastCache() {}
    }

    @Test fun `query filters list after debounce`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = StationsViewModel(FakeRepo(FakeStationDao()), AuthManager())
        val job = launch { vm.stations.collect { } }
        advanceTimeBy(400); runCurrent()
        assertEquals(8, vm.stations.value.size)
        vm.setQuery("Київ")
        advanceTimeBy(400); runCurrent()
        assertEquals(1, vm.stations.value.size)
        job.cancel()
    }

    @Test fun `viewer cannot toggle station`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = AuthManager().also { it.login("user@solar.test", "solar1234") }
        val vm = StationsViewModel(FakeRepo(FakeStationDao()), auth)
        assertFalse(vm.toggleOnline(DemoStations.all.first()))
    }
}
