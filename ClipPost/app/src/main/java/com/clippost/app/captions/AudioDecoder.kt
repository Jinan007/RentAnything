package com.clippost.app.captions

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * Reads the sound of a video and turns it into what the speech recognizer wants:
 * 16 kHz, mono, 16-bit samples. It works in small pieces so a long video never fills the memory.
 */
object AudioDecoder {

    private const val TARGET_RATE = 16_000

    /**
     * @return false if the video has no audio track.
     */
    suspend fun decode(
        context: Context,
        uri: Uri,
        onChunk: (ShortArray, Int) -> Unit,
        onProgress: suspend (Int) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext false

            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext false
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var resampler: Resampler? = null
            var lastPercent = -1

            while (!outputDone) {
                currentCoroutineContext().ensureActive() // lets the user cancel

                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    resampler = resamplerFor(decoder.outputFormat)
                } else if (outIndex >= 0) {
                    if (info.size > 0) {
                        val rs = resampler ?: resamplerFor(decoder.outputFormat).also { resampler = it }
                        val buffer = decoder.getOutputBuffer(outIndex)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val samples = ShortArray(info.size / 2)
                        buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

                        val converted = rs.process(samples)
                        if (converted.isNotEmpty()) onChunk(converted, converted.size)

                        if (durationUs > 0) {
                            val percent = ((info.presentationTimeUs * 100) / durationUs).toInt().coerceIn(0, 100)
                            if (percent != lastPercent && percent % 5 == 0) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            true
        } finally {
            codec?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    private fun resamplerFor(format: MediaFormat) = Resampler(
        srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    )

    /** Mixes all channels into one and converts the sample rate to 16 kHz (simple linear interpolation). */
    private class Resampler(srcRate: Int, private val channels: Int) {
        private val step = srcRate.toDouble() / TARGET_RATE
        private var pos = 0.0        // where the next output sample sits, in input samples
        private var prev = 0f        // the last input sample of the previous piece
        private var started = false

        fun process(pcm: ShortArray): ShortArray {
            val frames = pcm.size / channels
            if (frames == 0) return ShortArray(0)

            val mono = FloatArray(frames) { f ->
                var sum = 0f
                for (c in 0 until channels) sum += pcm[f * channels + c]
                sum / channels
            }
            if (!started) {
                prev = mono[0]
                started = true
            }

            val out = ShortArray((frames / step).toInt() + 4)
            var n = 0
            while (pos < frames - 1) {
                val i = floor(pos).toInt()
                val frac = (pos - i).toFloat()
                val a = if (i < 0) prev else mono[i]
                val b = mono[i + 1]
                out[n++] = (a + (b - a) * frac).toInt().coerceIn(-32768, 32767).toShort()
                pos += step
            }
            pos -= frames
            prev = mono[frames - 1]
            return out.copyOf(n)
        }
    }
}
