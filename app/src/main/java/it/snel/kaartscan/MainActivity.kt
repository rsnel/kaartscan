package it.snel.kaartscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import it.snel.kaartscan.ui.KaartscanTheme
import it.snel.kaartscan.ui.LoginScreen
import it.snel.kaartscan.ui.MainScreen
import it.snel.kaartscan.ui.StartupScreen

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
    when {
        me != null -> MainScreen(vm, me)
        vm.checkingSession -> StartupScreen(vm.baseUrl, onSkip = { vm.skipSessionCheck() })
        else -> LoginScreen(vm)
    }
}
