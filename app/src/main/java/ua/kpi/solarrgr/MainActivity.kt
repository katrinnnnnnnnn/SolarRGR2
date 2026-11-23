package ua.kpi.solarrgr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ua.kpi.solarrgr.ui.ForecastScreen
import ua.kpi.solarrgr.ui.LoginScreen
import ua.kpi.solarrgr.ui.MonitorScreen
import ua.kpi.solarrgr.ui.ReportsScreen
import ua.kpi.solarrgr.ui.StationsScreen
import ua.kpi.solarrgr.viewmodel.ForecastViewModel
import ua.kpi.solarrgr.viewmodel.LoginViewModel
import ua.kpi.solarrgr.viewmodel.MonitorViewModel
import ua.kpi.solarrgr.viewmodel.ReportViewModel
import ua.kpi.solarrgr.viewmodel.StationsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SolarApp).container
        setContent { MaterialTheme { AppRoot(container) } }
    }
}

private data class Dest(val route: String, val label: String, val icon: String)

private val DESTS = listOf(
    Dest("forecast", "Прогноз", "☀"),
    Dest("stations", "Станції", "⚡"),
    Dest("monitor", "Моніторинг", "📈"),
    Dest("reports", "Звіти", "📄")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(c: AppContainer) {
    val factory = viewModelFactory {
        initializer { LoginViewModel(c.auth) }
        initializer { ForecastViewModel(c.repository) }
        initializer { StationsViewModel(c.repository, c.auth) }
        initializer { MonitorViewModel(c.telemetry) }
        initializer { ReportViewModel(c.repository, c.auth, c.filesDir) }
    }
    val nav: NavHostController = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val session by c.auth.session.collectAsStateWithLifecycle()
    val loginVm: LoginViewModel = viewModel(factory = factory)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SolarGrid РГР") },
                actions = {
                    if (session != null) {
                        Text(session!!.role.name)
                        TextButton(onClick = {
                            loginVm.logout()
                            nav.navigate("login") { popUpTo(0) }
                        }) { Text("Вийти") }
                    }
                }
            )
        },
        bottomBar = {
            if (route != null && route != "login") {
                NavigationBar {
                    DESTS.forEach { d ->
                        NavigationBarItem(
                            selected = route == d.route,
                            onClick = { nav.navigate(d.route) { launchSingleTop = true; popUpTo("forecast") } },
                            icon = { Text(d.icon) },
                            label = { Text(d.label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "login", modifier = Modifier.padding(pad)) {
            composable("login") {
                LoginScreen(loginVm) { nav.navigate("forecast") { popUpTo("login") { inclusive = true } } }
            }
            composable("forecast") { ForecastScreen(viewModel(factory = factory)) }
            composable("stations") { StationsScreen(viewModel(factory = factory), session?.role) }
            composable("monitor") { MonitorScreen(viewModel(factory = factory)) }
            composable("reports") { ReportsScreen(viewModel(factory = factory), session?.role) }
        }
    }
}
