package com.kaleedtc.nitterium.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.model.AppSettingsBackup
import com.kaleedtc.nitterium.data.model.ReleaseNotesPreferences
import com.kaleedtc.nitterium.data.model.SubscriptionSortOrder

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    produceMigrations = { listOf(ReleaseDefaultsMigration()) }
)

class UserPreferencesRepository(
    private val store: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    internal object PreferencesKeys {
        val INSTANCE_URL = stringPreferencesKey("instance_url")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val TRUE_BLACK = booleanPreferencesKey("true_black")
        val ENABLE_SITE_HEADER = booleanPreferencesKey("enable_site_header")
        val SHOW_NAV_LABELS = booleanPreferencesKey("show_nav_labels")
        val DEFAULT_TAB = stringPreferencesKey("default_tab")
        val BLOCK_DIRECT_X = booleanPreferencesKey("block_direct_x")
        val CUSTOM_INSTANCES = androidx.datastore.preferences.core.stringSetPreferencesKey("custom_instances")
        val SHOW_RELEASE_NOTES = booleanPreferencesKey("show_release_notes")
        val LAST_SEEN_VERSION_CODE = longPreferencesKey("last_seen_version_code")
        val SUBSCRIPTION_SORT_ORDER = stringPreferencesKey("subscription_sort_order")
    }

    val releaseNotesPreferences: Flow<ReleaseNotesPreferences> = store.data.map { preferences ->
        ReleaseNotesPreferences(
            enabled = preferences[PreferencesKeys.SHOW_RELEASE_NOTES] ?: true,
            lastSeenVersionCode = preferences[PreferencesKeys.LAST_SEEN_VERSION_CODE] ?: 0
        )
    }.distinctUntilChanged()

    suspend fun dismissReleaseNotes(versionCode: Long, hideFutureUpdates: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.LAST_SEEN_VERSION_CODE] = maxOf(
                preferences[PreferencesKeys.LAST_SEEN_VERSION_CODE] ?: 0,
                versionCode
            )
            preferences[PreferencesKeys.SHOW_RELEASE_NOTES] = !hideFutureUpdates
        }
    }

    suspend fun setShowReleaseNotes(enabled: Boolean) {
        store.edit { it[PreferencesKeys.SHOW_RELEASE_NOTES] = enabled }
    }

    val instanceUrl: Flow<String> = store.data
        .map { preferences ->
            preferences[PreferencesKeys.INSTANCE_URL] ?: "https://xitter.cf"
        }.distinctUntilChanged()

    private fun frontendKey(url: String, detected: Boolean = false) = stringPreferencesKey(
        "${if (detected) "detected_frontend" else "frontend"}:${url.trimEnd('/')}"
    )

    val frontendMode: Flow<FrontendType> = store.data.map { preferences ->
        val url = preferences[PreferencesKeys.INSTANCE_URL] ?: "https://xitter.cf"
        runCatching { FrontendType.valueOf(preferences[frontendKey(url)] ?: "AUTO") }.getOrDefault(FrontendType.AUTO)
    }

    val frontend: Flow<FrontendType> = store.data.map { preferences ->
        val url = preferences[PreferencesKeys.INSTANCE_URL] ?: "https://xitter.cf"
        val mode = preferences[frontendKey(url)] ?: "AUTO"
        val type = if (mode == "AUTO") preferences[frontendKey(url, true)] ?: "NITTER" else mode
        runCatching { FrontendType.valueOf(type) }.getOrDefault(FrontendType.NITTER)
    }

    suspend fun setFrontend(type: FrontendType) {
        store.edit { preferences ->
            val url = preferences[PreferencesKeys.INSTANCE_URL] ?: "https://xitter.cf"
            preferences[frontendKey(url)] = type.name
        }
    }

    suspend fun recordDetectedFrontend(url: String, type: FrontendType) {
        store.edit { preferences ->
            val selected = preferences[PreferencesKeys.INSTANCE_URL] ?: "https://xitter.cf"
            if (com.kaleedtc.nitterium.data.frontend.FrontendAdapter.sameOrigin(selected, url)) {
                val key = frontendKey(selected, true)
                if (preferences[key] != type.name) preferences[key] = type.name
            }
        }
    }

    val dynamicColor: Flow<Boolean> = store.data
        .map { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
        }

    val trueBlack: Flow<Boolean> = store.data
        .map { preferences ->
            preferences[PreferencesKeys.TRUE_BLACK] ?: false
        }

    // null means system default, true = dark, false = light
    val darkTheme: Flow<Boolean?> = store.data
        .map { preferences ->
            preferences[PreferencesKeys.DARK_THEME]
        }

    val enableSiteHeader: Flow<Boolean> = store.data
        .map { preferences ->
            preferences[PreferencesKeys.ENABLE_SITE_HEADER] ?: false
        }

    val showNavLabels: Flow<Boolean> = store.data
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_NAV_LABELS] ?: true
        }

    val defaultTab: Flow<String> = store.data
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_TAB] ?: "Search"
        }

    val blockDirectX: Flow<Boolean> = store.data
        .map { preferences ->
            preferences[PreferencesKeys.BLOCK_DIRECT_X] ?: false
        }

    val customInstances: Flow<Set<String>> = store.data
        .map { preferences ->
            preferences[PreferencesKeys.CUSTOM_INSTANCES] ?: emptySet()
        }

    val subscriptionSortOrder: Flow<SubscriptionSortOrder> = store.data.map { preferences ->
        SubscriptionSortOrder.fromPreference(preferences[PreferencesKeys.SUBSCRIPTION_SORT_ORDER])
    }.distinctUntilChanged()

    suspend fun setSubscriptionSortOrder(order: SubscriptionSortOrder) {
        store.edit { it[PreferencesKeys.SUBSCRIPTION_SORT_ORDER] = order.name }
    }

    suspend fun addCustomInstance(url: String) {
        store.edit { preferences ->
            val current = preferences[PreferencesKeys.CUSTOM_INSTANCES] ?: emptySet()
            preferences[PreferencesKeys.CUSTOM_INSTANCES] = current + url
        }
    }

    suspend fun removeCustomInstance(url: String) {
        store.edit { preferences ->
            val current = preferences[PreferencesKeys.CUSTOM_INSTANCES] ?: emptySet()
            preferences[PreferencesKeys.CUSTOM_INSTANCES] = current - url
        }
    }

    suspend fun setInstanceUrl(url: String) {
        store.edit { preferences ->
            preferences[PreferencesKeys.INSTANCE_URL] = url
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setTrueBlack(enabled: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.TRUE_BLACK] = enabled
        }
    }

    suspend fun setDarkTheme(isDark: Boolean?) {
        store.edit { preferences ->
            if (isDark == null) {
                preferences.remove(PreferencesKeys.DARK_THEME)
            } else {
                preferences[PreferencesKeys.DARK_THEME] = isDark
            }
        }
    }

    suspend fun setEnableSiteHeader(enabled: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_SITE_HEADER] = enabled
        }
    }

    suspend fun setShowNavLabels(enabled: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.SHOW_NAV_LABELS] = enabled
        }
    }

    suspend fun setDefaultTab(tab: String) {
        store.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_TAB] = tab
        }
    }

    suspend fun setBlockDirectX(enabled: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_DIRECT_X] = enabled
        }
    }

    suspend fun backupSettings(): AppSettingsBackup {
        val preferences = store.data.first()
        fun frontends(prefix: String) = preferences.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(prefix)) null
            else runCatching { key.name.removePrefix(prefix) to FrontendType.valueOf(value as String) }.getOrNull()
        }.toMap()
        return AppSettingsBackup(
            instanceUrl = preferences[PreferencesKeys.INSTANCE_URL] ?: "https://xitter.cf",
            dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true,
            darkTheme = preferences[PreferencesKeys.DARK_THEME],
            trueBlack = preferences[PreferencesKeys.TRUE_BLACK] ?: false,
            siteHeader = preferences[PreferencesKeys.ENABLE_SITE_HEADER] ?: false,
            navLabels = preferences[PreferencesKeys.SHOW_NAV_LABELS] ?: true,
            defaultTab = preferences[PreferencesKeys.DEFAULT_TAB] ?: "Search",
            blockDirectX = preferences[PreferencesKeys.BLOCK_DIRECT_X] ?: false,
            customInstances = preferences[PreferencesKeys.CUSTOM_INSTANCES] ?: emptySet(),
            frontendModes = frontends("frontend:"),
            detectedFrontends = frontends("detected_frontend:"),
            showReleaseNotes = preferences[PreferencesKeys.SHOW_RELEASE_NOTES] ?: true,
            subscriptionSortOrder = SubscriptionSortOrder.fromPreference(preferences[PreferencesKeys.SUBSCRIPTION_SORT_ORDER])
        )
    }

    suspend fun restoreSettings(settings: AppSettingsBackup) {
        store.edit { preferences ->
            preferences[PreferencesKeys.INSTANCE_URL] = settings.instanceUrl
            preferences[PreferencesKeys.DYNAMIC_COLOR] = settings.dynamicColor
            if (settings.darkTheme == null) preferences.remove(PreferencesKeys.DARK_THEME)
            else preferences[PreferencesKeys.DARK_THEME] = settings.darkTheme
            preferences[PreferencesKeys.TRUE_BLACK] = settings.trueBlack
            preferences[PreferencesKeys.ENABLE_SITE_HEADER] = settings.siteHeader
            preferences[PreferencesKeys.SHOW_NAV_LABELS] = settings.navLabels
            preferences[PreferencesKeys.DEFAULT_TAB] = settings.defaultTab
            preferences[PreferencesKeys.BLOCK_DIRECT_X] = settings.blockDirectX
            preferences[PreferencesKeys.CUSTOM_INSTANCES] = settings.customInstances
            preferences[PreferencesKeys.SHOW_RELEASE_NOTES] = settings.showReleaseNotes
            preferences[PreferencesKeys.SUBSCRIPTION_SORT_ORDER] = settings.subscriptionSortOrder.name
            preferences.asMap().keys.filter {
                it.name.startsWith("frontend:") || it.name.startsWith("detected_frontend:")
            }.forEach { preferences.remove(stringPreferencesKey(it.name)) }
            settings.frontendModes.forEach { (url, type) -> preferences[frontendKey(url)] = type.name }
            settings.detectedFrontends.forEach { (url, type) -> preferences[frontendKey(url, true)] = type.name }
        }
    }
}
