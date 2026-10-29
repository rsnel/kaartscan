package it.snel.kaartscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.snel.kaartscan.LogEvent
import it.snel.kaartscan.LogSoort
import it.snel.kaartscan.MainViewModel
import it.snel.kaartscan.ScreenState
import it.snel.kaartscan.Tab

private fun LogSoort.icon(): ImageVector = when (this) {
    LogSoort.VERKOOP -> Icons.Filled.ConfirmationNumber
    LogSoort.BINNEN -> Icons.Filled.HowToReg
    LogSoort.UITGECHECKT -> Icons.AutoMirrored.Filled.ExitToApp
    LogSoort.VERKOOP_VERWIJDERD -> Icons.Filled.DeleteForever
}

@Composable
private fun LogSoort.color(): Color = when (this) {
    LogSoort.VERKOOP -> MaterialTheme.colorScheme.primary
    LogSoort.BINNEN -> SuccessGreen
    LogSoort.UITGECHECKT -> CheckoutAmber
    LogSoort.VERKOOP_VERWIJDERD -> ErrorRed
}

/** Every sale, check-in, check-out and deleted sale of this party, newest first. */
@Composable
fun LogScreen(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.load(Tab.LOG) }
    LogContent(
        state = vm.state(Tab.LOG),
        zoek = vm.logZoek,
        onZoekChange = { vm.updateLogZoek(it) },
        onRefresh = { vm.load(Tab.LOG) },
    )
}

@Composable
internal fun LogContent(
    state: ScreenState,
    zoek: String,
    onZoekChange: (String) -> Unit,
    onRefresh: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = zoek,
                onValueChange = onZoekChange,
                label = { Text("Zoek leerling op naam of nummer") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (zoek.isNotEmpty()) {
                        IconButton(onClick = { onZoekChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Zoekveld leegmaken")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.loadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()
        Refreshable(state.loading, onRefresh, Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.loaded && state.log.isEmpty()) {
                    item {
                        Text(
                            if (zoek.isBlank()) "Nog niets gebeurd" else "Niets gevonden",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                items(state.log) { e ->
                    LogRow(e)
                    HorizontalDivider(Modifier.padding(start = 60.dp))
                }
            }
        }
    }
}

@Composable
private fun LogRow(e: LogEvent) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(e.soort.color().copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(e.soort.icon(), contentDescription = null, tint = e.soort.color(), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.soort.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = e.soort.color(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    e.tijdstip.substringAfter(' '),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                (e.gast ?: "onbekend") + if (e.introducee) "  + introducee" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            // the source belongs to the sale: scan or wiscollect
            val bron = e.bron?.takeIf { e.soort == LogSoort.VERKOOP || e.soort == LogSoort.VERKOOP_VERWIJDERD }
            Text(
                listOfNotNull(e.barcode, "door ${e.door}", bron?.let { "bron: $it" }, e.tijdstip.substringBefore(' '))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
