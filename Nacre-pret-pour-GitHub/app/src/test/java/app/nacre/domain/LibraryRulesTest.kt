package app.nacre.domain

import app.nacre.data.Track
import org.junit.Assert.*
import org.junit.Test

class LibraryRulesTest {
    private val track = Track("id", "content://audio/1", "Été électrique", "Björk", "Lumière", "Ambient", 125000)
    @Test fun searchIgnoresAccentsAndCase() { assertTrue(matches(track, "ete ELECTRIQUE")) }
    @Test fun searchAcceptsArtist() { assertTrue(matches(track, "bjork")) }
    @Test fun searchAcceptsAlbumAndGenre() { assertTrue(matches(track, "lumiere ambient")) }
    @Test fun searchRequiresEveryWord() { assertFalse(matches(track, "ete jazz")) }
    @Test fun whitespaceQueryMatchesAll() { assertTrue(matches(track, "  ")) }
    @Test fun excludesNestedDirectory() { assertTrue(excluded("Music/Privé/2026", setOf("Music/Privé"))) }
    @Test fun doesNotExcludeSimilarPrefix() { assertFalse(excluded("Music/Privé2", setOf("Music/Privé"))) }
    @Test fun exclusionsIgnoreCaseAndSlashes() { assertTrue(excluded("/Music/TEST/", setOf("music/test/"))) }
    @Test fun emptyExclusionDoesNotExcludeEverything() { assertFalse(excluded("Music", setOf("", " "))) }
    @Test fun canExcludeSpecificFile() { assertTrue(excluded("Music/test.mp3", setOf("Music/test.mp3"))) }
    @Test fun formatsDuration() { assertEquals("2:05", timeLabel(125000)); assertEquals("0:00", timeLabel(-100)) }
    @Test fun reorderMovesForward() { assertEquals(listOf("b", "c", "a"), moved(listOf("a", "b", "c"), 0, 2)) }
    @Test fun reorderMovesBackward() { assertEquals(listOf("c", "a", "b"), moved(listOf("a", "b", "c"), 2, 0)) }
    @Test fun invalidReorderIsNoOp() { assertEquals(listOf("a"), moved(listOf("a"), -1, 2)) }
}
