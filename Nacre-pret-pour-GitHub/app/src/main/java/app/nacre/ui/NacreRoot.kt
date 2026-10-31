@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.nacre.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nacre.LibraryViewModel
import app.nacre.audio.PlaybackState
import app.nacre.data.Track
import kotlinx.coroutines.delay

@Composable fun NacreRoot(vm: LibraryViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val playback by vm.audio.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf("home") }
    var detailKind by rememberSaveable { mutableStateOf("albums") }
    var detailKey by rememberSaveable { mutableStateOf("") }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var splash by rememberSaveable { mutableStateOf(true) }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var lyricsId by rememberSaveable { mutableStateOf<String?>(null) }
    val current = index.tracks.firstOrNull { it.id == playback.id }
    val snackbar = remember { SnackbarHostState() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.imports(it) }
    val lyrics = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val track = index.tracks.firstOrNull { it.id == lyricsId }
        if (uri != null && track != null) vm.lyrics(track, uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.resumed() else vm.message.value = "Accès refusé. L’import manuel reste disponible ; vous pouvez aussi autoriser l’audio dans les réglages Android."
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.resumed() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { delay(700); splash = false }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(playback.error) { playback.error?.let { snackbar.showSnackbar(it); vm.audio.clearError() } }
    fun openDetail(kind: String, key: String) { detailKind = kind; detailKey = key; page = "detail" }
    BackHandler(playerOpen || page !in listOf("home", "search", "library", "playlists")) {
        if (playerOpen) playerOpen = false else page = if (page == "detail") detailKind.takeIf { it != "playlist" } ?: "playlists" else "library"
    }
    NacreTheme(settings) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            SharedTransitionLayout {
                AnimatedContent(targetState = playerOpen && current != null,
                    transitionSpec = { fadeIn(tween(if (settings.motion) 300 else 0)) togetherWith fadeOut(tween(if (settings.motion) 180 else 0)) }, label = "player-transition") { expanded ->
                    val artModifier = if (settings.motion) Modifier.sharedElement(
                        rememberSharedContentState(key = "cover:${playback.id}"), animatedVisibilityScope = this
                    ) else Modifier
                    if (expanded && current != null) {
                        PlayerScreen(vm, current, playback, settings, artModifier, onClose = { playerOpen = false },
                            onAdd = { addTrack = current }, onLyrics = { lyricsId = current.id; lyrics.launch(arrayOf("text/*", "application/octet-stream")) })
                    } else {
                        Scaffold(containerColor = Color.Transparent, bottomBar = {
                            Column {
                                if (current != null) MiniPlayer(current, playback, artModifier,
                                    onOpen = { playerOpen = true }, onToggle = vm.audio::toggle, onNext = vm.audio::next)
                                NavigationBar(containerColor = Color(0xFA0E111C), tonalElevation = 0.dp) {
                                    val destinations = listOf(Triple("home", "Accueil", Icons.Rounded.Home), Triple("search", "Recherche", Icons.Rounded.Search),
                                        Triple("library", "Bibliothèque", Icons.Rounded.LibraryMusic), Triple("playlists", "Playlists", Icons.Rounded.QueueMusic))
                                    destinations.forEach { (route, title, icon) ->
                                        NavigationBarItem(selected = page == route, onClick = { page = route },
                                            icon = { Icon(icon, title) }, label = { Text(title, fontSize = 10.sp, maxLines = 1) },
                                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .15f)))
                                    }
                                }
                            }
                        }) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                                Box(Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
                                    Crossfade(targetState = page, animationSpec = tween(if (settings.motion) 220 else 0), label = "navigation") { screen ->
                                        when (screen) {
                                            "home" -> HomeScreen(vm, index, playback, { page = it }, ::openDetail, { addTrack = it })
                                            "search" -> SearchScreen(vm, playback, { addTrack = it })
                                            "library" -> LibraryScreen(vm, index, playback, { page = it }, ::openDetail, { addTrack = it })
                                            "artists", "albums", "genres", "folders" -> GroupsScreen(screen, index, { page = "library" }) { openDetail(screen, it) }
                                            "detail" -> CollectionScreen(vm, index, detailKind, detailKey, playback,
                                                { page = if (detailKind == "playlist") "playlists" else detailKind }, { addTrack = it })
                                            "playlists" -> PlaylistsScreen(vm, index) { openDetail("playlist", it.toString()) }
                                            "settings" -> SettingsScreen(vm, settings, { page = "home" }, { page = "scan" })
                                            "scan" -> ScanScreen(vm, { page = "home" },
                                                { importer.launch(arrayOf("audio/*", "application/ogg")) },
                                                { if (vm.canScan()) vm.scan() else permission.launch(vm.permission()) },
                                                { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
                                            else -> EmptyState("Écran introuvable", "Revenez à votre bibliothèque.", "Bibliothèque", { page = "library" })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp, start = 16.dp, end = 16.dp))
            if (splash) SplashScreen(settings.motion)
        }
        if (addTrack != null) {
            val lists by vm.playlists.collectAsStateWithLifecycle()
            AlertDialog(onDismissRequest = { addTrack = null }, title = { Text("Ajouter à une playlist") },
                text = {
                    LazyColumn {
                        if (lists.isEmpty()) item { Text("Créez d’abord une playlist dans l’onglet Playlists.") }
                        items(lists, key = { it.id }) { list ->
                            TextButton(onClick = { addTrack?.let { vm.add(list.id, it) }; addTrack = null }, modifier = Modifier.fillMaxWidth()) { Text(list.name) }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { addTrack = null }) { Text("Fermer") } })
        }
    }
}

@Composable private fun MiniPlayer(track: Track, state: PlaybackState, artModifier: Modifier, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xFF30334C), Color(0xFF1B2033)))).clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(track, artModifier.size(46.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artist, maxLines = 1, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (state.buffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else ActionIcon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Lire", onClick = onToggle)
            ActionIcon(Icons.Rounded.SkipNext, "Morceau suivant", onClick = onNext)
        }
        LinearProgressIndicator(progress = { if (state.duration > 0) state.position.toFloat() / state.duration else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
    }
}

@Composable private fun SplashScreen(motion: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, tween(if (motion) 500 else 0), label = "logo")
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { this.alpha = alpha; scaleX = .9f + .1f * alpha; scaleY = scaleX }) {
            Text("nacre.", fontSize = 64.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-3).sp, color = MaterialTheme.colorScheme.primary)
            Text("VOTRE MUSIQUE. LIBRE.", letterSpacing = 3.sp, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}
