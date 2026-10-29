package it.snel.kaartscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import it.snel.kaartscan.ui.KaartscanTheme
import it.snel.kaartscan.ui.LoginScreen
import it.snel.kaartscan.ui.MainScreen
import it.snel.kaartscan.ui.SettingsScreen
import it.snel.kaartscan.ui.StartupScreen
import it.snel.kaartscan.ui.UpdateDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaartscanTheme {
                KaartscanApp()
            }
        }
    }
}

@Composable
fun KaartscanApp(vm: MainViewModel = viewModel()) {
    val me = vm.me
    var settings by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf<Tab?>(null) }
    if (me == null) tab = null // after logout, start again on the start screen
    when {
        settings -> SettingsScreen(vm, onBack = { settings = false })
        me != null -> MainScreen(vm, me, tab, onTabChange = { tab = it }, onOpenSettings = { settings = true })
        vm.checkingSession -> StartupScreen(vm.baseUrl, onSkip = { vm.skipSessionCheck() })
        else -> LoginScreen(vm, onOpenSettings = { settings = true })
    }
    // a found update is offered over whatever screen is open
    UpdateDialog(vm)
}
