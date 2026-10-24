package it.snel.kaartscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.snel.kaartscan.ServerSnapshot
import it.snel.kaartscan.ServerStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Grey = Color(0xFF9E9E9E)
private val Amber = Color(0xFFF9A825)

private fun ServerStatus.color(): Color = when (this) {
    ServerStatus.UNKNOWN -> Grey
    ServerStatus.OK -> SuccessGreen
    ServerStatus.UNEXPECTED -> Amber
    ServerStatus.DOWN -> ErrorRed
}

private fun ServerStatus.label(): String = when (this) {
    ServerStatus.UNKNOWN -> "Nog niet gecontroleerd"
    ServerStatus.OK -> "Verbonden"
    ServerStatus.UNEXPECTED -> "Onverwacht antwoord"
    ServerStatus.DOWN -> "Geen verbinding"
}

private fun ServerStatus.explanation(): String = when (this) {
    ServerStatus.UNKNOWN -> "De app heeft de server nog niet bereikt of geprobeerd."
    ServerStatus.OK -> "De server antwoordt normaal."
    ServerStatus.UNEXPECTED -> "De server antwoordde, maar niet zoals verwacht. Controleer of scans goed zijn opgeslagen."
    ServerStatus.DOWN -> "Geen verbinding met de server of geen antwoord. Controleer wifi of mobiele data."
}

/**
 * Coloured dot in the top bar; a tap shows what the app knows about the
 * server. Nothing is shown until the first answer (or failure) is in.
 * [loggedIn] only decides whether user and party are listed.
 */
@Composable
fun ServerStatusDot(
    snapshot: ServerSnapshot,
    loggedIn: Boolean,
    server: String,
    user: String?,
    feest: String?,
    checking: Boolean = false,
    onCheckNow: (() -> Unit)? = null,
) {
    val status = snapshot.status
    val title = status.label()
    val explanation = status.explanation()
    var open by remember { mutableStateOf(false) }

    IconButton(
        onClick = { open = true },
        modifier = Modifier.semantics { contentDescription = "Serverstatus: $title" },
    ) {
        if (status != ServerStatus.UNKNOWN) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(status.color(), CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }

    if (open) {
        ServerStatusDialog(status, title, explanation, snapshot, loggedIn, server, user, feest, checking, onCheckNow) { open = false }
    }
}

@Composable
private fun ServerStatusDialog(
    status: ServerStatus,
    title: String,
    explanation: String,
    s: ServerSnapshot,
    loggedIn: Boolean,
    server: String,
    user: String?,
    feest: String?,
    checking: Boolean,
    onCheckNow: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // tick, so "12 s geleden" keeps counting while the dialog is open
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }
    fun moment(t: Long?): String = t?.let { "${clock.format(Date(it))} (${ago(now - it)})" } ?: "nog niet"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Box(Modifier.size(20.dp).background(status.color(), CircleShape)) },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(explanation, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(4.dp))
                Line("Server", server)
                if (loggedIn) {
                    Line("Ingelogd als", user ?: "?")
                    Line("Feest", feest ?: "onbekend")
                }
                Line("Laatste contact", moment(s.lastContactAt))
                Line(
                    "Responstijd",
                    s.lastLatencyMs?.let { "${it} ms (gemiddeld ${s.avgLatencyMs} ms)" } ?: "nog niet gemeten",
                )
                Line("Verzoeken", "${s.okCount} goed, ${s.unexpectedCount} onverwacht, ${s.downCount} mislukt")
                Line(
                    "Meldingen",
                    when (s.meldingApi) {
                        true -> "via api.php"
                        false -> "via de pagina (api.php zonder melding)"
                        null -> "nog niet gebruikt"
                    },
                )
                Line(
                    "Foutmeldingen api.php",
                    when (s.jsonErrors) {
                        true -> "JSON (phplib-patch actief)"
                        false -> "inlogpagina (zonder phplib-patch)"
                        null -> "nog niet gezien"
                    },
                )
                if (s.lastProblem != null) {
                    Line("Laatste probleem", "${moment(s.lastProblemAt)}\n${s.lastProblem}")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Sluiten") } },
        dismissButton = onCheckNow?.let { check ->
            {
                TextButton(onClick = check, enabled = !checking) {
                    Text(if (checking) "Bezig…" else "Nu controleren")
                }
            }
        },
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun ago(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "$s s geleden"
        s < 3600 -> "${s / 60} min geleden"
        else -> "${s / 3600} uur geleden"
    }
}
