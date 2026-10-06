package pl.misieklab.nfccardlab.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class AppChoice(val packageName: String, val label: String)

/** Current-profile launcher apps only; no QUERY_ALL_PACKAGES or network access. */
internal fun launcherApps(manager: PackageManager): List<AppChoice> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return manager.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled &&
            it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED == 0 }
        .map { AppChoice(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<AppChoice> { it.label.lowercase(Locale.getDefault()) }.thenBy { it.packageName })
}

@Composable
internal fun AppPicker(onDismiss: () -> Unit, onSelect: (AppChoice) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppChoice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            apps = withContext(Dispatchers.IO) { launcherApps(context.packageManager) }
        } catch (_: SecurityException) {
            failed = true
        } finally {
            loading = false
        }
    }
    val filtered = apps.filter { it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Wybierz aplikację") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(search, { search = it }, label = { Text("Szukaj aplikacji") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when {
                    loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Wczytuję aplikacje…") }
                    failed -> Text("Telefon nie udostępnił listy aplikacji. Zamknij okno i spróbuj ponownie.")
                    filtered.isEmpty() -> Text("Brak pasujących aplikacji. Lista obejmuje aplikacje uruchamiane z ekranu telefonu w bieżącym profilu.")
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            TextButton(onClick = { onSelect(app) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } })
}
