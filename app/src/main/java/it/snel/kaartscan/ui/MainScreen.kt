package it.snel.kaartscan.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import it.snel.kaartscan.MainViewModel
import it.snel.kaartscan.Tab
import kotlinx.coroutines.delay

private fun Tab.label(): String = when (this) {
    Tab.VERKOOP -> "Verkoop"
    Tab.OVERZICHT -> "Overzicht"
    Tab.CONTROLE -> "Controle"
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.VERKOOP -> Icons.Filled.ConfirmationNumber
    Tab.OVERZICHT -> Icons.AutoMirrored.Filled.List
    Tab.CONTROLE -> Icons.Filled.HowToReg
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, user: String) {
    var tab by rememberSaveable { mutableStateOf(Tab.VERKOOP) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kaartscan OVC")
                        Text(user, style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load(tab) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Vernieuwen")
                    }
                    IconButton(onClick = { vm.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Uitloggen")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(t.icon(), contentDescription = null) },
                        label = { Text(t.label()) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.VERKOOP -> ScanScreen(vm, Tab.VERKOOP)
                Tab.OVERZICHT -> OverzichtScreen(vm)
                Tab.CONTROLE -> ScanScreen(vm, Tab.CONTROLE)
            }
        }
    }
}

/** kaartverkoop.php and kaartcontrole.php: scan or type a barcode, see the result and the latest entries. */
@Composable
fun ScanScreen(vm: MainViewModel, tab: Tab) {
    val state = vm.state(tab)
    val context = LocalContext.current
    val feedback = rememberFeedback()

    LaunchedEffect(tab) { vm.load(tab) }

    var cameraOn by rememberSaveable { mutableStateOf(true) }
    var manual by rememberSaveable(tab) { mutableStateOf("") }
    var cooling by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(cameraOn) {
        if (cameraOn && !hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // sound/vibrate once per new result, then pause scanning briefly so the
    // result can be read before the next card is scanned
    LaunchedEffect(state.resultSeq) {
        val msg = state.result
        if (state.feedbackPending && msg != null) {
            vm.feedbackDone(tab)
            feedback(msg.isError)
            cooling = true
            delay(1500)
            cooling = false
        }
    }

    val submitManual = {
        if (manual.isNotBlank()) {
            vm.submit(tab, manual)
            manual = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (cameraOn) {
            if (hasPermission) {
                CameraScanner(
                    enabled = !state.submitting && !cooling,
                    onBarcode = { vm.onScanned(tab, it) },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(120.dp).background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Camera toestaan om te scannen", color = Color.White)
                    }
                }
            }
        }
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.page?.heading?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
            ResultBanner(state.result, state.submitting)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("Leerlingnummer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitManual() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { submitManual() }, enabled = manual.isNotBlank() && !state.submitting) {
                    Text(if (tab == Tab.VERKOOP) "Opslaan" else "Controleren")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tab == Tab.VERKOOP) {
                    Checkbox(checked = vm.introducee, onCheckedChange = { vm.introducee = it })
                    Text(
                        "met introducee (geldt voor de volgende scan)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = { cameraOn = !cameraOn }) {
                    Icon(
                        if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        contentDescription = if (cameraOn) "Camera uit" else "Camera aan",
                    )
                }
            }
            if (tab == Tab.CONTROLE) {
                state.page?.counts?.let { CountsRow("Binnen", it) }
            } else {
                state.page?.hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.loadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()
        Text(
            "Nieuwste mutaties",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp),
        )
        RowList(
            rows = state.page?.rows ?: emptyList(),
            loading = state.loading,
            emptyText = "Nog niets",
            onDelete = { vm.delete(tab, it) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** overzicht.php */
@Composable
fun OverzichtScreen(vm: MainViewModel) {
    val state = vm.state(Tab.OVERZICHT)
    LaunchedEffect(Unit) { vm.load(Tab.OVERZICHT) }
    var query by rememberSaveable { mutableStateOf("") }

    val rows = state.page?.rows ?: emptyList()
    val filtered = remember(rows, query) {
        val q = query.trim()
        if (q.isEmpty()) rows
        else rows.filter { r ->
            r["gast"]?.text?.contains(q, ignoreCase = true) == true ||
                r["barcode"]?.text?.contains(q, ignoreCase = true) == true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.page?.counts?.let { CountsRow("Verkocht", it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !vm.alphabetic,
                    onClick = { vm.updateAlphabetic(false) },
                    label = { Text("Chronologisch") },
                )
                FilterChip(
                    selected = vm.alphabetic,
                    onClick = { vm.updateAlphabetic(true) },
                    label = { Text("Alfabetisch") },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.showDeleted, onCheckedChange = { vm.updateShowDeleted(it) })
                Spacer(Modifier.width(8.dp))
                Text("Toon verwijderd")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Zoek op naam of nummer") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.loadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()
        RowList(
            rows = filtered,
            loading = state.loading,
            emptyText = if (query.isBlank()) "Geen kaartjes" else "Niets gevonden",
            onDelete = { vm.delete(Tab.OVERZICHT, it) },
            modifier = Modifier.weight(1f),
        )
    }
}
