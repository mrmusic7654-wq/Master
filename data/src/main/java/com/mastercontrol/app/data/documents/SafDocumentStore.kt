package com.mastercontrol.app.data.documents

import android.content.Context
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.port.DocumentStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF-backed [DocumentStore].
 *
 * Only the URI the operator picked is ever opened; nothing is written to shared
 * storage implicitly. IO runs on [Dispatchers.IO] so the caller's dispatcher is
 * never blocked.
 */
@Singleton
class SafDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentStore {

    private val io: CoroutineDispatcher = Dispatchers.IO

    override suspend fun readText(uri: String): String = withContext(io) {
        val resolver = context.contentResolver
        try {
            resolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw AppError.StorageError("The selected document could not be opened for reading.")
        } catch (error: IOException) {
            throw AppError.StorageError("Reading the selected document failed.", error)
        } catch (security: SecurityException) {
            throw AppError.StorageError("Permission to read the selected document was denied.", security)
        }
    }

    override suspend fun writeText(uri: String, text: String) {
        withContext(io) {
            val resolver = context.contentResolver
            try {
                resolver.openOutputStream(android.net.Uri.parse(uri), "wt")?.use { output ->
                    output.write(text.toByteArray(Charsets.UTF_8))
                    output.flush()
                } ?: throw AppError.StorageError("The selected destination could not be opened for writing.")
            } catch (error: IOException) {
                throw AppError.StorageError("Writing the catalog backup failed.", error)
            } catch (security: SecurityException) {
                throw AppError.StorageError("Permission to write the selected destination was denied.", security)
            }
        }
    }

    override fun suggestedExportName(nowEpochMs: Long): String {
        val stamp = Instant.ofEpochMilli(nowEpochMs).atZone(ZoneId.systemDefault()).format(FILE_STAMP)
        return "master-control-catalog-$stamp.json"
    }

    override suspend fun persistReadAccess(uri: String): Boolean = withContext(io) {
        val parsed = android.net.Uri.parse(uri)
        // Only content URIs from a documents provider can carry a persistable
        // grant; anything else (file://, http://) is refused here rather than
        // throwing into the import flow.
        if (parsed.scheme != "content") return@withContext false
        try {
            context.contentResolver.takePersistableUriPermission(parsed, READ_ONLY)
            true
        } catch (security: SecurityException) {
            // The provider did not offer a persistable grant (e.g. a share sheet
            // result). Access stays valid for the current process only.
            false
        }
    }

    override suspend fun releasePersistedAccess(uri: String) {
        withContext(io) {
            val parsed = android.net.Uri.parse(uri)
            if (parsed.scheme != "content") return@withContext
            try {
                context.contentResolver.releasePersistableUriPermission(parsed, READ_ONLY)
            } catch (security: SecurityException) {
                // Nothing was granted, or the grant is already gone.
            }
        }
    }

    private companion object {
        /** Locale-fixed so a backup file name is stable on every device. */
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT)

        val READ_ONLY: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}
