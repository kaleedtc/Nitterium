package com.kaleedtc.nitterium.data.repository

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/** Retire the old reset without changing a user's instance, privacy preferences or cookies. */
internal class ReleaseDefaultsMigration : DataMigration<Preferences> {
    private val completed = booleanPreferencesKey("xitter_direct_media_defaults_v1_applied")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[completed] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        return currentData.toMutablePreferences().apply {
            this[completed] = true
        }
    }

    override suspend fun cleanUp() = Unit
}
