package com.example.mytodoapp.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mytodoapp.components.RewriteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class PdfConfig(
    val includeStatus: Boolean = true,
    val includeFavorites: Boolean = true
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

class PreferenceManager(private val context: Context) {

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val AI_REWRITE_TYPE_KEY = stringPreferencesKey("ai_rewrite_type")
        val PDF_INCLUDE_STATUS_KEY = booleanPreferencesKey("pdf_include_status")
        val PDF_INCLUDE_FAVORITES_KEY = booleanPreferencesKey("pdf_include_favorites")
        val MOVE_DONE_TO_BOTTOM_KEY = booleanPreferencesKey("move_done_to_bottom")
    }

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
            includeFavorites = preferences[PDF_INCLUDE_FAVORITES_KEY] ?: true
        )
    }

    val moveDoneToBottom: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MOVE_DONE_TO_BOTTOM_KEY] ?: false
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun saveAiRewriteType(type: RewriteType) {
        context.dataStore.edit { preferences ->
            preferences[AI_REWRITE_TYPE_KEY] = type.name
        }
    }

    suspend fun savePdfConfig(config: PdfConfig) {
        context.dataStore.edit { preferences ->
            preferences[PDF_INCLUDE_STATUS_KEY] = config.includeStatus
            preferences[PDF_INCLUDE_FAVORITES_KEY] = config.includeFavorites
        }
    }

    suspend fun saveMoveDoneToBottom(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MOVE_DONE_TO_BOTTOM_KEY] = value
        }
    }
}
