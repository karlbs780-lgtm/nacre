package app.nacre.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import app.nacre.domain.excluded
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** Never mutates users' audio files. All disk/metadata work is confined to IO. */
data class ScanState(val running: Boolean = false, val done: Int = 0, val total: Int = 0,
    val skipped: Int = 0, val message: String = "Prêt à découvrir votre musique", val error: String? = null)

class LibraryRepository(
    private val context: Context, val dao: LibraryDao, val prefs: Preferences,
    private val scope: CoroutineScope
) {
    private val resolver = context.contentResolver
    private val mutex = Mutex()
    private val mutableScan = MutableStateFlow(ScanState())
    val scan = mutableScan.asStateFlow()
    private var watcherJob: Job? = null
    private var watching = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            watcherJob?.cancel()
            watcherJob = scope.launch { delay(1200); scanDevice() }
        }
    }
    fun permission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun canScan() = ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED
    fun watch() {
        if (!watching && canScan()) {
            resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            watching = true
        }
    }
    private fun digest(uri: Uri): String {
        val hash = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        } ?: error("Fichier illisible")
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    private fun metadata(uri: Uri, fallback: String): Track {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun field(key: Int, default: String) = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: default
            return Track(id = "", uri = uri.toString(),
                title = field(MediaMetadataRetriever.METADATA_KEY_TITLE, fallback.substringBeforeLast('.')),
                artist = field(MediaMetadataRetriever.METADATA_KEY_ARTIST, "Artiste inconnu"),
                album = field(MediaMetadataRetriever.METADATA_KEY_ALBUM, "Sans album"),
                genre = field(MediaMetadataRetriever.METADATA_KEY_GENRE, "Non classé"),
                duration = field(MediaMetadataRetriever.METADATA_KEY_DURATION, "0").toLongOrNull() ?: 0)
        } finally { retriever.release() }
    }
    private suspend fun merge(candidate: Track, previousId: String? = null) {
        if (previousId != null && previousId != candidate.id) dao.replaceChanged(previousId, candidate)
        else dao.merge(candidate)
    }
    suspend fun scanDevice() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!canScan()) {
                mutableScan.value = ScanState(error = "Accès audio non accordé. Autorisez le scan ou importez des fichiers sans permission globale.")
                return@withLock
            }
            mutableScan.value = ScanState(running = true, message = "Lecture de l’index Android…")
            var done = 0; var skipped = 0
            val seen = hashSetOf<String>()
            try {
                val old = dao.all().associateBy { it.uri }
                val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_MODIFIED,
                    if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA)
                val cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns, null, null, null)
                    ?: error("L’index audio Android n’est pas disponible")
                cursor.use { c ->
                    val total = c.count
                    while (c.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                        val name = c.getString(1) ?: "Sans titre"
                        val size = c.getLong(2); val modified = c.getLong(3)
                        val rawPath = c.getString(4) ?: "Musique"
                        val folder = if (Build.VERSION.SDK_INT >= 29) rawPath.trim('/') else rawPath.substringBeforeLast('/')
                        if (excluded(folder, prefs.state.value.exclusions) || excluded("$folder/$name", prefs.state.value.exclusions)) {
                            done++; continue
                        }
                        try {
                            val cached = old[uri.toString()]
                            if (cached != null && cached.size == size && cached.modified == modified && cached.available) {
                                seen.add(cached.id)
                            } else {
                                // Hash only new/changed content; exact byte duplicates share one Track id.
                                val id = digest(uri)
                                val track = metadata(uri, name).copy(id = id, source = "media", folder = folder,
                                    size = size, modified = modified)
                                merge(track, cached?.id); seen.add(id)
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            skipped++
                            // A transient provider error must not invalidate a previously indexed item.
                            old[uri.toString()]?.let { seen.add(it.id) }
                        }
                        done++
                        mutableScan.value = ScanState(true, done, total, skipped, "Indexation · $name")
                    }
                }
                // Only after a successful full query; never clear the library on permission/provider failure.
                dao.all().filter { it.source == "media" && it.id !in seen }.forEach { dao.missing(it.id) }
                mutableScan.value = ScanState(done = done, total = done, skipped = skipped,
                    message = "Scan terminé · $done fichiers examinés" + if (skipped > 0) " · $skipped illisibles" else "")
            } catch (cancelled: CancellationException) {
                mutableScan.value = mutableScan.value.copy(running = false, message = "Scan interrompu")
                throw cancelled
            } catch (e: Exception) {
                mutableScan.value = mutableScan.value.copy(running = false, error = e.localizedMessage ?: "Scan impossible")
            }
        }
    }
    suspend fun importFiles(uris: List<Uri>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutableScan.value = ScanState(true, total = uris.size, message = "Importation…")
            var failed = 0
            uris.forEachIndexed { index, uri ->
                try {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    var name = "Audio"; var size = 0L
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                        if (it.moveToFirst()) { name = it.getString(0) ?: name; size = it.getLong(1) }
                    }
                    val track = metadata(uri, name).copy(id = digest(uri), size = size)
                    require(track.duration > 0) { "Ce fichier ne contient pas d’audio lisible" }
                    merge(track, dao.byUri(uri.toString())?.id)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed++ }
                mutableScan.value = ScanState(true, index + 1, uris.size, failed, "Import · ${index + 1}/${uris.size}")
            }
            mutableScan.value = mutableScan.value.copy(running = false,
                message = "${uris.size - failed} fichiers traités · doublons fusionnés",
                error = if (failed > 0) "$failed import(s) impossible(s) : format, accès permanent ou fichier indisponible. Réessayez depuis le gestionnaire de fichiers local." else null)
        }
    }
    suspend fun lyrics(trackId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(100_000) }
            ?: error("Paroles illisibles")
        dao.setLyrics(trackId, text)
    }
}
