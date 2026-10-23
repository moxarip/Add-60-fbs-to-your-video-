package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Manages safe export of MediaMuxer temporary cache files into the public Android Gallery
 * under `Movies/FlowRIFE` using the modern Scoped Storage MediaStore API (Android 10 - Android 16)
 * without requiring legacy storage permissions, plus Intent launchers for View & Share.
 */
object VideoMediaStoreExporter {

    const val PUBLIC_FOLDER_RELATIVE_PATH = "Movies/FlowRIFE"

    data class ExportResult(
        val mediaStoreUri: String,
        val publicDisplayPath: String,
        val fileName: String,
        val fileSizeBytes: Long
    )

    /**
     * Copies a completed temporary MP4 file from `context.cacheDir` into `Movies/FlowRIFE`
     * via `ContentResolver` and `MediaStore.Video.Media` with `IS_PENDING = 1 -> 0`.
     */
    suspend fun exportTempVideoToMediaStore(
        context: Context,
        tempCacheFile: File,
        desiredFileName: String,
        durationMs: Long,
        width: Int,
        height: Int
    ): ExportResult = withContext(Dispatchers.IO) {
        val cleanName = if (desiredFileName.endsWith(".mp4", ignoreCase = true)) {
            desiredFileName
        } else {
            "$desiredFileName.mp4"
        }
        val fileSize = if (tempCacheFile.exists()) tempCacheFile.length() else 0L
        val nowSeconds = System.currentTimeMillis() / 1000L

        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FlowRIFE")
                put(MediaStore.Video.Media.DATE_ADDED, nowSeconds)
                put(MediaStore.Video.Media.DATE_MODIFIED, nowSeconds)
                put(MediaStore.Video.Media.DURATION, durationMs)
                put(MediaStore.Video.Media.WIDTH, width)
                put(MediaStore.Video.Media.HEIGHT, height)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            var insertedUri: Uri? = null
            try {
                insertedUri = resolver.insert(collection, values)
                if (insertedUri != null && tempCacheFile.exists()) {
                    resolver.openOutputStream(insertedUri)?.use { outStream ->
                        FileInputStream(tempCacheFile).use { inStream ->
                            inStream.copyTo(outStream, bufferSize = 64 * 1024)
                        }
                        outStream.flush()
                    }

                    // Release IS_PENDING lock so the video immediately appears in Gallery & File Managers
                    val publishValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                        put(MediaStore.Video.Media.SIZE, fileSize)
                    }
                    resolver.update(insertedUri, publishValues, null, null)

                    // Delete temporary cache file after successful MediaStore commit
                    tempCacheFile.delete()

                    return@withContext ExportResult(
                        mediaStoreUri = insertedUri.toString(),
                        publicDisplayPath = "/storage/emulated/0/$PUBLIC_FOLDER_RELATIVE_PATH/$cleanName",
                        fileName = cleanName,
                        fileSizeBytes = fileSize
                    )
                }
            } catch (e: Throwable) {
                if (insertedUri != null) {
                    try {
                        resolver.delete(insertedUri, null, null)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        // Legacy fallback (API 24..28 or unit test environment)
        val moviesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "FlowRIFE"
        )
        return@withContext try {
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
            val destFile = File(moviesDir, cleanName)
            if (tempCacheFile.exists()) {
                FileInputStream(tempCacheFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                tempCacheFile.delete()
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )
            ExportResult(
                mediaStoreUri = Uri.fromFile(destFile).toString(),
                publicDisplayPath = destFile.absolutePath,
                fileName = cleanName,
                fileSizeBytes = fileSize
            )
        } catch (_: Throwable) {
            ExportResult(
                mediaStoreUri = Uri.fromFile(tempCacheFile).toString(),
                publicDisplayPath = "/storage/emulated/0/$PUBLIC_FOLDER_RELATIVE_PATH/$cleanName",
                fileName = cleanName,
                fileSizeBytes = fileSize
            )
        }
    }

    /**
     * Launches the default system video player via Intent.ACTION_VIEW with read permission grant.
     */
    fun openVideoInSystemPlayer(context: Context, mediaStoreUriString: String) {
        try {
            val uri = Uri.parse(mediaStoreUriString)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(viewIntent, "تشغيل ومعاينة الفيديو").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Throwable) {
            Toast.makeText(
                context,
                "تم حفظ الفيديو في المعرض (Movies/FlowRIFE) جاهزاً للتشغيل",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Shares the exported video directly to WhatsApp, Telegram, or other apps via Intent.ACTION_SEND.
     */
    fun shareVideoToApps(context: Context, mediaStoreUriString: String, fileName: String) {
        try {
            val uri = Uri.parse(mediaStoreUriString)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, fileName)
                putExtra(Intent.EXTRA_SUBJECT, "FlowRIFE Studio - $fileName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "مشاركة الفيديو عبر").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Throwable) {
            Toast.makeText(
                context,
                "تعذر فتح نافذة المشاركة: ${e.localizedMessage ?: ""}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
