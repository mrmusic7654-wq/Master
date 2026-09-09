package com.mastercontrol.app.domain.repository

import com.mastercontrol.app.domain.model.AuthorizationState
import com.mastercontrol.app.domain.model.ChannelCandidate
import com.mastercontrol.app.domain.model.ChannelPermissions
import com.mastercontrol.app.domain.model.ChannelVerification
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.model.TelegramAccountInfo
import kotlinx.coroutines.flow.Flow

/** Runtime-provided Telegram application credentials (api_id / api_hash). */
interface TelegramCredentialsRepository {
    /** True once both api_id and api_hash are stored. */
    fun observeConfigured(): Flow<Boolean>

    suspend fun isConfigured(): Boolean

    /** Stored credentials (apiId to apiHash), or null when not configured. */
    suspend fun readCredentials(): Pair<Long, String>?

    suspend fun save(apiId: Long, apiHash: String)

    suspend fun clear()
}

/** Session/authorization handling against the Telegram account. */
interface TelegramAccountRepository {

    fun observeAuthorizationState(): Flow<AuthorizationState>

    fun observeConnectionState(): Flow<ConnectionState>

    /** Starts the TDLib engine when credentials are configured. No-op if already started. */
    suspend fun start()

    /** Restarts the underlying engine (used by diagnostics). */
    suspend fun restart()

    suspend fun submitPhoneNumber(phoneNumber: String)

    suspend fun submitAuthenticationCode(code: String)

    suspend fun submitTwoFactorPassword(password: String)

    suspend fun resendCode()

    suspend fun logout()

    /** Details about the currently authenticated account. */
    suspend fun getAccountInfo(): TelegramAccountInfo?

    suspend fun getTdlibVersion(): String
}

/**
 * Local bookkeeping of storage channels (Room-backed). Implemented by the data
 * module; Telegram-side discovery lives in [TelegramChannelRepository].
 */
interface ChannelPersistence {
    fun observeChannels(): Flow<List<StorageChannel>>

    fun observeDefaultChannel(): Flow<StorageChannel?>

    suspend fun getChannel(channelId: Long): StorageChannel?

    suspend fun getChannelByDefault(): StorageChannel?

    suspend fun addChannel(channel: StorageChannel): StorageChannel

    /** Removes a channel from Master Control (does not touch Telegram). */
    suspend fun removeChannel(channelId: Long)

    suspend fun setDefaultChannel(channelId: Long)

    suspend fun setChannelEnabled(channelId: Long, enabled: Boolean)

    suspend fun renameChannel(channelId: Long, localLabel: String?)

    suspend fun refreshPermissions(channelId: Long, permissions: ChannelPermissions)

    suspend fun refreshLastVerified(channelId: Long, atEpochMillis: Long)
}

/**
 * Telegram-side channel discovery and live permission verification.
 * Implemented by the telegram module over TDLib; combines [ChannelPersistence]
 * so stored rows stay in sync after verification.
 */
interface TelegramChannelRepository {
    fun observeChannels(): Flow<List<StorageChannel>>
    fun observeDefaultChannel(): Flow<StorageChannel?>
    suspend fun getChannel(channelId: Long): StorageChannel?
    suspend fun getChannelByDefault(): StorageChannel?

    /** Discover channels by name/username (Telegram-side search + main chat list). */
    suspend fun searchChannels(query: String): List<ChannelCandidate>

    /** Verify the account's current permissions inside [chatId] right now. */
    suspend fun verifyPermissions(chatId: Long): ChannelPermissions

    /** Persist a candidate as a storage location. */
    suspend fun addChannel(candidate: ChannelCandidate): StorageChannel

    /** Remove a channel from Master Control (does not touch Telegram). */
    suspend fun removeChannel(channelId: Long)

    suspend fun setDefaultChannel(channelId: Long)

    suspend fun setChannelEnabled(channelId: Long, enabled: Boolean)

    suspend fun renameChannel(channelId: Long, localLabel: String?)

    /** Detailed verification for display (permissions + reachability notes). */
    suspend fun verifyChannel(channelId: Long): ChannelVerification

    /** Persists freshly verified permissions into the stored channel row. */
    suspend fun refreshPermissions(channelId: Long, permissions: ChannelPermissions)
}
