package com.mastercontrol.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mastercontrol.app.core.security.PinHasher
import com.mastercontrol.app.core.security.SecretAliases
import com.mastercontrol.app.core.security.SecretStore
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.repository.AppLockMode
import com.mastercontrol.app.domain.repository.AppLockRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * App lock state: non-sensitive toggles in DataStore, PIN hash encrypted in the
 * Android Keystore through [SecretStore]. No custom crypto.
 */
@Singleton
class AppLockRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val secretStore: SecretStore,
    private val pinHasher: PinHasher,
) : AppLockRepository {

    override fun observeLockEnabled(): Flow<Boolean> =
        dataStore.data.map { it[Keys.ENABLED] ?: false }

    override fun observeLockMode(): Flow<AppLockMode> =
        dataStore.data.map { prefs ->
            prefs[Keys.MODE]?.let { mode -> runCatching { AppLockMode.valueOf(mode) }.getOrNull() }
                ?: AppLockMode.PIN
        }

    override suspend fun setPin(pin: String) {
        if (pin.length < 4) throw AppError.CredentialValidationError("PIN must be at least 4 digits.")
        val hash = pinHasher.hash(pin)
        secretStore.save(SecretAliases.APP_LOCK_PIN, hash)
        dataStore.edit {
            it[Keys.ENABLED] = true
            it[Keys.MODE] = AppLockMode.PIN.name
        }
    }

    override suspend fun disable() {
        dataStore.edit {
            it[Keys.ENABLED] = false
            it.remove(Keys.MODE)
        }
        secretStore.clear(SecretAliases.APP_LOCK_PIN)
    }

    override suspend fun verifyPin(pin: String): Boolean {
        if (!observeLockEnabled().first()) return true
        val stored = secretStore.read(SecretAliases.APP_LOCK_PIN) ?: return false
        return pinHasher.verify(pin, stored)
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("app_lock_enabled")
        val MODE = stringPreferencesKey("app_lock_mode")
    }
}
