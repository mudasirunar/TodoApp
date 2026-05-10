package com.example.mytodoapp.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

class PreferenceManager(private val context: Context) {

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val AI_REWRITE_TYPE_KEY = stringPreferencesKey("ai_rewrite_type")
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
}
