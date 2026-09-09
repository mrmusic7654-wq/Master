package com.mastercontrol.app.data.repository

import com.mastercontrol.app.core.database.database.MasterControlDatabase
import com.mastercontrol.app.data.local.toDomain
import com.mastercontrol.app.data.local.toEntity
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ChannelPermissions
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.repository.ChannelPersistence
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ChannelPersistenceImpl @Inject constructor(
    db: MasterControlDatabase,
) : ChannelPersistence {

    private val channelDao get() = db.channelDao()

    override fun observeChannels(): Flow<List<StorageChannel>> =
        channelDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeDefaultChannel(): Flow<StorageChannel?> =
        channelDao().observeDefault().map { it?.toDomain() }

    override suspend fun getChannel(channelId: Long): StorageChannel? =
        channelDao().get(channelId)?.toDomain()

    override suspend fun getChannelByDefault(): StorageChannel? =
        channelDao().getDefault()?.toDomain()

    override suspend fun addChannel(channel: StorageChannel): StorageChannel {
        val existing = channelDao().get(channel.id)
        if (existing != null) {
            throw AppError.DatabaseError("Channel ${channel.id} is already configured.")
        }
        channelDao().upsert(channel.toEntity())
        return channel
    }

    override suspend fun removeChannel(channelId: Long) {
        channelDao().delete(channelId)
    }

    override suspend fun setDefaultChannel(channelId: Long) {
        channelDao().setDefault(channelId, Instant.now().toEpochMilli())
    }

    override suspend fun setChannelEnabled(channelId: Long, enabled: Boolean) {
        val row = channelDao().get(channelId) ?: return
        channelDao().update(row.copy(enabled = enabled, updatedAtEpochMs = Instant.now().toEpochMilli()))
    }

    override suspend fun renameChannel(channelId: Long, localLabel: String?) {
        val row = channelDao().get(channelId) ?: return
        channelDao().update(row.copy(localLabel = localLabel, updatedAtEpochMs = Instant.now().toEpochMilli()))
    }

    override suspend fun refreshPermissions(channelId: Long, permissions: ChannelPermissions) {
        val row = channelDao().get(channelId) ?: return
        val now = Instant.now().toEpochMilli()
        channelDao().update(
            row.copy(
                isCreator = permissions.isCreator,
                isAdministrator = permissions.isAdministrator,
                canPostMessages = permissions.canPostMessages,
                canEditMessages = permissions.canEditMessages,
                canDeleteMessages = permissions.canDeleteMessages,
                canInviteUsers = permissions.canInviteUsers,
                canChangeInfo = permissions.canChangeInfo,
                canPinMessages = permissions.canPinMessages,
                lastVerifiedAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    override suspend fun refreshLastVerified(channelId: Long, atEpochMillis: Long) {
        val row = channelDao().get(channelId) ?: return
        channelDao().update(row.copy(lastVerifiedAtEpochMs = atEpochMillis, updatedAtEpochMs = Instant.now().toEpochMilli()))
    }
}
