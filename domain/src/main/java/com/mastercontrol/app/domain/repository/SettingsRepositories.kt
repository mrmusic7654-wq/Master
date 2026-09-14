package com.mastercontrol.app.domain.repository

import com.mastercontrol.app.domain.model.AppSettings
import com.mastercontrol.app.domain.model.LibraryLayout
import com.mastercontrol.app.domain.model.LibrarySort
import com.mastercontrol.app.domain.model.ThemeMode
import com.mastercontrol.app.domain.model.UploadNetworkRule
import kotlinx.coroutines.flow.Flow

/** Non-sensitive settings. */
interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun getSettings(): AppSettings

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setLibraryLayout(layout: LibraryLayout)
    suspend fun setLibrarySort(sort: LibrarySort)
    suspend fun setUploadNetworkRule(rule: UploadNetworkRule)
    suspend fun setAllowUploadsOnlyWhileCharging(enabled: Boolean)
    suspend fun setMaxConcurrentUploads(count: Int)
    suspend fun setThumbnailQualityEnabledStandard(standard: Boolean)
    suspend fun setThumbnailCaptureMs(ms: Long)
    suspend fun setCaptionIncludesVideoId(enabled: Boolean)
    suspend fun setMediaHashingEnabled(enabled: Boolean)
    suspend fun setAutoDeleteLocalCopyAfterUpload(enabled: Boolean)
}

/** Application-lock configuration. PIN verification hashing is done in the implementation. */
interface AppLockRepository {
    /** True when an app lock is currently active. */
    fun observeLockEnabled(): Flow<Boolean>

    fun observeLockMode(): Flow<AppLockMode>

    /** Stores a Master Control PIN and switches the lock to [AppLockMode.PIN]. */
    suspend fun setPin(pin: String)

    /**
     * Switches the lock to a device-unlock based mode.
     *
     * BIOMETRIC and DEVICE_CREDENTIAL delegate to the platform prompt, so no
     * secret is stored by Master Control for them. PIN mode requires [setPin].
     */
    suspend fun setMode(mode: AppLockMode)

    suspend fun disable()
    suspend fun verifyPin(pin: String): Boolean
}

enum class AppLockMode { PIN, BIOMETRIC, DEVICE_CREDENTIAL }
