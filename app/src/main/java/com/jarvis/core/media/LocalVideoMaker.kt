package com.jarvis.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.sin

/**
 * Creates a real H.264 MP4 on the phone, no server involved.
 *
 * It renders text cards into a Bitmap, converts them to YUV420 and feeds a
 * hardware/software AVC encoder through MediaCodec, then muxes the result with
 * MediaMuxer. The chat bubble then plays it with Media3.
 *
 * Everything is wrapped so a phone without an AVC encoder returns a failure
 * with a Hindi message instead of crashing Jarvis.
 */
class LocalVideoMaker {

    fun makeVideo(
        title: String,
        lines: List<String>,
        seconds: Int,
        output: File,
        onProgress: (Int) -> Unit = {},
    ): Result<File> {
        val duration = seconds.coerceIn(2, 15)
        val totalFrames = duration * FPS
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var bitmap: Bitmap? = null

        return try {
            output.parentFile?.mkdirs()
            if (output.exists()) output.delete()

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 1_500_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = encoder
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux

            var trackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()
            val pixels = IntArray(WIDTH * HEIGHT)
            val frameBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            bitmap = frameBitmap
            val canvas = Canvas(frameBitmap)

            fun drain(endOfStream: Boolean): Boolean {
                var idle = 0
                while (true) {
                    val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIndex >= 0 -> {
                            val isConfig =
                                bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (!isConfig && bufferInfo.size > 0) {
                                if (trackIndex >= 0 && !muxerStarted) {
                                    mux.start()
                                    muxerStarted = true
                                }
                                if (muxerStarted) {
                                    val buffer = encoder.getOutputBuffer(outIndex)
                                    if (buffer != null) {
                                        buffer.position(bufferInfo.offset)
                                        buffer.limit(bufferInfo.offset + bufferInfo.size)
                                        mux.writeSampleData(trackIndex, buffer, bufferInfo)
                                    }
                                }
                            }
                            encoder.releaseOutputBuffer(outIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                return true
                            }
                        }

                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (trackIndex < 0) trackIndex = mux.addTrack(encoder.outputFormat)
                        }

                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return false
                            // never spin forever waiting for a missing EOS flag
                            idle++
                            if (idle > 400) return false
                        }

                        else -> return false
                    }
                }
            }

            for (frame in 0 until totalFrames) {
                drawFrame(canvas, title, lines, frame, totalFrames)
                frameBitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)

                var queued = false
                var attempts = 0
                while (!queued && attempts < 200) {
                    attempts++
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex < 0) {
                        drain(false)
                        continue
                    }
                    val image = encoder.getInputImage(inIndex)
                    val pts = frame.toLong() * 1_000_000L / FPS
                    if (image == null) {
                        encoder.queueInputBuffer(inIndex, 0, 0, pts, 0)
                    } else {
                        fillYuv420(image, pixels)
                        encoder.queueInputBuffer(inIndex, 0, WIDTH * HEIGHT * 3 / 2, pts, 0)
                    }
                    queued = true
                }
                drain(false)
                if (frame % 5 == 0) onProgress(frame * 100 / totalFrames)
            }

            val eosIndex = encoder.dequeueInputBuffer(20_000)
            if (eosIndex >= 0) {
                encoder.queueInputBuffer(
                    eosIndex,
                    0,
                    0,
                    totalFrames.toLong() * 1_000_000L / FPS,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
            }
            drain(true)

            if (!muxerStarted) {
                runCatching { mux.stop() }
            } else {
                mux.stop()
            }
            if (!output.exists() || output.length() < 512) {
                Result.failure(IllegalStateException("Video file khali bana."))
            } else {
                onProgress(100)
                Result.success(output)
            }
        } catch (t: Throwable) {
            runCatching { output.delete() }
            Result.failure(t)
        } finally {
            runCatching { muxer?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { bitmap?.recycle() }
        }
    }

    // ------------------------------------------------------------------ frames

    private fun drawFrame(canvas: Canvas, title: String, lines: List<String>, frame: Int, total: Int) {
        val progress = frame.toFloat() / total.toFloat()

        val background = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                intArrayOf(0xFF05060A.toInt(), 0xFF141033.toInt(), 0xFF05060A.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        // breathing orb
        val pulse = 1f + 0.06f * sin(progress * 6.28f * 3f)
        val orbGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FF3B30 }
        canvas.drawCircle(WIDTH / 2f, 230f, 86f * pulse, orbGlow)
        val orb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B30.toInt() }
        canvas.drawCircle(WIDTH / 2f, 230f, 44f * pulse, orb)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title.take(28), WIDTH / 2f, 380f, titlePaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD7E3FF.toInt()
            textSize = 21f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val visible = lines.filter { it.isNotBlank() }
        if (visible.isNotEmpty()) {
            val reveal = (progress * 1.5f + 0.35f) * visible.size
            var y = 450f
            for ((index, line) in visible.withIndex()) {
                if (index > reveal) break
                for (row in wrap(line, bodyPaint, (WIDTH - 80).toFloat())) {
                    if (y > HEIGHT - 150f) break
                    canvas.drawText(row, 40f, y, bodyPaint)
                    y += 30f
                }
                y += 8f
            }
        }

        val barBg = Paint().apply { color = 0x33FFFFFF }
        canvas.drawRect(40f, HEIGHT - 92f, (WIDTH - 40).toFloat(), HEIGHT - 86f, barBg)
        val bar = Paint().apply { color = 0xFF00E5FF.toInt() }
        canvas.drawRect(40f, HEIGHT - 92f, 40f + (WIDTH - 80) * progress, HEIGHT - 86f, bar)

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6C7A99.toInt()
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("JARVIS", WIDTH / 2f, HEIGHT - 50f, footer)
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(' ')
        val rows = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                rows += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) rows += current.toString()
        return rows.take(4)
    }

    // ------------------------------------------------------------------- yuv

    private fun fillYuv420(image: Image, pixels: IntArray) {
        val planes = image.planes
        if (planes.size < 3) throw IllegalStateException("Encoder YUV planes nahi de raha.")

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val width = image.width
        val height = image.height

        for (row in 0 until height) {
            for (col in 0 until width) {
                val color = pixels[row * WIDTH + col]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val yIndex = row * yRowStride + col * yPixelStride
                if (yIndex < yBuffer.capacity()) {
                    yBuffer.put(yIndex, y.coerceIn(0, 255).toByte())
                }

                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvRow = row / 2
                    val uvCol = col / 2
                    val uIndex = uvRow * uRowStride + uvCol * uPixelStride
                    val vIndex = uvRow * vRowStride + uvCol * vPixelStride
                    if (uIndex < uBuffer.capacity()) {
                        uBuffer.put(uIndex, u.coerceIn(0, 255).toByte())
                    }
                    if (vIndex < vBuffer.capacity()) {
                        vBuffer.put(vIndex, v.coerceIn(0, 255).toByte())
                    }
                }
            }
        }
    }

    companion object {
        private const val WIDTH = 480
        private const val HEIGHT = 854
        private const val FPS = 15
    }
}
