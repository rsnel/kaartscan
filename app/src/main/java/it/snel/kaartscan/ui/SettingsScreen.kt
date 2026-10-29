package it.snel.kaartscan.ui

import android.content.pm.PackageInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.snel.kaartscan.KaartscanClient
import it.snel.kaartscan.MainViewModel
import it.snel.kaartscan.ScanSensitivity
import it.snel.kaartscan.UpdateState

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val focus = LocalFocusManager.current
    var draft by rememberSaveable { mutableStateOf(vm.serverUrl) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // "1.9 (build 42)": the git tag, and the CI run that built it
    val version = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val build = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else versionCodeLegacy(info).toLong()
            "${info.versionName} (build $build)"
        } catch (e: Exception) {
            null
        }
    }

    SettingsContent(
        sensitivity = vm.scanSensitivity,
        onSensitivityChange = { vm.updateScanSensitivity(it) },
        loggedIn = vm.me != null,
        server = draft,
        currentServer = vm.serverUrl,
        onServerChange = {
            draft = it
            serverError = null
            saved = false
        },
        onSaveServer = {
            focus.clearFocus()
            serverError = vm.updateServer(draft)
            if (serverError == null) {
                draft = vm.serverUrl
                saved = true
            }
        },
        onDefaultServer = {
            draft = KaartscanClient.DEFAULT_BASE_URL
            serverError = null
            saved = false
        },
        serverError = serverError,
        serverSaved = saved,
        version = version,
        autoUpdateCheck = vm.autoUpdateCheck,
        onAutoUpdateCheckChange = { vm.updateAutoUpdateCheck(it) },
        onCheckUpdates = { vm.checkForUpdates() },
        updateStatus = when (val u = vm.update) {
            is UpdateState.Checking -> "Bezig met controleren…"
            is UpdateState.UpToDate -> "Je hebt de nieuwste versie (${u.current})."
            is UpdateState.Failed -> if (u.release == null) "Controleren mislukt: ${u.message}" else null
            is UpdateState.Available -> "Versie ${u.release.version} is beschikbaar."
            is UpdateState.Downloading -> "Versie ${u.release.version} wordt gedownload…"
            is UpdateState.Ready -> "Versie ${u.release.version} is klaar om te installeren."
            UpdateState.Idle -> null
        },
        checkingUpdates = vm.update is UpdateState.Checking,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    sensitivity: ScanSensitivity,
    onSensitivityChange: (ScanSensitivity) -> Unit,
    loggedIn: Boolean,
    server: String,
    currentServer: String,
    onServerChange: (String) -> Unit,
    onSaveServer: () -> Unit,
    onDefaultServer: () -> Unit,
    serverError: String?,
    serverSaved: Boolean,
    version: String?,
    autoUpdateCheck: Boolean,
    onAutoUpdateCheckChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    updateStatus: String?,
    checkingUpdates: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
                title = { Text("Instellingen") },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Gevoeligheid barcodescanner", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (s in ScanSensitivity.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = s == sensitivity,
                                onClick = { onSensitivityChange(s) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = s == sensitivity, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                s.label + if (s == ScanSensitivity.DEFAULT) " (aanbevolen)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                s.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Server", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = if (loggedIn) currentServer else server,
                onValueChange = onServerChange,
                enabled = !loggedIn,
                label = { Text("Adres van Kaartscan") },
                singleLine = true,
                isError = serverError != null,
                supportingText = {
                    Text(
                        when {
                            loggedIn -> "Log uit om de server te wijzigen."
                            serverError != null -> serverError
                            serverSaved -> "Opgeslagen."
                            else -> "Standaard: ${KaartscanClient.DEFAULT_BASE_URL}"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSaveServer() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!loggedIn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveServer, enabled = server.trim() != currentServer) {
                        Text("Opslaan")
                    }
                    OutlinedButton(onClick = onDefaultServer, enabled = server != KaartscanClient.DEFAULT_BASE_URL) {
                        Text("Standaard")
                    }
                }
            }

            HorizontalDivider()

            Text("Updates", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = autoUpdateCheck,
                        onValueChange = onAutoUpdateCheckChange,
                        role = Role.Switch,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Bij opstarten op updates controleren", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Bij een nieuwe versie vraagt de app of je die wilt installeren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = autoUpdateCheck, onCheckedChange = null)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCheckUpdates, enabled = !checkingUpdates) {
                    Text("Nu controleren op updates")
                }
            }
            updateStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            Text(
                "Kaartscan" + (version?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun versionCodeLegacy(info: PackageInfo): Int = info.versionCode
