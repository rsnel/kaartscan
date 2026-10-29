package it.snel.kaartscan.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import it.snel.kaartscan.MainViewModel
import it.snel.kaartscan.UpdateState
import java.io.File

/** Offer, download progress and install of a new version; shown over any screen. */
@Composable
fun UpdateDialog(vm: MainViewModel) {
    if (!vm.updateDialog) return
    val context = LocalContext.current
    var hint by remember { mutableStateOf<String?>(null) }
    val state = vm.update

    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) vm.dismissUpdate() },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        title = {
            Text(
                when (state) {
                    is UpdateState.Failed -> "Update mislukt"
                    is UpdateState.Ready -> "Klaar om te installeren"
                    else -> "Nieuwe versie"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is UpdateState.Available -> Text(
                        if (state.knownOlder) "Versie ${state.release.version} is beschikbaar. Je hebt nu ${state.current}."
                        else "Versie ${state.release.version} is gepubliceerd. Je hebt een ontwikkelversie (${state.current})."
                    )
                    is UpdateState.Downloading -> {
                        Text("Versie ${state.release.version} downloaden…")
                        val p = state.progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is UpdateState.Ready -> Text(
                        "Versie ${state.release.version} is gedownload. Android vraagt nu of je de update wilt installeren."
                    )
                    is UpdateState.Failed -> Text(state.message)
                    else -> Text("Bezig…")
                }
                hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { vm.downloadUpdate() }) { Text("Downloaden en installeren") }
                is UpdateState.Ready -> TextButton(onClick = { hint = install(context, state.file) }) { Text("Installeren") }
                is UpdateState.Failed -> if (state.release != null) {
                    TextButton(onClick = { vm.downloadUpdate() }) { Text("Opnieuw") }
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismissUpdate() }) {
                Text(
                    when (state) {
                        is UpdateState.Downloading -> "Annuleren"
                        is UpdateState.Failed -> "Sluiten"
                        else -> "Later"
                    }
                )
            }
        },
    )
}

/**
 * Hands the APK to Android's package installer. Returns a hint for the
 * user when that is not possible yet, or null when the installer opened.
 */
private fun install(context: Context, file: File): String? {
    // Android 8+: the user allows "install unknown apps" once for Kaartscan
    if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            return "Sta in de Android-instellingen 'onbekende apps installeren' toe voor Kaartscan en tik dan opnieuw op Installeren."
        }
        return "Zet 'Toestaan van deze bron' aan, ga terug en tik opnieuw op Installeren."
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "Geen installatieprogramma gevonden op deze telefoon."
    }
}
