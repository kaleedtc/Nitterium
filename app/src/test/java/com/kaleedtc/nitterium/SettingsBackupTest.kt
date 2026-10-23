package com.kaleedtc.nitterium

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.model.AppSettingsBackup
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {
    @Test fun directXBlockingDefaultsOffAcrossInstancesAndKeepsUserChoice() = runBlocking {
        val directory = Files.createTempDirectory("nitterium-default-settings").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { directory.resolve("defaults.preferences_pb") }
            val preferences = UserPreferencesRepository(store)
            assertFalse(preferences.blockDirectX.first())
            assertFalse(preferences.backupSettings().blockDirectX)
            preferences.addCustomInstance("https://new.example")
            preferences.setInstanceUrl("https://new.example")
            assertFalse(preferences.blockDirectX.first())
            preferences.setBlockDirectX(true)
            preferences.setInstanceUrl("https://another.example")
            assertTrue(UserPreferencesRepository(store).blockDirectX.first())
            assertTrue(preferences.backupSettings().blockDirectX)
            preferences.setBlockDirectX(false)
            assertFalse(UserPreferencesRepository(store).blockDirectX.first())
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test fun restoringSettingsReplacesPreferencesAndRemovesStaleFrontendChoices() = runBlocking {
        val directory = Files.createTempDirectory("nitterium-settings-backup").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { directory.resolve("backup.preferences_pb") }
            val preferences = UserPreferencesRepository(store)
            preferences.setInstanceUrl("https://old.example")
            preferences.setFrontend(FrontendType.NITTER)
            preferences.setDarkTheme(true)
            val saved = AppSettingsBackup(
                instanceUrl = "https://new.example", dynamicColor = false, darkTheme = null, trueBlack = true,
                siteHeader = true, navLabels = false, defaultTab = "Subscriptions", blockDirectX = false,
                customInstances = setOf("https://new.example"),
                frontendModes = mapOf("https://new.example" to FrontendType.TEAPOT),
                detectedFrontends = mapOf("https://auto.example" to FrontendType.NITTER)
            )
            preferences.restoreSettings(saved)
            assertEquals(saved, preferences.backupSettings())
            preferences.restoreSettings(AppSettingsBackup())
            assertEquals(AppSettingsBackup(), preferences.backupSettings())
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }
}
