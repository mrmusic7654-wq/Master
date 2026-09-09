package com.mastercontrol.app.telegram.repository

import com.mastercontrol.app.core.security.SecretAliases
import com.mastercontrol.app.core.security.SecretStore
import com.mastercontrol.app.domain.repository.TelegramCredentialsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Runtime BYOK storage for the Telegram application credentials. Values are
 * AES-GCM encrypted with an Android Keystore key (see core:security); they are
 * never logged, exported, or committed anywhere.
 */
@Singleton
class TelegramCredentialsRepositoryImpl @Inject constructor(
    private val secretStore: SecretStore,
) : TelegramCredentialsRepository {

    private val _configured = MutableStateFlow(secretStore.contains(SecretAliases.TELEGRAM_API_ID) && secretStore.contains(SecretAliases.TELEGRAM_API_HASH))

    override fun observeConfigured(): Flow<Boolean> = _configured

    override suspend fun isConfigured(): Boolean = _configured.value

    override suspend fun save(apiId: Long, apiHash: String) {
        secretStore.save(SecretAliases.TELEGRAM_API_ID, apiId.toString())
        secretStore.save(SecretAliases.TELEGRAM_API_HASH, apiHash.trim())
        _configured.value = true
    }

    override suspend fun clear() {
        secretStore.clear(SecretAliases.TELEGRAM_API_ID)
        secretStore.clear(SecretAliases.TELEGRAM_API_HASH)
        _configured.value = false
    }

    /** Returns (apiId, apiHash) or null when not configured. */
    suspend fun readCredentials(): Pair<Long, String>? {
        val id = secretStore.read(SecretAliases.TELEGRAM_API_ID)?.toLongOrNull() ?: return null
        val hash = secretStore.read(SecretAliases.TELEGRAM_API_HASH) ?: return null
        return id to hash
    }
}
