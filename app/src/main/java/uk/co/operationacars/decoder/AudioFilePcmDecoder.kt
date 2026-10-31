package uk.co.operationacars.decoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.media.AudioFormat
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Offline audio-file decoder. Output is mono PCM16 resampled to 48 kHz. */
object AudioFilePcmDecoder {
    data class Result(val sampleRate: Int, val channels: Int, val samples: ShortArray)

    fun decodeToMono48k(context: Context, uri: Uri): Result {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = candidate
                    break
                }
            }
            require(track >= 0 && format != null) { "No audio track found" }
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val mono = ArrayList<Float>()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var loops = 0
            while (!outputDone && loops++ < 200000) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val input = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        rate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                            val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else if (encoding == AudioFormat.ENCODING_PCM_8BIT) 1 else 2
                            val frame = bytes * channels
                            while (buffer.remaining() >= frame) {
                                var sum = 0.0
                                repeat(channels) {
                                    sum += when (encoding) {
                                        AudioFormat.ENCODING_PCM_FLOAT -> buffer.float.toDouble()
                                        AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 255) - 128) / 128.0
                                        else -> buffer.short.toDouble() / 32768.0
                                    }
                                }
                                mono.add((sum / channels).toFloat().coerceIn(-1f, 1f))
                            }
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
            check(outputDone && mono.isNotEmpty()) { "Audio decode failed or timed out" }
            val count = (mono.size.toLong() * 48000L / rate).toInt().coerceAtLeast(1)
            val pcm = ShortArray(count)
            for (i in pcm.indices) {
                val pos = i.toDouble() * rate / 48000.0
                val left = pos.toInt().coerceIn(0, mono.lastIndex)
                val right = (left + 1).coerceAtMost(mono.lastIndex)
                val fraction = (pos - left).coerceIn(0.0, 1.0)
                pcm[i] = ((mono[left] * (1.0 - fraction) + mono[right] * fraction) * 32767.0)
                    .roundToInt().coerceIn(-32768, 32767).toShort()
            }
            return Result(rate, channels, pcm)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }
}
