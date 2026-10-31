package app.nacre.audio

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.session.*
import app.nacre.data.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val connected: Boolean = false, val id: String? = null, val playing: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val buffering: Boolean = false,
    val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<String> = emptyList(), val error: String? = null
)
/** One UI connection. ExoPlayer is owned exclusively by the service, never by a screen. */
class PlaybackConnection(context: Context, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    private var controller: MediaController? = null
    private val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { publish() }
        override fun onPlayerError(error: PlaybackException) {
            mutable.value = mutable.value.copy(error = "Lecture impossible : ${error.errorCodeName}. Vérifiez le fichier ou réimportez-le.")
        }
    }
    init {
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                publish()
            } catch (e: Exception) { mutable.value = mutable.value.copy(error = "Connexion audio impossible : ${e.localizedMessage}") }
        }, ContextCompat.getMainExecutor(context))
        scope.launch { while (isActive) { delay(250); if (controller?.isPlaying == true) publish() } }
    }
    private fun publish() {
        val p = controller ?: return
        mutable.value = mutable.value.copy(connected = true, id = p.currentMediaItem?.mediaId,
            playing = p.isPlaying, position = p.currentPosition.coerceAtLeast(0),
            duration = p.duration.takeIf { it > 0 } ?: 0, buffering = p.playbackState == Player.STATE_BUFFERING,
            shuffle = p.shuffleModeEnabled, repeat = p.repeatMode,
            queue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId })
    }
    fun play(tracks: List<Track>, selected: Track) {
        val p = controller ?: run { mutable.value = mutable.value.copy(error = "Le moteur audio se connecte. Réessayez dans un instant."); return }
        val list = tracks.filter { it.available }
        val index = list.indexOfFirst { it.id == selected.id }
        if (index < 0) return
        mutable.value = mutable.value.copy(error = null)
        p.setMediaItems(list.map { it.mediaItem() }, index, 0)
        p.prepare(); p.play()
    }
    fun toggle() { controller?.let { if (it.playWhenReady) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seek(ms: Long) { controller?.seekTo(ms); publish() }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }; publish() }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 }; publish() }
    fun sleep(minutes: Int) { controller?.sendCustomCommand(SessionCommand(PlaybackService.SLEEP, Bundle.EMPTY), Bundle().apply { putInt("minutes", minutes) }) }
    fun queueItem(index: Int) { controller?.seekToDefaultPosition(index); controller?.play() }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    fun close() { controller?.removeListener(listener); MediaController.releaseFuture(future); controller = null }
}
