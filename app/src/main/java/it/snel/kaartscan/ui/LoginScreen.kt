package it.snel.kaartscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.dp
import it.snel.kaartscan.MainViewModel

@Composable
fun LoginScreen(vm: MainViewModel) {
    var username by rememberSaveable { mutableStateOf(vm.lastUsername) }
    var password by remember { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(vm.baseUrl) }
    var editServer by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    // no session: check every 30 s whether the server is alive, for the dot
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.pingServer()
                delay(MainViewModel.HEARTBEAT_MS)
            }
        }
    }

    val canLogin = !vm.loginBusy && username.isNotBlank() && password.isNotEmpty()
    val doLogin = {
        if (canLogin) {
            focus.clearFocus()
            vm.login(username, password, server)
        }
    }

    LoginContent(
        username = username,
        onUsernameChange = { username = it },
        password = password,
        onPasswordChange = { password = it },
        server = server,
        onServerChange = { server = it },
        editServer = editServer,
        onEditServer = { editServer = true },
        error = vm.loginError,
        busy = vm.loginBusy,
        canLogin = canLogin,
        onLogin = { doLogin() },
        statusDot = {
            val server by vm.server.collectAsState()
            ServerStatusDot(
                snapshot = server,
                loggedIn = false,
                server = vm.baseUrl,
                user = null,
                feest = null,
                onCheckNow = { vm.pingServer() },
            )
        },
    )
}

@Composable
internal fun LoginContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    server: String,
    onServerChange: (String) -> Unit,
    editServer: Boolean,
    onEditServer: () -> Unit,
    error: String?,
    busy: Boolean,
    canLogin: Boolean,
    onLogin: () -> Unit,
    statusDot: @Composable () -> Unit = {},
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Scaffold { pad ->
        Column(
            Modifier
                .padding(pad)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kaartscan OVC", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                statusDot()
            }
            Text("Inloggen met lokaal wachtwoord", style = MaterialTheme.typography.titleMedium)
            Text(
                "Docenten loggen in met het eerste deel van hun schoolemail. " +
                    "Het wachtwoord is een zelfgekozen wachtwoord dat je hebt insgesteld of kan installen op https://srv-ovc-linux.ovc.nl/checkset/ .",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Gebruikersnaam") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Wachtwoord") },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Wachtwoord verbergen" else "Wachtwoord tonen",
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onLogin() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (editServer) {
                OutlinedTextField(
                    value = server,
                    onValueChange = onServerChange,
                    label = { Text("Server") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = onEditServer) {
                    Text("Server: $server", style = MaterialTheme.typography.bodySmall)
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onLogin, enabled = canLogin, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Inloggen")
            }
        }
    }
}

/** Shown at startup while the stored session is checked. */
@Composable
fun StartupScreen(server: String, onSkip: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Kaartscan OVC", style = MaterialTheme.typography.headlineMedium)
            CircularProgressIndicator()
            Text("Verbinden met $server…", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onSkip) { Text("Naar inloggen") }
        }
    }
}
