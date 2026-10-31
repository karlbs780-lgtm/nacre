package app.nacre.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nacre.LibraryIndex
import app.nacre.LibraryViewModel
import app.nacre.audio.PlaybackState
import app.nacre.data.*
import app.nacre.domain.moved
import app.nacre.domain.timeLabel

@Composable private fun SectionTitle(title: String, detail: String = "", onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (onClick != null) TextButton(onClick = onClick) { Text(detail.ifBlank { "Tout voir" }) }
        else if (detail.isNotBlank()) Text(detail, color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}
@Composable fun HomeScreen(vm: LibraryViewModel, data: LibraryIndex, playback: PlaybackState,
    navigate: (String) -> Unit, detail: (String, String) -> Unit, add: (Track) -> Unit) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageTitle("Votre refuge sonore", "À votre rythme.") {
            ActionIcon(Icons.Rounded.Settings, "Paramètres", onClick = { navigate("settings") })
        } }
        item {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.OfflineBolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text("  Hors ligne · Sans compte · Sans limites", color = Muted, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
        item {
            Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), onClick = {
                if (data.history.isNotEmpty()) vm.audio.play(data.history, data.history.first()) else navigate("scan")
            }) {
                Text("L’INSTANT NACRE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(if (data.history.isNotEmpty()) "Retrouvez votre\nfréquence." else "Votre musique,\nvotre univers.", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(12.dp))
                        Text(if (data.history.isNotEmpty()) "Reprendre vos dernières écoutes" else "Importez vos premiers morceaux", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Cover(data.history.firstOrNull() ?: data.recent.firstOrNull(), Modifier.size(104.dp), round = true)
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (data.history.isEmpty()) "  Explorer mes fichiers" else "  Lancer la sélection", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (scan.running) item {
            LinearProgressIndicator(modifier = Modifier.padding(24.dp).fillMaxWidth())
            Text(scan.message, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        if (!data.ready) item { CircularProgressIndicator(Modifier.padding(24.dp)) }
        else if (data.tracks.none { it.available }) item {
            EmptyState("Le silence avant la musique", "Aucun morceau de démonstration : cette bibliothèque attend vos propres fichiers.", "Scanner et importer", { navigate("scan") })
        }
        if (data.albums.isNotEmpty()) {
            item { SectionTitle("À redécouvrir", onClick = { navigate("albums") }) }
            item {
                val albums = remember(data.albums) { data.albums.entries.take(12) }
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(albums, key = { it.key }) { entry ->
                        Column(Modifier.width(145.dp).clickable { detail("albums", entry.key) }) {
                            Cover(entry.value.first(), Modifier.size(145.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(entry.value.first().album, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(entry.value.first().artist, maxLines = 1, color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (data.recent.isNotEmpty()) {
            item { SectionTitle("Tout juste arrivés", "${data.recent.size} titres") }
            items(data.recent.take(8), key = { "recent:${it.id}" }) { track ->
                MusicRow(vm, track, data.recent, playback, add)
            }
        }
        item { TextButton(onClick = { navigate("scan") }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Rounded.AddCircleOutline, null); Text("  Enrichir ma bibliothèque")
        } }
    }
}
@Composable private fun MusicRow(vm: LibraryViewModel, track: Track, queue: List<Track>, state: PlaybackState, add: (Track) -> Unit) {
    TrackRow(track, state.id == track.id, { vm.audio.play(queue, track) }, { vm.favorite(track) }) {
        ActionIcon(Icons.Rounded.PlaylistAdd, "Ajouter à une playlist", onClick = { add(track) })
    }
}
@Composable fun LibraryScreen(vm: LibraryViewModel, data: LibraryIndex, playback: PlaybackState,
    navigate: (String) -> Unit, detail: (String, String) -> Unit, add: (Track) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Tous") }
    val tracks = when (filter) { "Favoris" -> data.favorites; "Récents" -> data.recent; "Historique" -> data.history; else -> data.tracks.filter { it.available } }
    LazyColumn {
        item { PageTitle("Collection personnelle", "Bibliothèque") {
            ActionIcon(Icons.Rounded.Sync, "Scanner et importer", onClick = { navigate("scan") })
        } }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Tous", "Favoris", "Récents", "Historique")) { type ->
                    FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(type) })
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf("artists" to "Artistes", "albums" to "Albums", "genres" to "Genres", "folders" to "Dossiers")) { (route, title) ->
                    OutlinedButton(onClick = { navigate(route) }) { Text(title) }
                }
            }
        }
        item { SectionTitle(filter, "${tracks.size} titres") }
        if (tracks.isEmpty()) item {
            EmptyState("Rien ici pour le moment", if (filter == "Tous") "Scannez votre téléphone ou importez vos fichiers audio." else "Votre sélection se remplira au fil de vos écoutes.", "Importer", { navigate("scan") })
        }
        items(tracks, key = { it.id }, contentType = { "track" }) { MusicRow(vm, it, tracks, playback, add) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
fun collectionGroups(kind: String, data: LibraryIndex): Map<String, List<Track>> = when (kind) {
    "artists" -> data.artists; "genres" -> data.genres; "folders" -> data.folders; else -> data.albums
}
fun groupLabel(kind: String) = when (kind) { "artists" -> "Artistes"; "genres" -> "Genres"; "folders" -> "Dossiers"; else -> "Albums" }
@Composable fun GroupsScreen(kind: String, data: LibraryIndex, back: () -> Unit, open: (String) -> Unit) {
    val groups = collectionGroups(kind, data)
    LazyColumn {
        item { PageTitle("Explorer la collection", groupLabel(kind)) { ActionIcon(Icons.Rounded.ArrowBack, "Retour", onClick = back) } }
        if (groups.isEmpty()) item { EmptyState("Aucune collection", "Les métadonnées des fichiers importés apparaîtront ici.") }
        items(groups.entries.toList(), key = { it.key }) { entry ->
            Row(Modifier.fillMaxWidth().clickable { open(entry.key) }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(entry.value.first(), Modifier.size(70.dp), round = kind == "artists")
                Column(Modifier.weight(1f).padding(16.dp)) {
                    Text(entry.key, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${entry.value.size} titres", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Rounded.ChevronRight, "Ouvrir", tint = Muted)
            }
        }
    }
}
@Composable fun CollectionScreen(vm: LibraryViewModel, data: LibraryIndex, kind: String, key: String, state: PlaybackState, back: () -> Unit, add: (Track) -> Unit) {
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val playlistId = key.toLongOrNull()
    val lookup = remember(data.tracks) { data.tracks.associateBy { it.id } }
    val stored = if (kind == "playlist") entries.filter { it.playlistId == playlistId }.sortedBy { it.position }.mapNotNull { lookup[it.trackId] }
        else collectionGroups(kind, data)[key].orEmpty()
    var local by remember(kind, key) { mutableStateOf(stored) }
    LaunchedEffect(stored) { local = stored }
    val latest by rememberUpdatedState(local)
    val title = if (kind == "playlist") lists.firstOrNull { it.id == playlistId }?.name ?: "Playlist" else key
    val haptic = LocalHapticFeedback.current
    val rowHeight = with(LocalDensity.current) { 76.dp.toPx() }
    LazyColumn {
        item { PageTitle(if (kind == "playlist") "Votre sélection" else groupLabel(kind), title) { ActionIcon(Icons.Rounded.ArrowBack, "Retour", onClick = back) } }
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(local.firstOrNull(), Modifier.sizeIn(maxWidth = 260.dp, maxHeight = 260.dp).size(220.dp), round = kind == "artists")
                Spacer(Modifier.height(20.dp))
                Text("${local.size} titres · ${timeLabel(local.sumOf { it.duration })}", color = Muted)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { local.firstOrNull { it.available }?.let { vm.audio.play(local, it) } }, enabled = local.any { it.available }) {
                    Icon(Icons.Rounded.PlayArrow, null); Text("  Tout écouter")
                }
                if (kind == "playlist") Text("Appui long sur la poignée pour réordonner", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (kind == "folders") TextButton(onClick = {
                    vm.prefs.update(vm.settings.value.copy(exclusions = vm.settings.value.exclusions + key)); vm.scan(); back()
                }) { Text("Exclure ce dossier du scan") }
            }
        }
        if (local.isEmpty()) item { EmptyState("Votre prochaine sélection", "Ajoutez des morceaux depuis la bibliothèque avec le bouton playlist.") }
        items(local, key = { it.id }, contentType = { "track" }) { track ->
            if (kind == "playlist" && playlistId != null) {
                TrackRow(track, state.id == track.id, { vm.audio.play(local, track) }, { vm.favorite(track) }) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Icon(Icons.Rounded.DragHandle, "Appui long pour déplacer", Modifier.size(48.dp).clickable { menu = true }
                            .padding(10.dp).pointerInput(track.id, playlistId) {
                                var distance = 0f
                                detectDragGesturesAfterLongPress(onDragStart = { distance = 0f; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragEnd = { vm.reorder(playlistId, latest.map { it.id }) },
                                    onDragCancel = { vm.reorder(playlistId, latest.map { it.id }) },
                                    onDrag = { change, drag ->
                                        change.consume(); distance += drag.y
                                        if (kotlin.math.abs(distance) >= rowHeight) {
                                            val from = latest.indexOfFirst { it.id == track.id }
                                            val to = (from + if (distance > 0) 1 else -1).coerceIn(0, latest.lastIndex)
                                            local = moved(latest, from, to); distance = 0f
                                        }
                                    })
                            }, tint = Muted)
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Monter") }, onClick = {
                                val i = local.indexOf(track); local = moved(local, i, (i - 1).coerceAtLeast(0)); vm.reorder(playlistId, local.map { it.id }); menu = false
                            })
                            DropdownMenuItem(text = { Text("Descendre") }, onClick = {
                                val i = local.indexOf(track); local = moved(local, i, (i + 1).coerceAtMost(local.lastIndex)); vm.reorder(playlistId, local.map { it.id }); menu = false
                            })
                            DropdownMenuItem(text = { Text("Retirer de la playlist") }, onClick = { vm.remove(playlistId, track); menu = false })
                        }
                    }
                }
            } else MusicRow(vm, track, local, state, add)
        }
    }
}
@Composable fun PlaylistsScreen(vm: LibraryViewModel, data: LibraryIndex, open: (Long) -> Unit) {
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    LazyColumn {
        item { PageTitle("Composées par vous", "Playlists") { ActionIcon(Icons.Rounded.Add, "Créer une playlist", onClick = { creating = true }) } }
        item {
            Glass(Modifier.padding(horizontal = 20.dp)) {
                Text("SÉLECTIONS VIVANTES", letterSpacing = 2.sp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Évoluent avec vos écoutes", color = Muted, modifier = Modifier.padding(vertical = 12.dp))
                listOf("Vos favoris" to data.favorites, "Dernières écoutes" to data.history, "Nouveautés" to data.recent).forEach { (title, tracks) ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = tracks.isNotEmpty()) { vm.audio.play(tracks, tracks.first()) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  $title", Modifier.weight(1f)); Text("${tracks.size}", color = Muted)
                        Icon(Icons.Rounded.PlayArrow, "Lire la sélection")
                    }
                }
            }
        }
        item { SectionTitle("Mes playlists", "${lists.size}") }
        if (lists.isEmpty()) item { EmptyState("Un nom, une ambiance", "Créez votre première sélection. Tout reste sur cet appareil.", "Créer une playlist", { creating = true }) }
        items(lists, key = { it.id }) { list ->
            val ids = entries.filter { it.playlistId == list.id }
            val cover = data.tracks.firstOrNull { it.id == ids.firstOrNull()?.trackId }
            Row(Modifier.fillMaxWidth().clickable { open(list.id) }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(cover, Modifier.size(64.dp))
                Column(Modifier.weight(1f).padding(start = 16.dp)) { Text(list.name); Text("${ids.size} titres", color = Muted, style = MaterialTheme.typography.bodySmall) }
                ActionIcon(Icons.Rounded.DeleteOutline, "Supprimer la playlist", onClick = { deleting = list })
            }
        }
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("Nouvelle playlist") },
        text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nom de la playlist") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { vm.createPlaylist(name); name = ""; creating = false }, enabled = name.isNotBlank()) { Text("Créer") } },
        dismissButton = { TextButton(onClick = { creating = false }) { Text("Annuler") } })
    deleting?.let { list -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Supprimer « ${list.name} » ?") },
        text = { Text("Les fichiers audio ne seront pas supprimés.") },
        confirmButton = { TextButton(onClick = { vm.deletePlaylist(list); deleting = null }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } }) }
}
@Composable fun SearchScreen(vm: LibraryViewModel, state: PlaybackState, add: (Track) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val history by vm.searches.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    LazyColumn {
        item { PageTitle("Trouvez votre fréquence", "Recherche") }
        item {
            OutlinedTextField(query, { vm.query.value = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                placeholder = { Text("Titre, artiste, album, genre…") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = {
                    if (query.isNotEmpty()) ActionIcon(Icons.Rounded.Close, "Effacer la recherche", onClick = { vm.query.value = "" })
                }, shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.rememberQuery() }))
            Spacer(Modifier.height(16.dp))
        }
        if (query.isBlank()) {
            item { SectionTitle("Vos dernières recherches") }
            items(history, key = { it.query }) { entry ->
                ListItem(headlineContent = { Text(entry.query) }, leadingContent = { Icon(Icons.Rounded.History, null) },
                    modifier = Modifier.clickable { vm.query.value = entry.query })
            }
            item { SectionTitle("Explorer une ambiance") }
            items(index.genres.keys.take(8)) { genre ->
                TextButton(onClick = { vm.query.value = genre; vm.rememberQuery() }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(genre) }
            }
        } else {
            item { SectionTitle("Résultats", "${results.size} titres") }
            if (results.isEmpty()) item { EmptyState("Aucune résonance", "Essayez un autre nom ou importez de nouveaux morceaux.") }
            items(results, key = { it.id }) { track ->
                TrackRow(track, state.id == track.id, { vm.rememberQuery(); vm.audio.play(results, track) }, { vm.favorite(track) }) {
                    ActionIcon(Icons.Rounded.PlaylistAdd, "Ajouter à une playlist", onClick = { add(track) })
                }
            }
        }
    }
}
