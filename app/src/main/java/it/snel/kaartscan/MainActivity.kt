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
    val user = vm.loggedInAs
    if (user == null) LoginScreen(vm) else MainScreen(vm, user)
}
