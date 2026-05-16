package com.example.mytodoapp.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mytodoapp.components.RewriteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.example.mytodoapp.sync.SyncState
import java.util.UUID

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class PdfConfig(
    val includeStatus: Boolean = true,
    val includeFavorites: Boolean = true,
    val includeSummary: Boolean = true
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

class PreferenceManager(private val context: Context) {

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val MOVE_DONE_TO_BOTTOM_KEY = booleanPreferencesKey("move_done_to_bottom")
        val FORCE_REMOTE_SETTINGS_KEY = booleanPreferencesKey("force_remote_settings")
        val AI_REWRITE_TYPE_KEY = stringPreferencesKey("ai_rewrite_type")
        val PDF_INCLUDE_STATUS_KEY = booleanPreferencesKey("pdf_include_status")
        val PDF_INCLUDE_FAVORITES_KEY = booleanPreferencesKey("pdf_include_favorites")
        val PDF_INCLUDE_SUMMARY_KEY = booleanPreferencesKey("pdf_include_summary")
        
        val LAST_BACKUP_TIME_KEY = longPreferencesKey("last_backup_time")
        val LAST_BACKUP_SOURCE_KEY = stringPreferencesKey("last_backup_source")
        
        val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        
        val SETTINGS_UPDATED_AT_KEY = longPreferencesKey("settings_updated_at")
        val SETTINGS_SYNC_STATE_KEY = stringPreferencesKey("settings_sync_state")
        
        val HAS_MIGRATED_TO_CLOUD_KEY = booleanPreferencesKey("has_migrated_to_cloud")
    }

    val hasMigratedToCloud: Flow<Boolean> = context.dataStore.data.map { it[HAS_MIGRATED_TO_CLOUD_KEY] ?: false }

    val settingsUpdatedAt: Flow<Long> = context.dataStore.data.map { it[SETTINGS_UPDATED_AT_KEY] ?: 0L }
    val settingsSyncState: Flow<String> = context.dataStore.data.map { it[SETTINGS_SYNC_STATE_KEY] ?: "SYNCED" }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val themeName = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(themeName)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val aiRewriteType: Flow<RewriteType> = context.dataStore.data.map { preferences ->
        val typeName = preferences[AI_REWRITE_TYPE_KEY] ?: RewriteType.Standard.name
        try {
            RewriteType.valueOf(typeName)
        } catch (e: Exception) {
            RewriteType.Standard
        }
    }

    val pdfConfig: Flow<PdfConfig> = context.dataStore.data.map { preferences ->
        PdfConfig(
            includeStatus = preferences[PDF_INCLUDE_STATUS_KEY] ?: true,
            includeFavorites = preferences[PDF_INCLUDE_FAVORITES_KEY] ?: true,
            includeSummary = preferences[PDF_INCLUDE_SUMMARY_KEY] ?: true
        )
    }

    val moveDoneToBottom: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MOVE_DONE_TO_BOTTOM_KEY] ?: false
    }

    val lastBackupTime: Flow<Long> = context.dataStore.data.map { it[LAST_BACKUP_TIME_KEY] ?: 0L }
    val lastBackupSource: Flow<String?> = context.dataStore.data.map { it[LAST_BACKUP_SOURCE_KEY] }
    
    val deviceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEVICE_ID_KEY] ?: ""
    }

    suspend fun getOrCreateDeviceId(): String {
        var currentId = context.dataStore.data.first()[DEVICE_ID_KEY]
        if (currentId.isNullOrEmpty()) {
            currentId = UUID.randomUUID().toString()
            context.dataStore.edit { preferences ->
                preferences[DEVICE_ID_KEY] = currentId
            }
        }
        return currentId
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
            preferences[SETTINGS_UPDATED_AT_KEY] = System.currentTimeMillis()
            preferences[SETTINGS_SYNC_STATE_KEY] = com.example.mytodoapp.sync.SyncState.PENDING.name
        }
    }

    suspend fun saveAiRewriteType(type: RewriteType) {
        context.dataStore.edit { preferences ->
            preferences[AI_REWRITE_TYPE_KEY] = type.name
            preferences[SETTINGS_UPDATED_AT_KEY] = System.currentTimeMillis()
            preferences[SETTINGS_SYNC_STATE_KEY] = com.example.mytodoapp.sync.SyncState.PENDING.name
        }
    }

    suspend fun savePdfConfig(config: PdfConfig) {
        context.dataStore.edit { preferences ->
            preferences[PDF_INCLUDE_STATUS_KEY] = config.includeStatus
            preferences[PDF_INCLUDE_FAVORITES_KEY] = config.includeFavorites
            preferences[PDF_INCLUDE_SUMMARY_KEY] = config.includeSummary
            preferences[SETTINGS_UPDATED_AT_KEY] = System.currentTimeMillis()
            preferences[SETTINGS_SYNC_STATE_KEY] = com.example.mytodoapp.sync.SyncState.PENDING.name
        }
    }

    suspend fun saveMoveDoneToBottom(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MOVE_DONE_TO_BOTTOM_KEY] = value
            preferences[SETTINGS_UPDATED_AT_KEY] = System.currentTimeMillis()
            preferences[SETTINGS_SYNC_STATE_KEY] = com.example.mytodoapp.sync.SyncState.PENDING.name
        }
    }

    suspend fun saveLastBackupInfo(time: Long, source: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_BACKUP_TIME_KEY] = time
            preferences[LAST_BACKUP_SOURCE_KEY] = source
        }
    }

    val forceRemoteSettings: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FORCE_REMOTE_SETTINGS_KEY] ?: false
    }

    suspend fun setForceRemoteSettings(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FORCE_REMOTE_SETTINGS_KEY] = value
        }
    }

    suspend fun markSettingsSynced() {
        context.dataStore.edit { preferences ->
            preferences[SETTINGS_SYNC_STATE_KEY] = com.example.mytodoapp.sync.SyncState.SYNCED.name
        }
    }

    suspend fun markSettingsPending() {
        context.dataStore.edit { preferences ->
            preferences[SETTINGS_SYNC_STATE_KEY] = com.example.mytodoapp.sync.SyncState.PENDING.name
        }
    }

    suspend fun markMigratedToCloud() {
        context.dataStore.edit { preferences ->
            preferences[HAS_MIGRATED_TO_CLOUD_KEY] = true
        }
    }

    suspend fun applyRemoteSettings(
        themeMode: ThemeMode?, 
        aiRewriteType: RewriteType?, 
        pdfConfig: PdfConfig?, 
        moveDoneToBottom: Boolean?, 
        updatedAt: Long
    ) {
        context.dataStore.edit { preferences ->
            if (themeMode != null) preferences[THEME_MODE_KEY] = themeMode.name
            if (aiRewriteType != null) preferences[AI_REWRITE_TYPE_KEY] = aiRewriteType.name
            if (pdfConfig != null) {
                preferences[PDF_INCLUDE_STATUS_KEY] = pdfConfig.includeStatus
                preferences[PDF_INCLUDE_FAVORITES_KEY] = pdfConfig.includeFavorites
                preferences[PDF_INCLUDE_SUMMARY_KEY] = pdfConfig.includeSummary
            }
            if (moveDoneToBottom != null) preferences[MOVE_DONE_TO_BOTTOM_KEY] = moveDoneToBottom
            
            preferences[SETTINGS_UPDATED_AT_KEY] = updatedAt
            preferences[SETTINGS_SYNC_STATE_KEY] = SyncState.SYNCED.name
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
