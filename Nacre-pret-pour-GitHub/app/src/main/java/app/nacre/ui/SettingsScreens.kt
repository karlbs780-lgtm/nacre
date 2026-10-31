package app.nacre.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nacre.LibraryViewModel
import app.nacre.data.Settings

@Composable fun SettingsScreen(vm: LibraryViewModel, settings: Settings, back: () -> Unit, scan: () -> Unit) {
    var exclusionText by rememberSaveable { mutableStateOf(settings.exclusions.joinToString("\n")) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("À votre image", "Paramètres") { ActionIcon(Icons.Rounded.ArrowBack, "Retour", onClick = back) } }
        item {
            Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Text("Une couleur, une atmosphère", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("lilac" to "Iris", "mint" to "Glacier", "peach" to "Aube").forEach { (key, label) ->
                        FilterChip(selected = settings.palette == key, onClick = { vm.prefs.update(settings.copy(palette = key)) }, label = { Text(label) })
                    }
                }
                Text("Apparence du lecteur", color = Muted, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(settings.playerStyle == "square", { vm.prefs.update(settings.copy(playerStyle = "square")) }, label = { Text("Pochette") })
                    FilterChip(settings.playerStyle == "vinyl", { vm.prefs.update(settings.copy(playerStyle = "vinyl")) }, label = { Text("Disque") })
                }
                SettingToggle("Transitions animées", "Désactivez-les pour une interface plus sobre.", settings.motion) { vm.prefs.update(settings.copy(motion = it)) }
                SettingToggle("Onde audio", "Amplitude réelle du signal décodé, sans accès au micro.", settings.visualizer) { vm.prefs.update(settings.copy(visualizer = it)) }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
        item {
            Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Text("Votre bibliothèque", style = MaterialTheme.typography.titleMedium)
                Text("Un dossier ou un chemin de fichier par ligne, relatif au stockage partagé (Android 10+). Exemple : Notifications ou Music/Privé. Sur Android 8/9, utilisez le chemin absolu affiché dans Dossiers. Les imports manuels ne sont pas exclus.",
                    color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
                OutlinedTextField(exclusionText, { exclusionText = it }, label = { Text("Exclusions du scan") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    vm.prefs.update(settings.copy(exclusions = exclusionText.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()))
                    vm.scan(); vm.message.value = "Exclusions enregistrées. Les fichiers ne sont jamais supprimés."
                }) { Text("Enregistrer et actualiser") }
                FilledTonalButton(onClick = scan) { Icon(Icons.Rounded.LibraryAdd, null); Text("  Scan et importation") }
            }
        }
        item {
            Column(Modifier.padding(28.dp)) {
                Text("Privé, par nature.", style = MaterialTheme.typography.titleMedium)
                Text("Nacre n’utilise ni compte, ni abonnement, ni permission Internet. Vos fichiers, playlists et recherches restent sur cet appareil. Aucune sauvegarde cloud Android n’est activée.", color = Muted,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
                Text("NACRE 0.1 · ANDROID NATIF", letterSpacing = 2.sp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
@Composable private fun SettingToggle(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(checked, change)
    }
}
@Composable fun ScanScreen(vm: LibraryViewModel, back: () -> Unit, imports: () -> Unit, scan: () -> Unit, permissions: () -> Unit) {
    val state by vm.scan.collectAsStateWithLifecycle()
    val data by vm.index.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Ouvrez votre univers", "Vos fichiers.") { ActionIcon(Icons.Rounded.ArrowBack, "Retour", onClick = back) } }
        item {
            Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Icon(Icons.Rounded.Sensors, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))
                Text(if (state.running) "À la recherche de pépites…" else "Tout commence ici.", style = MaterialTheme.typography.headlineSmall)
                Text("Le scan lit l’index audio Android. L’import vous laisse choisir des fichiers précis, sans autoriser tout le stockage.", color = Muted,
                    modifier = Modifier.padding(vertical = 16.dp))
                if (state.running) {
                    if (state.total > 0) LinearProgressIndicator(progress = { (state.done.toFloat() / state.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }
                Text(state.message, style = MaterialTheme.typography.bodySmall)
                if (state.error != null) Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(20.dp))
                Button(onClick = scan, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Sync, null); Text(if (vm.canScan()) "  Scanner le téléphone" else "  Autoriser et scanner")
                }
                OutlinedButton(onClick = imports, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.FileOpen, null); Text("  Choisir des fichiers")
                }
                TextButton(onClick = permissions, modifier = Modifier.fillMaxWidth()) { Text("Permissions dans les réglages Android") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${data.tracks.count { it.available }}", fontSize = 32.sp, fontWeight = FontWeight.Light)
                    Text("titres disponibles", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${data.albums.size}", fontSize = 32.sp, fontWeight = FontWeight.Light)
                    Text("albums", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("MP3 · FLAC · WAV · AAC · OGG · M4A", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text("La lecture dépend des codecs disponibles sur votre appareil. Les fichiers protégés par DRM ne sont pas pris en charge.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Les copies identiques sont fusionnées grâce à leur empreinte SHA-256. Le premier scan peut être long : seuls les fichiers nouveaux ou modifiés sont relus ensuite.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Un fichier absent de l’index Android peut être importé manuellement. Les dossiers exclus se gèrent dans Paramètres ; vos fichiers ne sont jamais modifiés.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
