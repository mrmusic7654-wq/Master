package com.mastercontrol.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Catalog representation designed for the future Streamer application and for
 * JSON backup/restore. This model deliberately does NOT depend on the Room
 * implementation: a future backend API can serve the same shape.
 *
 * Secrets (API hash, verification codes, session keys) are never part of any
 * serializable catalog object.
 */
@Serializable
data class StreamerCatalogItem(
    @SerialName("videoId") val videoId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("thumbnailReference") val thumbnailReference: String? = null,
    @SerialName("durationMs") val durationMs: Long? = null,
    @SerialName("fileSizeBytes") val fileSizeBytes: Long? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("folder") val folder: String? = null,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("telegramChannelId") val telegramChannelId: Long? = null,
    @SerialName("telegramMessageId") val telegramMessageId: Long? = null,
    @SerialName("metadata") val metadata: Map<String, String> = emptyMap(),
)

/** Full JSON backup envelope produced by export and consumed by import. */
@Serializable
data class CatalogBackup(
    @SerialName("format") val format: String = "master-control-catalog",
    @SerialName("formatVersion") val formatVersion: Int = 1,
    @SerialName("exportedAt") val exportedAt: String,
    @SerialName("categories") val categories: List<CategoryBackup> = emptyList(),
    @SerialName("folders") val folders: List<FolderBackup> = emptyList(),
    @SerialName("items") val items: List<StreamerCatalogItem> = emptyList(),
)

@Serializable
data class CategoryBackup(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    @SerialName("icon") val icon: String = "",
    @SerialName("sortOrder") val sortOrder: Int = 0,
)

@Serializable
data class FolderBackup(
    @SerialName("name") val name: String,
    @SerialName("parentName") val parentName: String? = null,
    @SerialName("description") val description: String = "",
    @SerialName("sortOrder") val sortOrder: Int = 0,
)

/** One previewed/validated row during catalog import. */
enum class ImportConflictKind { NONE, VIDEO_ID_EXISTS, TITLE_MATCH }

data class ImportPreviewItem(
    val streamerItem: StreamerCatalogItem,
    val conflict: ImportConflictKind,
    val conflictDetail: String? = null,
)

data class ImportPreview(
    val categories: List<String>,
    val folders: List<String>,
    val items: List<ImportPreviewItem>,
    val valid: Boolean,
    val invalidReason: String? = null,
)

enum class ImportMode { MERGE, REPLACE_CONFLICTS }
