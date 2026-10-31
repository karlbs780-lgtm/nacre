package app.nacre

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nacre.audio.PlaybackConnection
import app.nacre.data.*
import app.nacre.domain.matches
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LibraryIndex(
    val ready: Boolean = false, val tracks: List<Track> = emptyList(),
    val artists: Map<String, List<Track>> = emptyMap(),
    val albums: Map<String, List<Track>> = emptyMap(),
    val genres: Map<String, List<Track>> = emptyMap(),
    val folders: Map<String, List<Track>> = emptyMap(),
    val recent: List<Track> = emptyList(), val history: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList()
)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NacreApp
    private val repo = app.library
    private val dao = app.database.library()
    val prefs = app.preferences
    val settings = prefs.state
    val audio = PlaybackConnection(application, viewModelScope)
    val scan = repo.scan
    val message = MutableStateFlow<String?>(null)
    @OptIn(FlowPreview::class)
    val index = dao.observeTracks().debounce(100).conflate().map { tracks ->
        val available = tracks.filter { it.available }
        LibraryIndex(true, tracks, available.groupBy { it.artist }.toSortedMap(),
            available.groupBy { "${it.album} · ${it.artist}" }.toSortedMap(),
            available.groupBy { it.genre }.toSortedMap(), available.groupBy { it.folder }.toSortedMap(),
            available.sortedByDescending { it.added }.take(30),
            available.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(50),
            available.filter { it.favorite })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, LibraryIndex())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val entries = dao.entries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val searches = dao.searches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val query = MutableStateFlow("")
    @OptIn(FlowPreview::class)
    val results = combine(index, query.debounce(120)) { data, text ->
        if (text.isBlank()) emptyList() else data.tracks.filter { it.available && matches(it, text) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private fun work(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message.value = e.localizedMessage ?: "Opération impossible" }
    } }
    fun resumed() { repo.watch(); if (repo.canScan() && !scan.value.running) scan() }
    fun permission() = repo.permission()
    fun canScan() = repo.canScan()
    fun scan() { app.applicationScope.launch { repo.scanDevice() } }
    fun imports(uris: List<Uri>) { if (uris.isNotEmpty()) app.applicationScope.launch { repo.importFiles(uris) } }
    fun favorite(track: Track) = work { dao.favorite(track.id) }
    fun createPlaylist(name: String) = work {
        require(name.trim().isNotEmpty()) { "Choisissez un nom" }
        dao.createPlaylist(Playlist(name = name.trim().take(80)))
    }
    fun deletePlaylist(playlist: Playlist) = work { dao.deletePlaylist(playlist) }
    fun add(playlist: Long, track: Track) = work { dao.append(playlist, track.id); message.value = "Ajouté à la playlist" }
    fun remove(playlist: Long, track: Track) = work { dao.remove(playlist, track.id) }
    fun reorder(playlist: Long, ids: List<String>) = work { dao.reorder(playlist, ids) }
    fun rememberQuery() = work {
        val text = query.value.trim().take(100)
        if (text.isNotEmpty()) { dao.search(SearchEntry(text)); dao.trimSearches() }
    }
    fun lyrics(track: Track, uri: Uri) = work { repo.lyrics(track.id, uri) }
    override fun onCleared() { audio.close(); super.onCleared() }
}
