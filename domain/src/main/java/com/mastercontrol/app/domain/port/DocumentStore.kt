package com.mastercontrol.app.domain.port

/**
 * Reads and writes user-chosen documents (SAF URIs).
 *
 * Catalog export/import never touches shared storage on its own: the operator
 * picks the destination with the system document picker, and this port performs
 * the IO for that URI only. Implemented by the data layer over `ContentResolver`.
 */
interface DocumentStore {

    /** Reads a picked document as UTF-8 text. */
    suspend fun readText(uri: String): String

    /** Writes UTF-8 text to a document the operator created with the picker. */
    suspend fun writeText(uri: String, text: String)

    /** Suggested file name for a catalog export. */
    fun suggestedExportName(nowEpochMs: Long = System.currentTimeMillis()): String

    /**
     * Keeps read access to a picked document across process death and reboot.
     *
     * The system document picker grants only temporary access; a video imported
     * today may be uploaded tomorrow, so the URI stored in the catalog needs a
     * persistable grant. Returns false when the provider does not offer one (for
     * example for a plain `file://` path), which callers must treat as "access
     * may be lost later" rather than as a failure to import.
     */
    suspend fun persistReadAccess(uri: String): Boolean

    /**
     * Gives up a previously persisted grant, e.g. when the referencing catalog
     * row is deleted. Never throws: releasing access is best-effort cleanup.
     */
    suspend fun releasePersistedAccess(uri: String)
}
