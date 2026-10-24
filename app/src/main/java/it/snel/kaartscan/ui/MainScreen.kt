package it.snel.kaartscan.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import it.snel.kaartscan.MainViewModel
import it.snel.kaartscan.Me
import it.snel.kaartscan.ScreenState
import it.snel.kaartscan.Tab
import kotlinx.coroutines.delay

private fun Tab.label(): String = when (this) {
    Tab.VERKOOP -> "Kaartverkoop"
    Tab.OVERZICHT -> "Overzicht"
    Tab.CONTROLE -> "Kaartcontrole"
}

private fun Tab.description(): String = when (this) {
    Tab.VERKOOP -> "Kaartjes verkopen: scan de leerlingpas"
    Tab.OVERZICHT -> "Alle verkochte kaartjes en aantallen"
    Tab.CONTROLE -> "Bij de deur: scan de leerlingpas"
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.VERKOOP -> Icons.Filled.ConfirmationNumber
    Tab.OVERZICHT -> Icons.AutoMirrored.Filled.List
    Tab.CONTROLE -> Icons.Filled.HowToReg
}

@Composable
fun MainScreen(vm: MainViewModel, me: Me) {
    // null: the start screen
    var tab by rememberSaveable { mutableStateOf<Tab?>(null) }
    BackHandler(enabled = tab != null) { tab = null }

    // check the server every 30 s while the app is on screen, so the status
    // dot turns red when the connection drops between scans
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.heartbeat()
            while (true) {
                delay(MainViewModel.HEARTBEAT_MS)
                vm.heartbeat()
            }
        }
    }
    val server by vm.server.collectAsState()

    MainScaffold(
        tab, me,
        onBack = { tab = null },
        onRefresh = { t -> vm.load(t) },
        statusDot = {
            ServerStatusDot(
                snapshot = server,
                loggedIn = true,
                server = vm.baseUrl,
                user = me.username,
                feest = me.feestNaam,
                checking = vm.checking,
                onCheckNow = { vm.heartbeat(onlyIfStale = false) },
            )
        },
    ) {
        when (tab) {
            null -> StartScreen(onSelect = { tab = it }, onLogout = { vm.logout() })
            Tab.VERKOOP -> ScanScreen(vm, Tab.VERKOOP)
            Tab.OVERZICHT -> OverzichtScreen(vm)
            Tab.CONTROLE -> ScanScreen(vm, Tab.CONTROLE)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScaffold(
    tab: Tab?,
    me: Me,
    onBack: () -> Unit,
    onRefresh: (Tab) -> Unit,
    statusDot: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (tab != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                        }
                    }
                },
                title = {
                    Column {
                        Text(tab?.label() ?: "Kaartscan OVC")
                        // the party on every screen, so nobody sells for the wrong one
                        Text(
                            listOfNotNull(me.feestNaam, me.username).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    statusDot()
                    if (tab != null) {
                        IconButton(onClick = { onRefresh(tab) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Vernieuwen")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            content()
        }
    }
}

@Composable
fun StartScreen(onSelect: (Tab) -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (t in Tab.entries) {
            StartCard(t.icon(), t.label(), t.description()) { onSelect(t) }
        }
        StartCard(Icons.AutoMirrored.Filled.Logout, "Uitloggen", "Sessie beëindigen", onLogout)
    }
}

@Composable
private fun StartCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

    ScanContent(
        tab = tab,
        state = state,
        introducee = vm.introducee,
        introduceeToegestaan = vm.me?.introduceeToegestaan ?: true,
        onIntroduceeChange = { vm.introducee = it },
        manual = manual,
        onManualChange = { manual = it },
        onSubmit = submitManual,
        cameraOn = cameraOn,
        onToggleCamera = { cameraOn = !cameraOn },
        onDelete = { vm.delete(tab, it) },
    ) {
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
}

/** Stateless part of [ScanScreen]; [camera] is shown at the top while [cameraOn]. */
@Composable
internal fun ScanContent(
    tab: Tab,
    state: ScreenState,
    introducee: Boolean,
    introduceeToegestaan: Boolean,
    onIntroduceeChange: (Boolean) -> Unit,
    manual: String,
    onManualChange: (String) -> Unit,
    onSubmit: () -> Unit,
    cameraOn: Boolean,
    onToggleCamera: () -> Unit,
    onDelete: (String) -> Unit,
    camera: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (cameraOn) camera()
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ResultBanner(state.result, state.submitting)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = onManualChange,
                    label = { Text("Leerlingnummer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSubmit, enabled = manual.isNotBlank() && !state.submitting) {
                    Text(if (tab == Tab.VERKOOP) "Opslaan" else "Controleren")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tab == Tab.VERKOOP && introduceeToegestaan) {
                    Checkbox(checked = introducee, onCheckedChange = onIntroduceeChange)
                    Text(
                        "met introducee (geldt voor de volgende scan)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onToggleCamera) {
                    Icon(
                        if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        contentDescription = if (cameraOn) "Camera uit" else "Camera aan",
                    )
                }
            }
            if (tab == Tab.CONTROLE) {
                state.aantallen?.let { CountsRow("Binnen", it.binnen, introduceeToegestaan) }
            } else {
                Text(
                    "De barcode moet een leerlingnummer zijn van minstens 6 cijfers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            rows = state.entries,
            loading = state.loading,
            emptyText = if (state.loaded) "Nog niets" else "",
            onDelete = onDelete,
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
    OverzichtContent(
        state = state,
        introduceeToegestaan = vm.me?.introduceeToegestaan ?: true,
        alphabetic = vm.alphabetic,
        onAlphabeticChange = { vm.updateAlphabetic(it) },
        showDeleted = vm.showDeleted,
        onShowDeletedChange = { vm.updateShowDeleted(it) },
        query = query,
        onQueryChange = { query = it },
        onDelete = { vm.delete(Tab.OVERZICHT, it) },
    )
}

@Composable
internal fun OverzichtContent(
    state: ScreenState,
    introduceeToegestaan: Boolean,
    alphabetic: Boolean,
    onAlphabeticChange: (Boolean) -> Unit,
    showDeleted: Boolean,
    onShowDeletedChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val rows = state.entries
    val filtered = remember(rows, query) {
        val q = query.trim()
        if (q.isEmpty()) rows
        else rows.filter { r ->
            r.gast?.contains(q, ignoreCase = true) == true || r.barcode.contains(q, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.aantallen?.let { CountsRow("Verkocht", it.verkocht, introduceeToegestaan) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !alphabetic,
                    onClick = { onAlphabeticChange(false) },
                    label = { Text("Chronologisch") },
                )
                FilterChip(
                    selected = alphabetic,
                    onClick = { onAlphabeticChange(true) },
                    label = { Text("Alfabetisch") },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showDeleted, onCheckedChange = onShowDeletedChange)
                Spacer(Modifier.width(8.dp))
                Text("Toon verwijderd")
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
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
            emptyText = when {
                !state.loaded -> ""
                query.isBlank() -> "Geen kaartjes"
                else -> "Niets gevonden"
            },
            onDelete = onDelete,
            modifier = Modifier.weight(1f),
        )
    }
}
