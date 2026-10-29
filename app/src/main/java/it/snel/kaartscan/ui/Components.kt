package it.snel.kaartscan.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.snel.kaartscan.Aantallen
import it.snel.kaartscan.Counts
import it.snel.kaartscan.Message
import it.snel.kaartscan.Entry

/** Big coloured result of the last scan, readable at arm's length. */
@Composable
fun ResultBanner(msg: Message?, busy: Boolean, modifier: Modifier = Modifier) {
    val bg: Color
    val fg: Color
    when {
        busy || msg == null -> {
            bg = MaterialTheme.colorScheme.surfaceVariant
            fg = MaterialTheme.colorScheme.onSurfaceVariant
        }
        msg.isError -> {
            bg = ErrorRed
            fg = Color.White
        }
        else -> {
            bg = SuccessGreen
            fg = Color.White
        }
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(32.dp), color = fg, strokeWidth = 3.dp)
                msg == null -> Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(32.dp))
                msg.isError -> Icon(Icons.Filled.Error, null, Modifier.size(32.dp))
                else -> Icon(Icons.Filled.CheckCircle, null, Modifier.size(32.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                when {
                    busy -> Text("Bezig…", style = MaterialTheme.typography.titleLarge)
                    msg == null -> Text("Klaar om te scannen", style = MaterialTheme.typography.titleLarge)
                    else -> {
                        if (msg.title.isNotEmpty()) {
                            Text(msg.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(msg.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun CountsRow(label: String, counts: Counts, introduceeToegestaan: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        // a party without introducees (and none in the data): just the students
        if (!introduceeToegestaan && counts.introducees == "0") {
            Stat(label, counts.leerlingen)
        } else {
            Stat(label, counts.total)
            Stat("Leerlingen", counts.leerlingen)
            Stat("Introducees", counts.introducees)
        }
    }
}

/** Kaartcontrole: inside / sold, per category, or only students for a party without introducees. */
@Composable
fun BinnenRow(aantallen: Aantallen, introduceeToegestaan: Boolean, modifier: Modifier = Modifier) {
    val binnen = aantallen.binnen
    val verkocht = aantallen.verkocht
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        if (!introduceeToegestaan && binnen.introducees == "0" && verkocht.introducees == "0") {
            Ratio("Leerlingen binnen", binnen.leerlingen, verkocht.leerlingen)
        } else {
            Ratio("Binnen", binnen.total, verkocht.total)
            Ratio("Leerlingen", binnen.leerlingen, verkocht.leerlingen)
            Ratio("Introducees", binnen.introducees, verkocht.introducees)
        }
    }
}

/** "87 / 143": inside, then sold in a smaller, quieter type */
@Composable
private fun Ratio(label: String, inside: String, sold: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inside) }
                withStyle(
                    SpanStyle(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                ) { append(" / $sold") }
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** List of kaartjes or controles with a confirmation dialog for 'verwijderen'. */
@Composable
fun RowList(
    rows: List<Entry>,
    loading: Boolean,
    emptyText: String,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // (entry, true = check out / false = delete)
    var pending by remember { mutableStateOf<Pair<Entry, Boolean>?>(null) }
    Box(modifier) {
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (rows.isEmpty() && !loading) {
                item { Text(emptyText, style = MaterialTheme.typography.bodyMedium) }
            }
            items(rows) { row ->
                RowCard(
                    row,
                    onDeleteClick = { pending = row to false },
                    onExitClick = { pending = row to true },
                )
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
    }
    pending?.let { (row, exit) ->
        val who = "${row.gast ?: "onbekend"} (${row.barcode})"
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (exit) "Niet meer binnen?" else "Verwijderen?") },
            text = {
                Text(
                    if (exit) "De aanwezigheid van $who wordt verwijderd. Het kaartje blijft bestaan."
                    else who
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    (if (exit) row.exitLink else row.deleteLink)?.let(onDelete)
                    pending = null
                }) { Text(if (exit) "Uitchecken" else "Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuleren") } },
        )
    }
}

@Composable
fun RowCard(row: Entry, onDeleteClick: () -> Unit, onExitClick: () -> Unit = {}) {
    val deleted = row.verwijderd != null
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.gast ?: "onbekend",
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (deleted) TextDecoration.LineThrough else null,
                )
                Row {
                    Text(row.barcode, style = MaterialTheme.typography.bodyMedium)
                    if (row.introducee) {
                        Text(
                            "  + introducee",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                val meta = listOfNotNull(
                    row.tijdstip.takeIf { it.isNotBlank() },
                    "door ${row.door}",
                    row.bron?.takeIf { it.isNotBlank() }?.let { "bron: $it" },
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.binnen?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = SuccessGreen)
                }
                row.verwijderd?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            // someone who is inside is checked out first; only then can the sale go
            when {
                row.exitLink != null -> IconButton(onClick = onExitClick) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Uitchecken")
                }
                row.deleteLink != null -> IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, contentDescription = "Verwijderen")
                }
            }
        }
    }
}

/** Beep + vibration, so the person at the door does not have to look at the screen. */
@Composable
fun rememberFeedback(): (Boolean) -> Unit {
    val context = LocalContext.current
    val tone = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        } catch (e: RuntimeException) {
            null
        }
    }
    DisposableEffect(tone) {
        onDispose { tone?.release() }
    }
    return remember(tone) {
        { isError: Boolean ->
            if (isError) tone?.startTone(ToneGenerator.TONE_SUP_ERROR, 700)
            else tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            vibrate(context, if (isError) longArrayOf(0, 150, 100, 150, 100, 300) else longArrayOf(0, 60))
        }
    }
}

private fun vibrate(context: Context, pattern: LongArray) {
    val vibrator = vibrator(context) ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= 26) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else {
        vibrateLegacy(vibrator, pattern)
    }
}

@Suppress("DEPRECATION")
private fun vibrateLegacy(vibrator: Vibrator, pattern: LongArray) {
    vibrator.vibrate(pattern, -1)
}

@Suppress("DEPRECATION")
private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
