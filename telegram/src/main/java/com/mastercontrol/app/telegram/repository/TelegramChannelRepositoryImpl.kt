package com.mastercontrol.app.telegram.repository

import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ChannelCandidate
import com.mastercontrol.app.domain.model.ChannelPermissions
import com.mastercontrol.app.domain.model.ChannelVerification
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.repository.ChannelPersistence
import com.mastercontrol.app.domain.repository.TelegramChannelRepository
import com.mastercontrol.app.telegram.tdlib.core.TdChatManager
import com.mastercontrol.app.telegram.tdlib.manager.TdLibManager
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Channel management: live discovery/verification via TDLib, local bookkeeping
 * delegated to [ChannelPersistence].
 */
@Singleton
class TelegramChannelRepositoryImpl @Inject constructor(
    manager: TdLibManager,
    private val persistence: ChannelPersistence,
) : TelegramChannelRepository {

    private val chatManager: TdChatManager = manager.chatManager

    override fun observeChannels(): Flow<List<StorageChannel>> = persistence.observeChannels()

    override fun observeDefaultChannel(): Flow<StorageChannel?> = persistence.observeDefaultChannel()

    override suspend fun getChannel(channelId: Long): StorageChannel? = persistence.getChannel(channelId)

    override suspend fun getChannelByDefault(): StorageChannel? = persistence.getChannelByDefault()

    override suspend fun searchChannels(query: String): List<ChannelCandidate> =
        chatManager.searchChannels(query)

    override suspend fun verifyPermissions(chatId: Long): ChannelPermissions =
        chatManager.verifyPermissions(chatId)

    override suspend fun addChannel(candidate: ChannelCandidate): StorageChannel {
        // Channels without posting rights can still be stored; upload is refused
        // at queue time with a precise explanation (QueueUploadUseCase).
        val now = Instant.now()
        return persistence.addChannel(
            StorageChannel(
                id = candidate.chatId,
                title = candidate.title,
                username = candidate.username,
                kind = candidate.kind,
                permissions = candidate.permissions,
                lastVerifiedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun removeChannel(channelId: Long) = persistence.removeChannel(channelId)

    override suspend fun setDefaultChannel(channelId: Long) = persistence.setDefaultChannel(channelId)

    override suspend fun setChannelEnabled(channelId: Long, enabled: Boolean) =
        persistence.setChannelEnabled(channelId, enabled)

    override suspend fun renameChannel(channelId: Long, localLabel: String?) =
        persistence.renameChannel(channelId, localLabel)

    override suspend fun verifyChannel(channelId: Long): ChannelVerification {
        val stored = persistence.getChannel(channelId) ?: throw AppError.ChannelNotFoundError()
        return try {
            val live = chatManager.describeChannel(channelId)
            val notes = mutableListOf<String>()
            if (live.kind.name == "CHANNEL" && !live.permissions.canUploadVideos) {
                notes += "This account cannot post to this channel — uploads will be blocked."
            }
            if (live.permissions.canPostMessages && !live.permissions.canEditMessages) {
                notes += "The account can post but not edit messages in this channel."
            }
            ChannelVerification(
                channel = stored.copy(
                    title = live.title,
                    username = live.username ?: stored.username,
                    kind = live.kind,
                    permissions = live.permissions,
                    lastVerifiedAt = Instant.now(),
                ),
                reachable = true,
                permissions = live.permissions,
                notes = notes,
            )
        } catch (t: AppError) {
            ChannelVerification(
                channel = stored,
                reachable = false,
                permissions = stored.permissions,
                notes = listOf(t.userMessage),
            )
        }
    }

    override suspend fun refreshPermissions(channelId: Long, permissions: ChannelPermissions) {
        persistence.refreshPermissions(channelId, permissions)
    }
}
