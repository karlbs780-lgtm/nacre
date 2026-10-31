package app.nacre

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nacre.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: LibraryDb
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LibraryDb::class.java).build() }
    @After fun close() { db.close() }
    @Test fun duplicateHashProducesOneRow() = runBlocking {
        val dao = db.library()
        dao.put(Track("same-sha", "content://a", "Titre"))
        dao.put(Track("same-sha", "content://b", "Copie"))
        assertEquals(1, dao.all().size)
    }
    @Test fun playlistReorderAndCascadeDoNotDeleteAudio() = runBlocking {
        val dao = db.library()
        dao.put(Track("a", "content://a", "A")); dao.put(Track("b", "content://b", "B"))
        val id = dao.createPlaylist(Playlist(name = "Test"))
        dao.append(id, "a"); dao.append(id, "b"); dao.append(id, "a")
        assertEquals(2, dao.entriesFor(id).size)
        dao.reorder(id, listOf("b", "a"))
        assertEquals(listOf("b", "a"), dao.entriesFor(id).map { it.trackId })
        dao.deletePlaylist(Playlist(id, "Test"))
        assertTrue(dao.entriesFor(id).isEmpty()); assertEquals(2, dao.all().size)
    }
    @Test fun changedHashKeepsPlaylistAndFavorite() = runBlocking {
        val dao = db.library()
        dao.put(Track("old", "content://a", "Avant", favorite = true, playCount = 2))
        val list = dao.createPlaylist(Playlist(name = "Test")); dao.append(list, "old")
        dao.replaceChanged("old", Track("new", "content://a", "Après"))
        assertNull(dao.track("old")); assertTrue(dao.track("new")!!.favorite)
        assertEquals(2, dao.track("new")!!.playCount)
        assertEquals(listOf("new"), dao.entriesFor(list).map { it.trackId })
    }
    @Test fun metadataMergeKeepsUserState() = runBlocking {
        val dao = db.library()
        dao.put(Track("a", "content://a", "Avant", source = "media", favorite = true, playCount = 7, lastPlayed = 1234, lyrics = "Paroles"))
        dao.merge(Track("a", "content://b", "Après", source = "media"))
        val result = dao.track("a")!!
        assertEquals("Après", result.title); assertTrue(result.favorite)
        assertEquals(7, result.playCount); assertEquals(1234L, result.lastPlayed)
        assertEquals("Paroles", result.lyrics)
    }
    @Test fun favoritesAndHistoryPersist() = runBlocking {
        val dao = db.library(); dao.put(Track("a", "content://a", "A"))
        dao.favorite("a"); dao.played("a", 1234)
        val track = dao.track("a")!!
        assertTrue(track.favorite); assertEquals(1, track.playCount); assertEquals(1234L, track.lastPlayed)
    }
}
