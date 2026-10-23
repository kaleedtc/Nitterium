package com.kaleedtc.nitterium

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.model.AppSettingsBackup
import com.kaleedtc.nitterium.data.model.BackupCodec
import com.kaleedtc.nitterium.data.model.BackupScope
import com.kaleedtc.nitterium.data.model.NitteriumBackup
import com.kaleedtc.nitterium.data.repository.ReleaseDefaultsMigration
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseDefaultsMigrationTest {
    @Test fun upgradePreservesAllChoicesBeforeTheFirstPreferenceIsRead() = withPreferences {
        val old = AppSettingsBackup(
            instanceUrl = "https://old.example/profile", blockDirectX = true,
            dynamicColor = false, darkTheme = true, trueBlack = true, siteHeader = true,
            navLabels = false, defaultTab = "Subscriptions", showReleaseNotes = false,
            customInstances = setOf("https://custom.example", "not a URL", "https://invalid-port.example:99999"),
            frontendModes = mapOf("https://chosen.example" to FrontendType.TEAPOT),
            detectedFrontends = mapOf("https://detected.example" to FrontendType.NITTER)
        )
        open().apply {
            restoreSettings(old)
            dismissReleaseNotes(12, hideFutureUpdates = true)
        }
        val upgraded = open(listOf(ReleaseDefaultsMigration()))

        assertEquals("https://old.example/profile", upgraded.instanceUrl.first())
        assertEquals(old, upgraded.backupSettings())
        assertEquals(12L, upgraded.releaseNotesPreferences.first().lastSeenVersionCode)
    }

    @Test fun userChangesSurviveReopeningAndFutureVersionAcknowledgments() = withPreferences {
        val migration = ReleaseDefaultsMigration()
        open().restoreSettings(AppSettingsBackup(instanceUrl = "https://old.example", blockDirectX = true))
        val upgraded = open(listOf(migration))
        assertTrue(upgraded.blockDirectX.first())
        upgraded.setInstanceUrl("https://user-choice.example")
        upgraded.setBlockDirectX(true)
        upgraded.dismissReleaseNotes(13, hideFutureUpdates = false)

        val restarted = open(listOf(migration))
        assertEquals("https://user-choice.example", restarted.instanceUrl.first())
        assertTrue(restarted.blockDirectX.first())
        restarted.dismissReleaseNotes(14, hideFutureUpdates = true)
        val laterUpdate = open(listOf(migration))
        assertEquals("https://user-choice.example", laterUpdate.instanceUrl.first())
        assertTrue(laterUpdate.blockDirectX.first())
    }

    @Test fun freshInstallIsMarkedCompleteBeforeCustomization() = withPreferences {
        val migration = ReleaseDefaultsMigration()
        val fresh = open(listOf(migration))
        assertEquals(AppSettingsBackup(), fresh.backupSettings())
        fresh.setInstanceUrl("https://fresh-choice.example")
        fresh.setBlockDirectX(true)

        val reopened = open(listOf(migration))
        assertEquals("https://fresh-choice.example", reopened.instanceUrl.first())
        assertTrue(reopened.blockDirectX.first())
    }

    @Test fun restoringAnOldBackupDoesNotRearmTheMigration() = withPreferences {
        val migration = ReleaseDefaultsMigration()
        val preferences = open(listOf(migration))
        val encoded = BackupCodec.encode(NitteriumBackup(settings = preferences.backupSettings()), BackupScope.SETTINGS)
        assertFalse(encoded.contains("xitter_direct_media_defaults_v1_applied"))

        val old = AppSettingsBackup(instanceUrl = "https://backup-choice.example", blockDirectX = true)
        preferences.restoreSettings(old)
        assertEquals(old, open(listOf(migration)).backupSettings())
    }

    @Test fun retiredResetIsNotRearmedAndDoesNotGuessPreviouslyLostChoices() = runBlocking {
        val migration = ReleaseDefaultsMigration()
        val old = androidx.datastore.preferences.core.mutablePreferencesOf(
            androidx.datastore.preferences.core.stringPreferencesKey("instance_url") to "https://xitter.cf",
            androidx.datastore.preferences.core.booleanPreferencesKey("block_direct_x") to false
        )
        assertTrue(migration.shouldMigrate(old))
        val retired = migration.migrate(old)
        assertFalse(migration.shouldMigrate(retired))
        assertEquals(false, retired[androidx.datastore.preferences.core.booleanPreferencesKey("block_direct_x")])
    }

    private fun withPreferences(test: suspend PreferenceFile.() -> Unit) = runBlocking {
        val fixture = PreferenceFile()
        try {
            fixture.test()
        } finally {
            fixture.close()
        }
    }

    private class PreferenceFile {
        private val directory = Files.createTempDirectory("nitterium-defaults-migration").toFile()
        private var job = SupervisorJob()

        suspend fun open(migrations: List<DataMigration<Preferences>> = emptyList()): UserPreferencesRepository {
            job.cancelAndJoin()
            job = SupervisorJob()
            val store = PreferenceDataStoreFactory.create(
                migrations = migrations, scope = CoroutineScope(job + Dispatchers.IO)
            ) { directory.resolve("settings.preferences_pb") }
            return UserPreferencesRepository(store)
        }

        suspend fun close() {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
