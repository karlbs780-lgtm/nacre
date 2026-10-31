@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.nacre.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.nacre.LibraryViewModel
import app.nacre.audio.AudioMeter
import app.nacre.audio.PlaybackState
import app.nacre.data.Settings
import app.nacre.data.Track
import app.nacre.domain.timeLabel
import kotlinx.coroutines.delay

@Composable fun PlayerScreen(vm: LibraryViewModel, track: Track, state: PlaybackState, settings: Settings,
    artModifier: Modifier, onClose: () -> Unit, onAdd: () -> Unit, onLyrics: () -> Unit) {
    var sheet by rememberSaveable { mutableStateOf("") }
    val data by vm.index.collectAsStateWithLifecycle()
    val levels by AudioMeter.levels.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var dragPosition by remember(track.id) { mutableStateOf<Float?>(null) }
    val haptic = LocalHapticFeedback.current
    var coverOffset by remember(track.id) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(if (settings.motion) coverOffset else 0f, tween(120), label = "cover-parallax")
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    var sleepDeadline by remember { mutableLongStateOf(vm.prefs.storage.getLong("sleepDeadline", 0)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            sleepDeadline = vm.prefs.storage.getLong("sleepDeadline", 0)
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = .18f), Ink, Ink)))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(Icons.Rounded.KeyboardArrowDown, "Réduire le lecteur", onClick = onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DANS VOTRE BULLE", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = accent)
                    Text(track.album, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
                }
                ActionIcon(Icons.Rounded.PlaylistAdd, "Ajouter à une playlist", onClick = onAdd)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f).pointerInput(track.id) {
                var distance = 0f
                detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                    coverOffset = 0f
                    if (distance < -swipeThreshold) vm.audio.next() else if (distance > swipeThreshold) vm.audio.previous()
                }, onDragCancel = { coverOffset = 0f }, onHorizontalDrag = { change, amount -> change.consume(); distance += amount; coverOffset = distance.coerceIn(-160f, 160f) * .18f })
            }) {
                Cover(track, artModifier.fillMaxSize().graphicsLayer { translationX = animatedOffset; rotationZ = animatedOffset * .06f }, round = settings.playerStyle == "vinyl")
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(track.artist, style = MaterialTheme.typography.titleMedium, color = Muted, modifier = Modifier.padding(top = 6.dp))
                }
                ActionIcon(if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favori", accent, { vm.favorite(track) })
            }
            if (settings.visualizer) {
                Spacer(Modifier.height(18.dp))
                // Display a chronological amplitude envelope; this is not a frequency spectrum.
                PcmWaveform(levels, state.playing && settings.motion, accent, Modifier.widthIn(max = 520.dp).fillMaxWidth().height(40.dp))
            }
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                val duration = state.duration.takeIf { it > 0 } ?: track.duration
                Slider(value = dragPosition ?: state.position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                    onValueChange = { dragPosition = it }, onValueChangeFinished = { dragPosition?.let { vm.audio.seek(it.toLong()) }; dragPosition = null },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = duration > 0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(timeLabel(dragPosition?.toLong() ?: state.position), style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(timeLabel(duration), style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
            Row(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(Icons.Rounded.Shuffle, "Lecture aléatoire", if (state.shuffle) accent else Muted, vm.audio::shuffle)
                ActionIcon(Icons.Rounded.SkipPrevious, "Morceau précédent", onClick = vm.audio::previous)
                FilledIconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.audio.toggle() },
                    modifier = Modifier.size(76.dp), shape = CircleShape) {
                    if (state.buffering) CircularProgressIndicator(Modifier.size(26.dp), color = Ink, strokeWidth = 2.dp)
                    else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.playing) "Pause" else "Lire", Modifier.size(38.dp))
                }
                ActionIcon(Icons.Rounded.SkipNext, "Morceau suivant", onClick = vm.audio::next)
                ActionIcon(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Répétition : ${when(state.repeat) { Player.REPEAT_MODE_ONE -> "un titre"; Player.REPEAT_MODE_ALL -> "tous"; else -> "désactivée" }}",
                    if (state.repeat != Player.REPEAT_MODE_OFF) accent else Muted, vm.audio::repeat)
            }
            Row(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeDown, "Volume", tint = Muted)
                Slider(volume, { volume = it; manager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) },
                    Modifier.weight(1f), valueRange = 0f..maxVolume.toFloat())
                Icon(Icons.Rounded.VolumeUp, null, tint = Muted)
            }
            Row(Modifier.widthIn(max = 440.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { sheet = "lyrics" }) { Icon(Icons.Rounded.Lyrics, null); Text(" Paroles") }
                TextButton(onClick = { sheet = "sleep" }) { Icon(Icons.Rounded.Bedtime, null)
                    Text(if (sleepDeadline > System.currentTimeMillis()) " ${((sleepDeadline - System.currentTimeMillis()) / 60000) + 1} min" else " Minuteur") }
                IconButton(onClick = { sheet = "queue" }) { Icon(Icons.Rounded.QueueMusic, "File d’attente") }
            }
            Text("AUDIO LOCAL · ${if (state.connected) "CONNECTÉ" else "CONNEXION…"}", letterSpacing = 2.sp, color = Muted, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
        }
    }
    if (sheet.isNotEmpty()) ModalBottomSheet(onDismissRequest = { sheet = "" }, containerColor = Color(0xFF151A29)) {
        when (sheet) {
            "lyrics" -> Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text("Les mots derrière le son", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(20.dp))
                Text(track.lyrics.ifBlank { "Aucune parole locale. Importez un fichier texte ou LRC associé à ce morceau. Aucune recherche en ligne n’est effectuée." },
                    style = MaterialTheme.typography.titleMedium, lineHeight = 30.sp)
                TextButton(onClick = { sheet = ""; onLyrics() }) { Text("Importer des paroles (.txt / .lrc)") }
            }
            "sleep" -> Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Laissez la nuit venir", style = MaterialTheme.typography.headlineSmall)
                Text("La lecture se mettra en pause, même écran verrouillé.", color = Muted, modifier = Modifier.padding(vertical = 12.dp))
                listOf(0, 5, 15, 30, 45, 60, 90).forEach { minutes ->
                    TextButton(onClick = { vm.audio.sleep(minutes); sheet = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (minutes == 0) "Désactiver le minuteur" else "$minutes minutes")
                    }
                }
            }
            "queue" -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 550.dp)) {
                item { Text("À suivre", Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall) }
                itemsIndexed(state.queue, key = { index, id -> "$index:$id" }) { i, id ->
                    val item = data.tracks.firstOrNull { it.id == id }
                    if (item != null) TrackRow(item, state.id == id, { vm.audio.queueItem(i) }, { vm.favorite(item) })
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
@Composable private fun PcmWaveform(levels: List<Float>, playing: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val step = size.width / levels.size.coerceAtLeast(1)
        levels.forEachIndexed { i, level ->
            val height = if (playing) (level.coerceIn(0f, 1f) * size.height).coerceAtLeast(3.dp.toPx()) else 3.dp.toPx()
            drawLine(color.copy(alpha = .35f + .65f * i / levels.size),
                androidx.compose.ui.geometry.Offset(step * (i + .5f), (size.height - height) / 2),
                androidx.compose.ui.geometry.Offset(step * (i + .5f), (size.height + height) / 2),
                strokeWidth = (step * .38f).coerceAtLeast(2f), cap = StrokeCap.Round)
        }
    }
}
