package com.mastercontrol.app.domain.model

import java.time.Instant

/** What kind of Telegram chat a storage location is. */
enum class ChannelKind {
    /** Broadcast channel (chatTypeSupergroup with isChannel = true). */
    CHANNEL,

    /** Broadcast group (supergroup where posts are visible as channel posts). */
    BROADCAST_GROUP,

    /** Regular supergroup. */
    SUPERGROUP,
}

/**
 * Rights the authenticated account has in a channel. `null` permission means "not
 * granted / not determinable". Creator status implies all rights.
 */
data class ChannelPermissions(
    val isCreator: Boolean = false,
    val isAdministrator: Boolean = false,
    val canPostMessages: Boolean = false,
    val canEditMessages: Boolean = false,
    val canDeleteMessages: Boolean = false,
    val canInviteUsers: Boolean = false,
    val canChangeInfo: Boolean = false,
    val canPinMessages: Boolean = false,
) {
    val canUploadVideos: Boolean
        get() = isCreator || (isAdministrator && canPostMessages)

    val missingForUpload: List<String>
        get() = buildList {
            if (!canPostMessages) add("post messages")
            if (!canEditMessages) add("edit messages")
            if (!canDeleteMessages) add("delete messages")
        }

    fun describe(): String = buildString {
        append(if (isCreator) "Creator" else if (isAdministrator) "Administrator" else "Member")
        append(" — ")
        append(if (canPostMessages) "✓ Can post" else "✗ Cannot post")
        append(" · ")
        append(if (canEditMessages) "✓ Can edit" else "✗ Cannot edit")
        append(" · ")
        append(if (canDeleteMessages) "✓ Can delete" else "✗ Cannot delete")
    }
}

/**
 * A channel configured in Master Control as a storage location.
 * Stored locally; [id] is the Telegram chat identifier.
 */
data class StorageChannel(
    val id: Long,
    val title: String,
    val username: String? = null,
    val kind: ChannelKind,
    val permissions: ChannelPermissions,
    val localLabel: String? = null,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val lastVerifiedAt: Instant? = null,
    val lastSynchronizedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val displayName: String get() = localLabel ?: title
}

/** A Telegram channel found during discovery that can be added as storage. */
data class ChannelCandidate(
    val chatId: Long,
    val title: String,
    val username: String? = null,
    val kind: ChannelKind,
    val permissions: ChannelPermissions,
    val memberCount: Int? = null,
)

/** Detailed verification result for one configured channel. */
data class ChannelVerification(
    val channel: StorageChannel,
    val reachable: Boolean,
    val permissions: ChannelPermissions,
    val notes: List<String> = emptyList(),
)
