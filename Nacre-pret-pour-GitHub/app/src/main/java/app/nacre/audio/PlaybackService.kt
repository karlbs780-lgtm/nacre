package app.nacre.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.*
import androidx.media3.session.*
import app.nacre.MainActivity
import app.nacre.NacreApp
import app.nacre.data.Track
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun Track.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
        .setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var timer: Job? = null
    private var counted = false
    private val app get() = application as NacreApp
    companion object { const val SLEEP = "app.nacre.SLEEP" }
    override fun onCreate() {
        super.onCreate()
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(AudioMeter))).build()
        }
        player = ExoPlayer.Builder(this, renderers).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { counted = false; saveQueue() }
                override fun onEvents(player: Player, events: Player.Events) {
                    if (player.isPlaying && !counted) {
                        counted = true
                        player.currentMediaItem?.mediaId?.let { id -> scope.launch(Dispatchers.IO) {
                            app.database.library().played(id, System.currentTimeMillis())
                        } }
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { saveQueue() }
                override fun onPlayerError(error: PlaybackException) { saveQueue() }
            })
        }
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(pending).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if (controller.packageName != packageName && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(SLEEP, Bundle.EMPTY)).build()).build()
            }
            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (customCommand.customAction != SLEEP) return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                timer?.cancel()
                val minutes = args.getInt("minutes").coerceIn(0, 180)
                val deadline = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
                app.preferences.storage.edit().putLong("sleepDeadline", deadline).apply()
                if (minutes > 0) timer = scope.launch { delay(minutes * 60_000L); player.pause()
                    app.preferences.storage.edit().putLong("sleepDeadline", 0).apply() }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).build()
        app.preferences.storage.edit().putLong("sleepDeadline", 0).apply()
        scope.launch {
            val prefs = app.preferences.storage
            val ids = prefs.getString("queue", "")!!.split('|').filter { it.isNotEmpty() }
            val tracks = withContext(Dispatchers.IO) { app.database.library().all().filter { it.available }.associateBy { it.id } }
            val items = ids.mapNotNull { tracks[it]?.mediaItem() }
            if (player.mediaItemCount == 0 && items.isNotEmpty()) {
                player.setMediaItems(items, prefs.getInt("queueIndex", 0).coerceIn(items.indices), prefs.getLong("position", 0).coerceAtLeast(0))
                player.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF)
                player.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
                player.prepare() // restore without autoplay
            }
            while (isActive) { delay(5000); if (player.isPlaying) saveQueue() }
        }
    }
    private fun saveQueue() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).joinToString("|") { player.getMediaItemAt(it).mediaId }
        app.preferences.storage.edit().putString("queue", ids).putInt("queueIndex", player.currentMediaItemIndex)
            .putLong("position", player.currentPosition).putInt("repeat", player.repeatMode)
            .putBoolean("shuffle", player.shuffleModeEnabled).apply()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }
    override fun onDestroy() {
        saveQueue(); scope.cancel(); session?.release(); player.release(); session = null
        super.onDestroy()
    }
}
