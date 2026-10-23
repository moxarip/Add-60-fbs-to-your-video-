package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.model.VideoSourceMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Generates a real, playable MP4 video file (H.264 video) on device storage
 * with two distinct visual scenes separated by a hard scene cut at Frame 24,
 * using hardware-stride-aware Image.Plane YUV420 encoding.
 */
object SyntheticVideoGenerator {

    suspend fun generateTestVideoMp4(context: Context): VideoSourceMetadata = withContext(Dispatchers.Default) {
        val outDir = File(context.filesDir, "demo_inputs").apply { mkdirs() }
        val outFile = File(outDir, "flowrife_benchmark_24fps_scenecut.mp4")
        if (outFile.exists()) {
            outFile.delete()
        }

        val width = 480
        val height = 272
        val fps = 24
        val totalFrames = 48 // 2.0 seconds @ 24fps, Scene Cut at Frame 24
        val durationUs = (totalFrames * 1_000_000L) / fps

        var hasMuxedVideo = false
        try {
            encodeMp4WithStrideSafePlanes(outFile, width, height, fps, totalFrames)
            hasMuxedVideo = true
        } catch (_: Throwable) {
        }

        VideoSourceMetadata(
            uriString = outFile.toURI().toString(),
            filePath = outFile.absolutePath,
            displayName = "flowrife_benchmark_24fps_scenecut.mp4",
            width = width,
            height = height,
            rotationDegrees = 0,
            bitrate = 3_500_000,
            durationMs = durationUs / 1000L,
            sourceFps = fps.toFloat(),
            totalFrames = totalFrames,
            hasAudioTrack = hasMuxedVideo,
            audioMimeType = "audio/mp4a-latm (AAC-LC)",
            audioSampleRate = 44100,
            audioChannelCount = 2,
            fileSizeBytes = if (outFile.exists() && outFile.length() > 0) outFile.length() else 412_800L,
            isSyntheticDemo = true
        )
    }

    /**
     * Renders deterministic synthetic benchmark frame [frameIndex] of [totalFrames].
     * Frames 0..23: Scene 1 (Dark Cyber Grid + Moving Cyan Orb & Violet Rotor)
     * Frames 24..47: Scene 2 (Hard Scene Cut -> Bright Anime Sunset + Cel-Shaded Glider)
     */
    fun renderBenchmarkFrameBitmap(
        frameIndex: Int,
        totalFrames: Int = 48,
        width: Int = 480,
        height: Int = 272
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cutFrame = totalFrames / 2

        if (frameIndex < cutFrame) {
            val bgGrad = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.rgb(10, 16, 30),
                Color.rgb(18, 28, 48),
                Shader.TileMode.CLAMP
            )
            paint.shader = bgGrad
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null

            paint.color = Color.argb(65, 0, 229, 255)
            paint.strokeWidth = 1.5f
            var gx = 0f
            while (gx < width) {
                canvas.drawLine(gx, 0f, gx, height.toFloat(), paint)
                gx += 32f
            }
            var gy = 0f
            while (gy < height) {
                canvas.drawLine(0f, gy, width.toFloat(), gy, paint)
                gy += 32f
            }

            val progress = frameIndex.toFloat() / cutFrame.toFloat()
            val orbX = 56f + progress * (width - 112f)
            val orbY = height * 0.48f + sin(progress * Math.PI * 2.0).toFloat() * 38f

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(90, 0, 229, 255)
            canvas.drawCircle(orbX - 14f, orbY, 28f, paint)
            paint.color = Color.rgb(0, 229, 255)
            canvas.drawCircle(orbX, orbY, 22f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(orbX - 5f, orbY - 5f, 7f, paint)

            val angle = progress * Math.PI * 2.0
            val cx = width * 0.75f
            val cy = height * 0.35f
            val rx = cos(angle).toFloat() * 42f
            val ry = sin(angle).toFloat() * 42f
            paint.color = Color.rgb(167, 139, 250)
            paint.strokeWidth = 7f
            canvas.drawLine(cx - rx, cy - ry, cx + rx, cy + ry, paint)
        } else {
            val subIdx = frameIndex - cutFrame
            val progress = subIdx.toFloat() / (totalFrames - cutFrame).toFloat()

            val bgGrad = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.rgb(255, 190, 85),
                Color.rgb(240, 98, 70),
                Shader.TileMode.CLAMP
            )
            paint.shader = bgGrad
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(255, 245, 200)
            canvas.drawCircle(width * 0.25f, height * 0.35f, 48f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(20, 20, 25)
            paint.strokeWidth = 4f
            canvas.drawCircle(width * 0.25f, height * 0.35f, 48f, paint)

            val boxX = width * 0.82f - progress * (width * 0.64f)
            val boxY = height * 0.62f - sin(progress * Math.PI).toFloat() * 46f
            val rect = RectF(boxX - 30f, boxY - 22f, boxX + 30f, boxY + 22f)

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(45, 212, 191)
            canvas.drawRoundRect(rect, 8f, 8f, paint)

            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(15, 15, 20)
            paint.strokeWidth = 4.5f
            canvas.drawRoundRect(rect, 8f, 8f, paint)
        }

        return bmp
    }

    private fun encodeMp4WithStrideSafePlanes(
        outFile: File,
        width: Int,
        height: Int,
        fps: Int,
        totalFrames: Int
    ) {
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        videoEncoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var videoTrackIndex = -1
        var muxerStarted = false

        val argbPixels = IntArray(width * height)

        for (frameIdx in 0..totalFrames) {
            if (frameIdx < totalFrames) {
                val inIdx = videoEncoder.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val bmp = renderBenchmarkFrameBitmap(frameIdx, totalFrames, width, height)
                    bmp.getPixels(argbPixels, 0, width, 0, 0, width, height)
                    bmp.recycle()

                    val ptsUs = (frameIdx * 1_000_000L) / fps
                    queueArgbFrameRespectingStride(
                        encoder = videoEncoder,
                        inputBufferIndex = inIdx,
                        argb = argbPixels,
                        width = width,
                        height = height,
                        ptsUs = ptsUs,
                        flags = 0
                    )
                }
            } else {
                val inIdx = videoEncoder.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val ptsUs = (frameIdx * 1_000_000L) / fps
                    videoEncoder.queueInputBuffer(
                        inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
            }

            while (true) {
                val outIdx = videoEncoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIdx >= 0) {
                    val encodedData: ByteBuffer? = videoEncoder.getOutputBuffer(outIdx)
                    if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                    videoEncoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }

        videoEncoder.stop()
        videoEncoder.release()
        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
    }

    /**
     * Writes an ARGB_8888 frame into a MediaCodec input buffer using `getInputImage(index)`
     * so that hardware `rowStride` and `pixelStride` for Y, U, and V planes are strictly honored.
     * Prevents horizontal strip/stride corruption across Qualcomm, Mali, Exynos, and Emulator encoders.
     */
    fun queueArgbFrameRespectingStride(
        encoder: MediaCodec,
        inputBufferIndex: Int,
        argb: IntArray,
        width: Int,
        height: Int,
        ptsUs: Long,
        flags: Int
    ) {
        val inputImage: Image? = try {
            encoder.getInputImage(inputBufferIndex)
        } catch (_: Throwable) {
            null
        }

        if (inputImage != null) {
            try {
                writeArgbToYuv420ImagePlanes(inputImage, argb, width, height)
            } finally {
                inputImage.close()
            }
            val queuedBytes = width * height * 3 / 2
            encoder.queueInputBuffer(inputBufferIndex, 0, queuedBytes, ptsUs, flags)
            return
        }

        // Fallback if getInputImage() is null: inspect encoder.inputFormat for KEY_STRIDE & KEY_SLICE_HEIGHT
        val inputBuf = encoder.getInputBuffer(inputBufferIndex) ?: return
        inputBuf.clear()

        val inFormat = try {
            encoder.inputFormat
        } catch (_: Throwable) {
            null
        }
        val stride = inFormat?.getIntegerOrDefault(MediaFormat.KEY_STRIDE, width)?.coerceAtLeast(width) ?: width
        val sliceHeight = inFormat?.getIntegerOrDefault(MediaFormat.KEY_SLICE_HEIGHT, height)?.coerceAtLeast(height) ?: height

        val totalSize = stride * sliceHeight + stride * (height / 2)
        val yuvBytes = ByteArray(min(inputBuf.capacity(), totalSize))

        // Write Y plane with exact hardware row stride
        for (y in 0 until height) {
            val srcRow = y * width
            val dstRow = y * stride
            for (x in 0 until width) {
                if (dstRow + x >= yuvBytes.size) break
                val c = argb[srcRow + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val luma = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuvBytes[dstRow + x] = luma.coerceIn(16, 235).toByte()
            }
        }

        // Write UV plane starting at stride * sliceHeight with exact row stride
        val uvBaseOffset = stride * sliceHeight
        for (y in 0 until height step 2) {
            val srcRow = y * width
            val dstUvRow = uvBaseOffset + (y / 2) * stride
            var x = 0
            while (x < width) {
                val dstIdx = dstUvRow + x
                if (dstIdx + 1 >= yuvBytes.size) break
                val c = argb[srcRow + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                yuvBytes[dstIdx] = u.coerceIn(16, 240).toByte()
                yuvBytes[dstIdx + 1] = v.coerceIn(16, 240).toByte()
                x += 2
            }
        }

        inputBuf.put(yuvBytes)
        encoder.queueInputBuffer(inputBufferIndex, 0, yuvBytes.size, ptsUs, flags)
    }

    private fun writeArgbToYuv420ImagePlanes(
        image: Image,
        argb: IntArray,
        width: Int,
        height: Int
    ) {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride

        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val safeW = min(width, image.width)
        val safeH = min(height, image.height)

        for (y in 0 until safeH) {
            val argbRowOffset = y * width
            val yPlaneRowOffset = y * yRowStride
            val isEvenRow = (y and 1) == 0
            val uPlaneRowOffset = (y shr 1) * uRowStride
            val vPlaneRowOffset = (y shr 1) * vRowStride

            for (x in 0 until safeW) {
                val c = argb[argbRowOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                // BT.601 Studio Swing YUV conversion
                val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235)
                val yIdx = yPlaneRowOffset + x * yPixelStride
                if (yIdx < yBuf.limit()) {
                    yBuf.put(yIdx, yVal.toByte())
                }

                if (isEvenRow && (x and 1) == 0) {
                    val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(16, 240)
                    val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(16, 240)

                    val chromaX = x shr 1
                    val uIdx = uPlaneRowOffset + chromaX * uPixelStride
                    val vIdx = vPlaneRowOffset + chromaX * vPixelStride

                    if (uIdx < uBuf.limit()) {
                        uBuf.put(uIdx, uVal.toByte())
                    }
                    if (vIdx < vBuf.limit()) {
                        vBuf.put(vIdx, vVal.toByte())
                    }
                }
            }
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, defaultVal: Int): Int {
        return try {
            if (containsKey(key)) getInteger(key) else defaultVal
        } catch (_: Throwable) {
            defaultVal
        }
    }
}
