package ua.kpi.solarrgr.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.kpi.solarrgr.auth.Role
import ua.kpi.solarrgr.domain.SolarMath
import ua.kpi.solarrgr.domain.StationFilter
import ua.kpi.solarrgr.domain.StationSort
import ua.kpi.solarrgr.notify.Notifier
import ua.kpi.solarrgr.viewmodel.ForecastViewModel
import ua.kpi.solarrgr.viewmodel.LoginViewModel
import ua.kpi.solarrgr.viewmodel.MonitorViewModel
import ua.kpi.solarrgr.viewmodel.ReportViewModel
import ua.kpi.solarrgr.viewmodel.StationsViewModel

@Composable
fun ScreenColumn(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

// ---------- Вхід ----------
@Composable
fun LoginScreen(vm: LoginViewModel, onSuccess: () -> Unit) {
    val session by vm.session.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("admin@solar.test") }
    var pass by remember { mutableStateOf("") }
    LaunchedEffect(session) { if (session != null) onSuccess() }

    ScreenColumn("Вхід (навчальна автентифікація)") {
        Text("Демо: admin@solar.test / solar1234 — адміністратор; інший email з цим паролем — глядач.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Пароль") }, singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { vm.login(email, pass) }) { Text("Увійти") }
    }
}

// ---------- Завдання 1 ----------
@Composable
fun ForecastScreen(vm: ForecastViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    ScreenColumn("Прогноз генерації (Open-Meteo)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.refresh() }, enabled = !ui.loading) { Text("Оновити з API") }
            OutlinedButton(onClick = { vm.clearCache() }) { Text("Очистити кеш") }
        }
        Text(ui.message)
        Text("Записів у кеші: ${items.size}")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.time }) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text(it.time.replace('T', ' '))
                        Text("G = %.0f Вт/м² → P ≈ %.1f кВт (150 кВт, PR 0.8)".format(
                            it.radiation, SolarMath.powerKw(it.radiation, 150.0)))
                    }
                }
            }
        }
    }
}

// ---------- Завдання 3 ----------
@Composable
fun StationsScreen(vm: StationsViewModel, role: Role?) {
    val list by vm.stations.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    ScreenColumn("Станції: пошук і фільтри") {
        OutlinedTextField(query, { query = it; vm.setQuery(it) }, label = { Text("Назва або область") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(StationFilter.ALL, "Наземна", "Дахова", "Плавуча").forEach { t ->
                FilterChip(selected = filter.type == t, onClick = { vm.setType(t) }, label = { Text(t) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter.sort == StationSort.NAME, { vm.setSort(StationSort.NAME) }, { Text("Назва") })
            FilterChip(filter.sort == StationSort.POWER_DESC, { vm.setSort(StationSort.POWER_DESC) }, { Text("Потужн. ↓") })
            FilterChip(filter.sort == StationSort.POWER_ASC, { vm.setSort(StationSort.POWER_ASC) }, { Text("Потужн. ↑") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Лише активні")
            Switch(filter.onlineOnly, { vm.setOnlineOnly(it) })
        }
        Text("Знайдено: ${list.size}")
        if (note.isNotEmpty()) Text(note, color = MaterialTheme.colorScheme.error)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text("${s.type} · ${s.region} · ${s.capacityKw.toInt()} кВт")
                        Text(if (s.online) "Стан: активна" else "Стан: неактивна")
                        if (role == Role.ADMIN) {
                            OutlinedButton(onClick = { note = if (vm.toggleOnline(s)) "" else "Доступ заборонено" }) {
                                Text(if (s.online) "Вимкнути" else "Увімкнути")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Завдання 4 ----------
@Composable
fun MonitorScreen(vm: MonitorViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(ctx))
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.alerts.collect { Notifier.send(ctx, it) }
    }
    val last = ui.history.lastOrNull()
    ScreenColumn("Моніторинг у реальному часі (симуляція)") {
        Text("Потужність: ${last?.let { "%.1f кВт".format(it.powerKw) } ?: "—"}")
        Text("Напруга: ${last?.let { "%.1f В".format(it.voltage) } ?: "—"}   Температура: ${last?.let { "%.1f °C".format(it.tempC) } ?: "—"}")
        Text("Поріг сповіщення: %.0f кВт".format(ui.thresholdKw))
        Slider(ui.thresholdKw.toFloat(), { vm.setThreshold(it.toDouble()) }, valueRange = 20f..200f)
        Text(ui.alert ?: "Показники в нормі", color = if (ui.alert != null) MaterialTheme.colorScheme.error else Color.Unspecified)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.start() }, enabled = !ui.running) { Text("Старт") }
            OutlinedButton(onClick = { vm.stop() }, enabled = ui.running) { Text("Стоп") }
        }
        val color = MaterialTheme.colorScheme.primary
        val thr = ui.thresholdKw
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val h = ui.history
            if (h.size > 1) {
                val max = maxOf(200.0, h.maxOf { it.powerKw })
                val path = Path()
                h.forEachIndexed { i, r ->
                    val x = size.width * i / 59f
                    val y = (size.height * (1 - r.powerKw / max)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 4f))
                val ty = (size.height * (1 - thr / max)).toFloat()
                drawLine(Color.Red, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 2f)
            }
        }
        Text("Дані симульовані: фізичних датчиків не підключено.", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------- Завдання 5 ----------
@Composable
fun ReportsScreen(vm: ReportViewModel, role: Role?) {
    val status by vm.status.collectAsStateWithLifecycle()
    ScreenColumn("Експорт звітів") {
        if (role != Role.ADMIN) Text("Експорт доступний лише ролі ADMIN.", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.export("csv") }, enabled = role == Role.ADMIN) { Text("CSV") }
            Button(onClick = { vm.export("json") }, enabled = role == Role.ADMIN) { Text("JSON") }
            Button(onClick = { vm.export("pdf") }, enabled = role == Role.ADMIN) { Text("PDF") }
        }
        Text(status)
    }
}
