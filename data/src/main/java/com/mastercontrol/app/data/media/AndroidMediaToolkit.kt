package com.mastercontrol.app.data.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.error.InvalidMediaError
import com.mastercontrol.app.domain.port.GeneratedThumbnail
import com.mastercontrol.app.domain.port.MediaSourceInfo
import com.mastercontrol.app.domain.port.MediaToolkit
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * ContentResolver-based video inspection, metadata extraction and thumbnail
 * generation. Never loads a video into memory; streams in bounded buffers.
 */
@Singleton
class AndroidMediaToolkit @Inject constructor(
    context: Context,
) : MediaToolkit {

    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun inspect(contentUri: String): MediaSourceInfo = withContext(Dispatchers.IO) {
        val uri = Uri.parse(contentUri)
        val displayName = queryDisplayName(uri) ?: "video"
        val sizeBytes = querySize(uri) ?: throw InvalidMediaError("File is not readable (no size).")
        val pfd = try {
            contentResolver.openFileDescriptor(uri, "r")
        } catch (t: Throwable) {
            null
        } ?: throw InvalidMediaError("File does not exist or is not readable.")

        pfd.use { descriptor ->
            val info = extractVideoInfo(descriptor, uri, displayName, sizeBytes)
            if (!info.hasVideoTrack) throw InvalidMediaError("No video track found in '$displayName'.")
            info
        }
    }

    override suspend fun generateThumbnail(
        contentUri: String,
        captureMs: Long,
        fallbackCaptureMs: Long,
    ): GeneratedThumbnail = withContext(Dispatchers.IO) {
        val uri = Uri.parse(contentUri)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
        } catch (t: Throwable) {
            throw InvalidMediaError("Could not read the video to generate a thumbnail.")
        }
        try {
            val captureTimes = listOf(captureMs * 1000L, fallbackCaptureMs.coerceAtLeast(0L) * 1000L, 0L)
            var frame: Bitmap? = null
            for (timeUs in captureTimes.distinct()) {
                frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) break
            }
            val bitmap = frame ?: throw InvalidMediaError("No frame could be decoded for a thumbnail.")

            val maxDim = if (thumbnailsAreStandard) 640 else 1280
            val scaled = scaleDown(bitmap, maxDim)
            val width = scaled.width
            val height = scaled.height
            val file = thumbnailFile()
            file.outputStream().use { out ->
                val quality = if (thumbnailsAreStandard) 80 else 90
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            if (scaled !== bitmap) bitmap.recycle()
            scaled.recycle()
            GeneratedThumbnail(fileUri = file.absolutePath, width = width, height = height, mimeType = "image/jpeg")
        } finally {
            runCatching { retriever.release() }
        }
    }

    override suspend fun computeSha256(contentUri: String, isActive: () -> Boolean): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(contentUri)
        val pfd = contentResolver.openFileDescriptor(uri, "r")
            ?: throw AppError.MediaUnavailableError("File is no longer accessible.")
        pfd.use { fd ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(256 * 1024)
            fd.openInputStream().use { input ->
                while (true) {
                    ensureActive()
                    if (!isActive()) throw AppError.UploadCancelledError("Hashing cancelled.")
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    // ---- internals ----------------------------------------------------------

    /** Quality toggled from Settings; defaults to standard to conserve space. */
    @Volatile
    var thumbnailsAreStandard: Boolean = true

    private fun thumbnailFile(): File {
        val dir = File(appContext.cacheDir, "thumbnails").apply { mkdirs() }
        return File.createTempFile("thumb_", ".jpg", dir)
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxSide
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun querySize(uri: Uri): Long? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        }.getOrNull() ?: runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { len -> len > 0 } }
        }.getOrNull()

    private fun extractVideoInfo(
        descriptor: android.os.ParcelFileDescriptor,
        uri: Uri,
        displayName: String,
        sizeBytes: Long,
    ): MediaSourceInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(descriptor.fileDescriptor)
        } catch (t: Throwable) {
            throw InvalidMediaError("Could not parse the file as a video.")
        }
        var durationMs: Long? = null
        var width: Int? = null
        var height: Int? = null
        var frameRate: Double? = null
        var mime: String? = null
        var hasVideo = false
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mimeType.startsWith("video/")) {
                hasVideo = true
                mime = mimeType
                durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000L else null
                width = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else null
                height = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else null
                frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() else null
                break
            }
        }
        extractor.release()
        if (!hasVideo) {
            // Some containers need the retriever to decide video-ness.
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(descriptor.fileDescriptor)
                val hasVideoMeta = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
                if (hasVideoMeta) {
                    mime = contentResolver.getType(uri) ?: mime
                    durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                }
            } finally {
                runCatching { r.release() }
            }
        }
        val fallbackMime = contentResolver.getType(uri) ?: "video/mp4"
        return MediaSourceInfo(
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = mime ?: fallbackMime,
            durationMs = durationMs,
            width = width,
            height = height,
            frameRate = frameRate,
            hasVideoTrack = hasVideo,
        )
    }
}
