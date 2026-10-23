package com.kaleedtc.nitterium.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kaleedtc.nitterium.data.model.*
import com.kaleedtc.nitterium.ui.feature.settings.*
import com.kaleedtc.nitterium.ui.theme.NitteriumTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val events = mutableListOf<SettingsEvent>()

    @Test fun settingsHasBackupTabWithAllFourScopesAndDocumentActions() {
        compose.setContent { NitteriumTheme { SettingsContent(SettingsState(isLoading = false), events::add) } }
        compose.onNodeWithText("Backups").performClick()
        listOf("Subscriptions only", "Subscriptions and groups", "Settings only", "Everything").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Settings only").performScrollTo().performClick()
        compose.onNodeWithText("Create backup").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SettingsEvent.ExportBackup(BackupScope.SETTINGS), events.last()) }
        compose.onNodeWithText("Restore from file").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SettingsEvent.ImportBackup, events.last()) }
    }

    @Test fun previewRequiresConfirmationAndCancelLeavesDataUntouched() {
        val backup = NitteriumBackup(
            subscriptions = listOf(Subscription("alice", "https://example.com")),
            groups = listOf(FeedGroup("news", "News")), settings = AppSettingsBackup()
        )
        compose.setContent { NitteriumTheme { SettingsContent(SettingsState(isLoading = false, backupToRestore = backup), events::add) } }
        compose.onNodeWithText("1 subscription").assertIsDisplayed()
        compose.onNodeWithText("1 group").assertIsDisplayed()
        compose.onNodeWithText("The saved settings will replace your current app settings. Settings take effect immediately.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsEvent.CancelRestoreBackup), events) }
    }
}
