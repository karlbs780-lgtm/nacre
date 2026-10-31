package app.nacre.audio

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Amplitude RMS of actual decoded PCM, not a random/equalizer animation. No microphone permission. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AudioMeter : TeeAudioProcessor.AudioBufferSink {
    private val mutable = MutableStateFlow(List(24) { 0f })
    val levels = mutable.asStateFlow()
    private var encoding = C.ENCODING_PCM_16BIT
    private var lastUpdate = 0L
    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
        mutable.value = List(24) { 0f }
    }
    override fun handleBuffer(buffer: ByteBuffer) {
        val now = System.nanoTime()
        if (now - lastUpdate < 40_000_000L) return // at most 25 updates/s, interpolated by Compose
        lastUpdate = now
        if (encoding != C.ENCODING_PCM_16BIT) return
        val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0; var count = 0
        while (data.remaining() >= 2) {
            val value = data.short / 32768.0
            sum += value * value; count++
        }
        val level = if (count > 0) (sqrt(sum / count) * 3).coerceIn(0.0, 1.0).toFloat() else 0f
        mutable.value = mutable.value.drop(1) + level
    }
}
