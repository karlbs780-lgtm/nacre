package app.nacre.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.nacre.data.Settings
import app.nacre.data.Track
import app.nacre.domain.timeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

val Ink = Color(0xFF090B13)
val Muted = Color(0xFF989DB3)
fun accent(settings: Settings) = when (settings.palette) {
    "mint" -> Color(0xFF8CDED0)
    "peach" -> Color(0xFFFFBEA3)
    else -> Color(0xFFC1B3FF)
}
@Composable fun NacreTheme(settings: Settings, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = accent(settings), onPrimary = Ink,
        background = Ink, surface = Color(0xFF141825), surfaceVariant = Color(0xFF1D2235),
        onBackground = Color(0xFFF4F2FA), onSurface = Color(0xFFF4F2FA), outline = Color(0xFF363B50))) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink, contentColor = MaterialTheme.colorScheme.onBackground) { content() }
    }
}
@Composable fun Glass(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xEE252A40), Color(0xDD131724))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .025f))), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(20.dp), content = content)
}
@Composable fun ActionIcon(icon: ImageVector, label: String, tint: Color = LocalContentColor.current, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label, tint = tint) }
}
@Composable fun PageTitle(eyebrow: String, title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        action?.invoke()
    }
}
@Composable fun EmptyState(title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, color = Muted, style = MaterialTheme.typography.bodyMedium)
        if (action != null) FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

private object ArtCache {
    val slots = Semaphore(3)
    val bitmaps = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
}
@Composable fun Cover(track: Track?, modifier: Modifier = Modifier, round: Boolean = false) {
    val context = LocalContext.current
    val key = track?.let { "${it.id}:${it.modified}" }
    var bitmap by remember(key) { mutableStateOf(key?.let { ArtCache.bitmaps.get(it) }) }
    LaunchedEffect(key) {
        if (track != null && bitmap == null && key != null) {
            bitmap = withContext(Dispatchers.IO) { ArtCache.slots.withPermit {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, Uri.parse(track.uri))
                    val bytes = r.embeddedPicture ?: return@withPermit null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 640) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?.also { ArtCache.bitmaps.put(key, it) }
                } catch (_: Exception) { null } finally { r.release() }
            } }
        }
    }
    val palette = listOf(Color(0xFF7C739B), Color(0xFF416C77), Color(0xFFB17F84), Color(0xFF667FAB))
    val color = palette[((track?.id?.hashCode() ?: 0) and Int.MAX_VALUE) % palette.size]
    Box(modifier.clip(RoundedCornerShape(if (round) 1000.dp else 22.dp))
        .background(Brush.linearGradient(listOf(color.copy(alpha = .9f), Color(0xFF141827)))), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Pochette de ${track?.album}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            Canvas(Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width * .66f, size.height * .36f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .30f), Color.Transparent), center, size.width * .7f), size.width * .7f, center)
                for (i in 1..5) drawCircle(Color.White.copy(alpha = .10f), size.minDimension * (.12f + i * .055f), center, style = androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension * .009f))
            }
            Text(track?.album?.take(1)?.uppercase() ?: "N", fontSize = 28.sp, fontWeight = FontWeight.Light, color = Color.White.copy(alpha = .8f))
        }
    }
}
@Composable fun TrackRow(track: Track, current: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(enabled = track.available, onClick = onPlay).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Cover(track, Modifier.size(52.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                color = if (current) MaterialTheme.colorScheme.primary else if (track.available) LocalContentColor.current else Muted)
            Text(if (track.available) "${track.artist} · ${timeLabel(track.duration)}" else "Fichier indisponible · réimporter", maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        ActionIcon(if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (track.favorite) "Retirer des favoris" else "Ajouter aux favoris", if (track.favorite) MaterialTheme.colorScheme.primary else Muted, onFavorite)
        trailing()
    }
}
