package com.mastercontrol.app.data.local

import com.mastercontrol.app.core.database.entity.ActivityEntity
import com.mastercontrol.app.core.database.entity.CategoryEntity
import com.mastercontrol.app.core.database.entity.ChannelEntity
import com.mastercontrol.app.core.database.entity.FolderEntity
import com.mastercontrol.app.core.database.entity.TelegramMappingEntity
import com.mastercontrol.app.core.database.entity.UploadTaskEntity
import com.mastercontrol.app.core.database.entity.VideoEntity
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.Category
import com.mastercontrol.app.domain.model.ChannelPermissions
import com.mastercontrol.app.domain.model.Folder
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.model.TelegramMapping
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.Video
import java.time.Instant

// ---- entity -> domain ------------------------------------------------------

fun VideoEntity.toDomain(tags: List<String>): Video = Video(
    videoId = videoId,
    title = title,
    originalFileName = originalFileName,
    description = description,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType,
    width = width,
    height = height,
    frameRate = frameRate,
    thumbnailUri = thumbnailUri,
    posterUri = posterUri,
    sourceUri = sourceUri,
    releaseDate = releaseDate,
    language = language,
    rating = rating,
    year = year,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    status = enumValueOf(status),
    categoryId = categoryId,
    folderId = folderId,
    tags = tags,
    sha256 = sha256,
    hashPending = hashPending,
)

fun TelegramMappingEntity.toDomain(): TelegramMapping = TelegramMapping(
    videoId = videoId,
    channelId = channelId,
    messageId = messageId,
    telegramFileId = telegramFileId,
    telegramRemoteFileId = telegramRemoteFileId,
    telegramUniqueFileId = telegramUniqueFileId,
    telegramLocalFilePath = telegramLocalFilePath,
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType,
    uploadedAt = Instant.ofEpochMilli(uploadedAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    mappingStatus = enumValueOf(mappingStatus),
)

fun CategoryEntity.toDomain(): Category = Category(
    categoryId = categoryId,
    name = name,
    description = description,
    icon = icon,
    sortOrder = sortOrder,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

fun FolderEntity.toDomain(): Folder = Folder(
    folderId = folderId,
    name = name,
    parentFolderId = parentFolderId,
    description = description,
    sortOrder = sortOrder,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

fun ChannelEntity.toDomain(): StorageChannel = StorageChannel(
    id = channelId,
    title = title,
    username = username,
    kind = enumValueOf(kind),
    permissions = ChannelPermissions(
        isCreator = isCreator,
        isAdministrator = isAdministrator,
        canPostMessages = canPostMessages,
        canEditMessages = canEditMessages,
        canDeleteMessages = canDeleteMessages,
        canInviteUsers = canInviteUsers,
        canChangeInfo = canChangeInfo,
        canPinMessages = canPinMessages,
    ),
    localLabel = localLabel,
    enabled = enabled,
    isDefault = isDefault,
    lastVerifiedAt = lastVerifiedAtEpochMs?.let(Instant::ofEpochMilli),
    lastSynchronizedAt = lastSynchronizedAtEpochMs?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

fun UploadTaskEntity.toDomain(): UploadTask = UploadTask(
    taskId = taskId,
    videoId = videoId,
    sourceUri = sourceUri,
    fileName = fileName,
    channelId = channelId,
    state = enumValueOf(state),
    bytesUploaded = bytesUploaded,
    totalBytes = totalBytes,
    attemptCount = attemptCount,
    lastError = lastError,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

fun ActivityEntity.toDomain(): ActivityLogEntry = ActivityLogEntry(
    activityId = activityId,
    type = enumValueOf(type),
    message = message,
    details = details,
    relatedVideoId = relatedVideoId,
    channelId = channelId,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
)

// ---- domain -> entity ------------------------------------------------------

fun Video.toEntity(): VideoEntity = VideoEntity(
    id = 0L,
    videoId = videoId,
    title = title,
    originalFileName = originalFileName,
    description = description,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType,
    width = width,
    height = height,
    frameRate = frameRate,
    thumbnailUri = thumbnailUri,
    posterUri = posterUri,
    sourceUri = sourceUri,
    releaseDate = releaseDate,
    language = language,
    rating = rating,
    year = year,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
    status = status.name,
    categoryId = categoryId,
    folderId = folderId,
    sha256 = sha256,
    hashPending = hashPending,
)

fun TelegramMapping.toEntity(): TelegramMappingEntity = TelegramMappingEntity(
    videoId = videoId,
    channelId = channelId,
    messageId = messageId,
    telegramFileId = telegramFileId,
    telegramRemoteFileId = telegramRemoteFileId,
    telegramUniqueFileId = telegramUniqueFileId,
    telegramLocalFilePath = telegramLocalFilePath,
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType,
    uploadedAtEpochMs = uploadedAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
    mappingStatus = mappingStatus.name,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    categoryId = if (categoryId == 0L) 0L else categoryId,
    name = name,
    description = description,
    icon = icon,
    sortOrder = sortOrder,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

fun Folder.toEntity(): FolderEntity = FolderEntity(
    folderId = if (folderId == 0L) 0L else folderId,
    name = name,
    parentFolderId = parentFolderId,
    description = description,
    sortOrder = sortOrder,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

fun StorageChannel.toEntity(): ChannelEntity = ChannelEntity(
    channelId = id,
    title = title,
    username = username,
    kind = kind.name,
    isCreator = permissions.isCreator,
    isAdministrator = permissions.isAdministrator,
    canPostMessages = permissions.canPostMessages,
    canEditMessages = permissions.canEditMessages,
    canDeleteMessages = permissions.canDeleteMessages,
    canInviteUsers = permissions.canInviteUsers,
    canChangeInfo = permissions.canChangeInfo,
    canPinMessages = permissions.canPinMessages,
    localLabel = localLabel,
    enabled = enabled,
    isDefault = isDefault,
    lastVerifiedAtEpochMs = lastVerifiedAt?.toEpochMilli(),
    lastSynchronizedAtEpochMs = lastSynchronizedAt?.toEpochMilli(),
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

fun UploadTask.toEntity(): UploadTaskEntity = UploadTaskEntity(
    taskId = if (taskId == 0L) 0L else taskId,
    videoId = videoId,
    sourceUri = sourceUri,
    fileName = fileName,
    channelId = channelId,
    state = state.name,
    bytesUploaded = bytesUploaded,
    totalBytes = totalBytes,
    attemptCount = attemptCount,
    lastError = lastError,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

fun ActivityLogEntry.toEntity(): ActivityEntity = ActivityEntity(
    activityId = if (activityId == 0L) 0L else activityId,
    type = type.name,
    message = message,
    details = details,
    relatedVideoId = relatedVideoId,
    channelId = channelId,
    createdAtEpochMs = createdAt.toEpochMilli(),
)
