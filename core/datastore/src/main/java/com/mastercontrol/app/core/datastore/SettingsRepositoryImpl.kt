package com.mastercontrol.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mastercontrol.app.domain.model.AppSettings
import com.mastercontrol.app.domain.model.LibraryLayout
import com.mastercontrol.app.domain.model.LibrarySort
import com.mastercontrol.app.domain.model.ThemeMode
import com.mastercontrol.app.domain.model.ThumbnailQuality
import com.mastercontrol.app.domain.model.UploadNetworkRule
import com.mastercontrol.app.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [SettingsRepository]. Only non-sensitive settings live
 * here; api_hash / session / PIN data never touch this store.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    override suspend fun getSettings(): AppSettings = dataStore.data.map { it.toAppSettings() }.first()

    override suspend fun setThemeMode(mode: ThemeMode) = edit { prefs -> prefs[Keys.THEME] = mode.name }
    override suspend fun setDynamicColor(enabled: Boolean) = edit { prefs -> prefs[Keys.DYNAMIC_COLOR] = enabled }
    override suspend fun setLibraryLayout(layout: LibraryLayout) = edit { prefs -> prefs[Keys.LAYOUT] = layout.name }
    override suspend fun setLibrarySort(sort: LibrarySort) = edit { prefs -> prefs[Keys.SORT] = sort.name }
    override suspend fun setUploadNetworkRule(rule: UploadNetworkRule) = edit { prefs -> prefs[Keys.NETWORK_RULE] = rule.name }
    override suspend fun setAllowUploadsOnlyWhileCharging(enabled: Boolean) = edit { prefs -> prefs[Keys.CHARGING_ONLY] = enabled }
    override suspend fun setMaxConcurrentUploads(count: Int) = edit { prefs -> prefs[Keys.MAX_CONCURRENT] = count.coerceIn(1, 4) }
    override suspend fun setThumbnailQualityEnabledStandard(standard: Boolean) = edit { prefs -> prefs[Keys.THUMB_STANDARD] = standard }
    override suspend fun setThumbnailCaptureMs(ms: Long) = edit { prefs -> prefs[Keys.THUMB_CAPTURE_MS] = ms }
    override suspend fun setCaptionIncludesVideoId(enabled: Boolean) = edit { prefs -> prefs[Keys.CAPTION_VIDEO_ID] = enabled }
    override suspend fun setMediaHashingEnabled(enabled: Boolean) = edit { prefs -> prefs[Keys.MEDIA_HASHING] = enabled }
    override suspend fun setAutoDeleteLocalCopyAfterUpload(enabled: Boolean) = edit { prefs -> prefs[Keys.AUTO_DELETE_COPY] = enabled }

    private suspend fun edit(block: MutablePreferences.() -> Unit) {
        dataStore.edit { prefs -> block(prefs) }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LAYOUT = stringPreferencesKey("library_layout")
        val SORT = stringPreferencesKey("library_sort")
        val NETWORK_RULE = stringPreferencesKey("upload_network_rule")
        val CHARGING_ONLY = booleanPreferencesKey("upload_charging_only")
        val MAX_CONCURRENT = intPreferencesKey("upload_max_concurrent")
        val THUMB_STANDARD = booleanPreferencesKey("thumb_quality_standard")
        val THUMB_CAPTURE_MS = longPreferencesKey("thumb_capture_ms")
        val CAPTION_VIDEO_ID = booleanPreferencesKey("caption_video_id")
        val MEDIA_HASHING = booleanPreferencesKey("media_hashing")
        val AUTO_DELETE_COPY = booleanPreferencesKey("auto_delete_copy")
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        themeMode = enumOr(name = this[Keys.THEME], fallback = ThemeMode.SYSTEM),
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: true,
        libraryLayout = enumOr(name = this[Keys.LAYOUT], fallback = LibraryLayout.GRID),
        librarySort = enumOr(name = this[Keys.SORT], fallback = LibrarySort.DATE_ADDED_DESC),
        uploadNetworkRule = enumOr(name = this[Keys.NETWORK_RULE], fallback = UploadNetworkRule.ANY_NETWORK),
        allowUploadsOnlyWhileCharging = this[Keys.CHARGING_ONLY] ?: false,
        maxConcurrentUploads = (this[Keys.MAX_CONCURRENT] ?: 1).coerceIn(1, 4),
        thumbnailQuality = if (this[Keys.THUMB_STANDARD] ?: true) ThumbnailQuality.STANDARD else ThumbnailQuality.HIGH,
        thumbnailCaptureMs = this[Keys.THUMB_CAPTURE_MS] ?: 1000L,
        captionIncludesVideoId = this[Keys.CAPTION_VIDEO_ID] ?: true,
        mediaHashingEnabled = this[Keys.MEDIA_HASHING] ?: true,
        autoDeleteLocalCopyAfterUpload = this[Keys.AUTO_DELETE_COPY] ?: false,
    )

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
