package com.kaleedtc.nitterium.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(
    context: Context
) {
    private val context: Context = context.applicationContext

    private object PreferencesKeys {
        val INSTANCE_URL = stringPreferencesKey("instance_url")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val TRUE_BLACK = booleanPreferencesKey("true_black")
        val ENABLE_SITE_HEADER = booleanPreferencesKey("enable_site_header")
        val SHOW_NAV_LABELS = booleanPreferencesKey("show_nav_labels")
        val BLOCK_DIRECT_X = booleanPreferencesKey("block_direct_x")
        val CUSTOM_INSTANCES = androidx.datastore.preferences.core.stringSetPreferencesKey("custom_instances")
    }

    val instanceUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.INSTANCE_URL] ?: "https://nitter.net"
        }

    val dynamicColor: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
        }

    val trueBlack: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.TRUE_BLACK] ?: false
        }

    // null means system default, true = dark, false = light
    val darkTheme: Flow<Boolean?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DARK_THEME]
        }

    val enableSiteHeader: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ENABLE_SITE_HEADER] ?: false
        }

    val showNavLabels: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_NAV_LABELS] ?: true
        }

    val blockDirectX: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BLOCK_DIRECT_X] ?: true
        }

    val customInstances: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CUSTOM_INSTANCES] ?: emptySet()
        }

    suspend fun addCustomInstance(url: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.CUSTOM_INSTANCES] ?: emptySet()
            preferences[PreferencesKeys.CUSTOM_INSTANCES] = current + url
        }
    }

    suspend fun removeCustomInstance(url: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.CUSTOM_INSTANCES] ?: emptySet()
            preferences[PreferencesKeys.CUSTOM_INSTANCES] = current - url
        }
    }

    suspend fun setInstanceUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INSTANCE_URL] = url
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setTrueBlack(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRUE_BLACK] = enabled
        }
    }

    suspend fun setDarkTheme(isDark: Boolean?) {
        context.dataStore.edit { preferences ->
            if (isDark == null) {
                preferences.remove(PreferencesKeys.DARK_THEME)
            } else {
                preferences[PreferencesKeys.DARK_THEME] = isDark
            }
        }
    }

    suspend fun setEnableSiteHeader(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_SITE_HEADER] = enabled
        }
    }

    suspend fun setShowNavLabels(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_NAV_LABELS] = enabled
        }
    }

    suspend fun setBlockDirectX(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_DIRECT_X] = enabled
        }
    }
}
