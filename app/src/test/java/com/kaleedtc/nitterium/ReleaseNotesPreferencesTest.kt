package com.kaleedtc.nitterium

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kaleedtc.nitterium.data.model.AppSettingsBackup
import com.kaleedtc.nitterium.data.model.BackupCodec
import com.kaleedtc.nitterium.data.model.BackupScope
import com.kaleedtc.nitterium.data.model.NitteriumBackup
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

class ReleaseNotesPreferencesTest {
    @Test fun firstInstallAndEachNewVersionShowOnce() = withPreferences { preferences ->
        assertTrue(preferences.releaseNotesPreferences.first().shouldShow(12))
        preferences.dismissReleaseNotes(12, hideFutureUpdates = false)
        val saved = preferences.releaseNotesPreferences.first()
        assertFalse(saved.shouldShow(12))
        assertTrue(saved.shouldShow(13))
        assertEquals(12L, saved.lastSeenVersionCode)
    }

    @Test fun optOutPersistsAndCanBeEnabledAgain() = withPreferences { preferences ->
        preferences.dismissReleaseNotes(12, hideFutureUpdates = true)
        assertFalse(preferences.releaseNotesPreferences.first().shouldShow(13))
        assertFalse(preferences.backupSettings().showReleaseNotes)
        preferences.setShowReleaseNotes(true)
        val enabled = preferences.releaseNotesPreferences.first()
        assertFalse(enabled.shouldShow(12))
        assertTrue(enabled.shouldShow(13))
    }

    @Test fun olderVersionsCannotRollBackTheAcknowledgedVersion() = withPreferences { preferences ->
        preferences.dismissReleaseNotes(13, hideFutureUpdates = false)
        preferences.dismissReleaseNotes(12, hideFutureUpdates = false)
        assertEquals(13L, preferences.releaseNotesPreferences.first().lastSeenVersionCode)
        assertFalse(preferences.releaseNotesPreferences.first().shouldShow(13))
    }

    @Test fun settingsBackupIncludesOptOutButKeepsDeviceVersionHistoryLocal() = withPreferences { preferences ->
        preferences.dismissReleaseNotes(12, hideFutureUpdates = true)
        val encoded = BackupCodec.encode(NitteriumBackup(settings = preferences.backupSettings()), BackupScope.SETTINGS)
        val restored = BackupCodec.decode(encoded, "https://example.com").settings!!
        assertFalse(restored.showReleaseNotes)
        preferences.restoreSettings(AppSettingsBackup(showReleaseNotes = true))
        assertTrue(preferences.releaseNotesPreferences.first().enabled)
        assertEquals(12L, preferences.releaseNotesPreferences.first().lastSeenVersionCode)
        preferences.restoreSettings(restored)
        assertFalse(preferences.releaseNotesPreferences.first().enabled)
    }

    @Test fun backupsFromBeforeThisFeatureKeepReleaseNotesEnabled() {
        val oldBackup = """{"format":"nitterium-backup","version":1,"settings":{}}"""
        assertTrue(BackupCodec.decode(oldBackup, "https://example.com").settings!!.showReleaseNotes)
    }

    private fun withPreferences(test: suspend (UserPreferencesRepository) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("nitterium-release-notes").toFile()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val file = directory.resolve("settings.preferences_pb")
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            test(UserPreferencesRepository(store))
            val saved = UserPreferencesRepository(store).releaseNotesPreferences.first()
            job.cancelAndJoin()
            val reopenedJob = SupervisorJob()
            try {
                val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(reopenedJob + Dispatchers.IO)) { file }
                assertEquals(saved, UserPreferencesRepository(reopened).releaseNotesPreferences.first())
            } finally {
                reopenedJob.cancelAndJoin()
            }
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
