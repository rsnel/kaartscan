package it.snel.kaartscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import it.snel.kaartscan.MainViewModel

@Composable
fun LoginScreen(vm: MainViewModel) {
    var username by rememberSaveable { mutableStateOf(vm.lastUsername) }
    var password by remember { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(vm.baseUrl) }
    var editServer by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val canLogin = !vm.loginBusy && username.isNotBlank() && password.isNotEmpty()
    val doLogin = {
        if (canLogin) {
            focus.clearFocus()
            vm.login(username, password, server)
        }
    }

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
            Text("Kaartscan OVC", style = MaterialTheme.typography.headlineMedium)
            Text("Inloggen met lokaal wachtwoord", style = MaterialTheme.typography.titleMedium)
            Text(
                "Docenten loggen in met het eerste deel van hun schoolemail. " +
                    "Het wachtwoord is het schoolwachtwoord. Alleen beheerders van Kaartscan hebben toegang.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Gebruikersnaam") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Wachtwoord") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { doLogin() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (editServer) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { editServer = true }) {
                    Text("Server: $server", style = MaterialTheme.typography.bodySmall)
                }
            }
            vm.loginError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { doLogin() }, enabled = canLogin, modifier = Modifier.fillMaxWidth()) {
                if (vm.loginBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Inloggen")
            }
            vm.resumableUser?.let { user ->
                OutlinedButton(onClick = { vm.resume() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Doorgaan als $user")
                }
            }
            if (vm.checkingSession) {
                Text("Opgeslagen sessie controleren…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
