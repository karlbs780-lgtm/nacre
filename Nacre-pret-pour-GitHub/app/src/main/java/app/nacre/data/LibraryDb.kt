package app.nacre.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks", indices = [Index("uri"), Index("artist"), Index("album"), Index("added")])
data class Track(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val artist: String = "Artiste inconnu",
    val album: String = "Sans album",
    val genre: String = "Non classé",
    val duration: Long = 0,
    val folder: String = "Importations",
    val source: String = "import",
    val size: Long = 0,
    val modified: Long = 0,
    val added: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val available: Boolean = true,
    val lyrics: String = ""
)
@Entity(tableName = "playlists")
data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId")])
data class PlaylistTrack(val playlistId: Long, val trackId: String, val position: Int)
@Entity(tableName = "searches")
data class SearchEntry(@PrimaryKey val query: String, val time: Long = System.currentTimeMillis())

@Dao
interface LibraryDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE") fun observeTracks(): Flow<List<Track>>
    @Query("SELECT * FROM tracks") suspend fun all(): List<Track>
    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun track(id: String): Track?
    @Upsert suspend fun put(track: Track)
    @Transaction suspend fun merge(candidate: Track) {
        val old = track(candidate.id)
        val chosen = if (old?.source == "import" && candidate.source == "media") old.copy(available = true) else candidate
        put(chosen.copy(added = old?.added ?: candidate.added, favorite = old?.favorite ?: false,
            playCount = old?.playCount ?: 0, lastPlayed = old?.lastPlayed ?: 0, lyrics = old?.lyrics ?: ""))
    }
    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1") suspend fun byUri(uri: String): Track?
    @Query("UPDATE OR IGNORE playlist_tracks SET trackId = :newId WHERE trackId = :oldId") suspend fun transferEntries(oldId: String, newId: String)
    @Query("DELETE FROM tracks WHERE id = :id") suspend fun deleteReference(id: String)
    @Transaction suspend fun replaceChanged(oldId: String, candidate: Track) {
        if (oldId == candidate.id) { merge(candidate); return }
        val old = track(oldId)
        merge(candidate)
        if (old != null) {
            val target = track(candidate.id)!!
            put(target.copy(favorite = target.favorite || old.favorite,
                playCount = target.playCount + old.playCount, lastPlayed = maxOf(target.lastPlayed, old.lastPlayed),
                added = minOf(target.added, old.added), lyrics = target.lyrics.ifBlank { old.lyrics }))
            transferEntries(oldId, candidate.id)
            deleteReference(oldId) // Database reference only, NEVER the audio file.
        }
    }
    @Query("UPDATE tracks SET lyrics = :text WHERE id = :id") suspend fun setLyrics(id: String, text: String)
    @Query("UPDATE tracks SET favorite = NOT favorite WHERE id = :id") suspend fun favorite(id: String)
    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayed = :time WHERE id = :id") suspend fun played(id: String, time: Long)
    @Query("UPDATE tracks SET available = 0 WHERE id = :id") suspend fun missing(id: String)
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE") fun playlists(): Flow<List<Playlist>>
    @Insert suspend fun createPlaylist(playlist: Playlist): Long
    @Delete suspend fun deletePlaylist(playlist: Playlist)
    @Query("SELECT * FROM playlist_tracks ORDER BY position") fun entries(): Flow<List<PlaylistTrack>>
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position") suspend fun entriesFor(id: Long): List<PlaylistTrack>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(entry: PlaylistTrack)
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlist AND trackId = :track") suspend fun remove(playlist: Long, track: String)
    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlist AND trackId = :track") suspend fun position(playlist: Long, track: String, position: Int)
    @Transaction suspend fun reorder(playlist: Long, ids: List<String>) {
        ids.forEachIndexed { i, id -> position(playlist, id, i) }
    }
    @Transaction suspend fun append(playlist: Long, track: String) {
        add(PlaylistTrack(playlist, track, (entriesFor(playlist).maxOfOrNull { it.position } ?: -1) + 1))
    }
    @Query("SELECT * FROM searches ORDER BY time DESC LIMIT 8") fun searches(): Flow<List<SearchEntry>>
    @Upsert suspend fun search(entry: SearchEntry)
    @Query("DELETE FROM searches WHERE query NOT IN (SELECT query FROM searches ORDER BY time DESC LIMIT 8)") suspend fun trimSearches()
}

@Database(entities = [Track::class, Playlist::class, PlaylistTrack::class, SearchEntry::class], version = 1, exportSchema = false)
abstract class LibraryDb : RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
        fun open(context: Context): LibraryDb = Room.databaseBuilder(context, LibraryDb::class.java, "nacre.db").build()
    }
}
