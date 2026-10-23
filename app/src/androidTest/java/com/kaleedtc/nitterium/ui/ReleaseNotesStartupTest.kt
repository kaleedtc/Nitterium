package com.kaleedtc.nitterium.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.MainViewModel
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.viewModelFactory
import com.kaleedtc.nitterium.ui.feature.whatsnew.WhatsNewScreen
import com.kaleedtc.nitterium.ui.theme.NitteriumTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ReleaseNotesStartupTest {
    @get:Rule val compose = createComposeRule()
    private val job = SupervisorJob()
    private val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "release-notes-${UUID.randomUUID()}.preferences_pb")
    private val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
    private val preferences = UserPreferencesRepository(store)

    @After fun cleanUp() = runBlocking {
        job.cancelAndJoin()
        file.delete()
        Unit
    }

    @Test fun startupWaitsForPreferencesThenShowsEachUpdateUnlessUserOptsOut() {
        val installedVersion = mutableLongStateOf(12)
        compose.setContent {
            val model: MainViewModel = viewModel(
                key = "version-${installedVersion.longValue}",
                factory = viewModelFactory { MainViewModel(preferences, installedVersion.longValue) }
            )
            val state by model.uiState.collectAsStateWithLifecycle()
            NitteriumTheme {
                when {
                    state.isLoading -> Text("Loading preferences")
                    state.showReleaseNotes -> WhatsNewScreen(
                        "test-version", onContinue = model::dismissReleaseNotes, onDonate = {},
                        isSaving = state.isSavingReleaseNotes, saveFailed = state.releaseNotesSaveFailed
                    )
                    else -> Text("App ready")
                }
            }
        }
        awaitText("What’s new")
        compose.onNodeWithText("Continue").performClick()
        awaitText("App ready")

        // A new ViewModel for the same version represents another launch.
        compose.runOnIdle { installedVersion.longValue = 11 }
        awaitText("App ready")
        compose.runOnIdle { installedVersion.longValue = 12 }
        awaitText("App ready")

        compose.runOnIdle { installedVersion.longValue = 13 }
        awaitText("What’s new")
        compose.onNodeWithText("Don’t show this after updates").performClick()
        compose.onNodeWithText("Continue").performClick()
        awaitText("App ready")
        compose.runOnIdle { installedVersion.longValue = 14 }
        awaitText("App ready")
        runBlocking { assertFalse(preferences.releaseNotesPreferences.first().enabled) }
        runBlocking { preferences.setShowReleaseNotes(true) }
        compose.waitForIdle()
        awaitText("App ready")
        compose.runOnIdle { installedVersion.longValue = 15 }
        awaitText("What’s new")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
