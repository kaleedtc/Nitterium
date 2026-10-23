package com.kaleedtc.nitterium.ui

import android.webkit.CookieManager
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.MainViewModel
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.model.AppSettingsBackup
import com.kaleedtc.nitterium.data.repository.CookieInstanceOptionsStore
import com.kaleedtc.nitterium.data.repository.ReleaseDefaultsMigration
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.viewModelFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReleaseDefaultsStartupTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "defaults-${UUID.randomUUID()}.preferences_pb")
    private var job = SupervisorJob()

    @After fun cleanUp() = runBlocking {
        job.cancelAndJoin()
        file.delete()
        Unit
    }

    @Test fun startupWaitsForMigrationEvenWhenReleaseNotesAreDisabledAndFutureVersionsKeepChoices() = runBlocking {
        val legacy = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
        UserPreferencesRepository(legacy).restoreSettings(AppSettingsBackup(
            instanceUrl = "https://old.example", blockDirectX = true, showReleaseNotes = false,
            defaultTab = "Subscriptions", darkTheme = true
        ))
        job.cancelAndJoin()
        job = SupervisorJob()
        val migrationStarted = CompletableDeferred<Unit>()
        val allowMigration = CompletableDeferred<Unit>()
        var migrations = 0
        val retirement = ReleaseDefaultsMigration()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            migrations = listOf(object : androidx.datastore.core.DataMigration<androidx.datastore.preferences.core.Preferences> {
                override suspend fun shouldMigrate(currentData: androidx.datastore.preferences.core.Preferences) = retirement.shouldMigrate(currentData)
                override suspend fun migrate(currentData: androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
                    migrations++
                    migrationStarted.complete(Unit)
                    allowMigration.await()
                    return retirement.migrate(currentData)
                }
                override suspend fun cleanUp() = Unit
            })
        ) { file }
        val preferences = UserPreferencesRepository(store)
        val version = mutableLongStateOf(13)
        compose.setContent {
            val model: MainViewModel = viewModel(
                key = "version-${version.longValue}",
                factory = viewModelFactory { MainViewModel(preferences, version.longValue) }
            )
            val state by model.uiState.collectAsStateWithLifecycle()
            when {
                state.isLoading -> Text("Loading preferences")
                state.showReleaseNotes -> Text("What’s new")
                else -> Text("App ready: ${state.defaultTab}")
            }
        }
        migrationStarted.await()
        awaitText("Loading preferences")
        compose.onNodeWithText("App ready: Subscriptions").assertDoesNotExist()
        allowMigration.complete(Unit)
        awaitText("App ready: Subscriptions")
        assertEquals("https://old.example", preferences.instanceUrl.first())
        assertTrue(preferences.blockDirectX.first())
        assertEquals(true, preferences.darkTheme.first())
        preferences.setInstanceUrl("https://user-choice.example")
        preferences.setBlockDirectX(true)
        compose.runOnIdle { version.longValue = 14 }
        awaitText("App ready: Subscriptions")
        assertEquals("https://user-choice.example", preferences.instanceUrl.first())
        assertTrue(preferences.blockDirectX.first())
        assertEquals(1, migrations)
    }

    @Test fun migrationAndInitializationPreserveExistingProxyCookies() = runBlocking {
        val origins = setOf("https://proxy-reset-${UUID.randomUUID()}.test", "https://custom-proxy-reset-${UUID.randomUUID()}.test")
        val options = CookieInstanceOptionsStore()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (origin in origins) {
                options.setOption(origin, FrontendType.NITTER, "proxyVideos", true)
                options.setOption(origin, FrontendType.TEAPOT, "proxyVideos", true)
                options.setOption(origin, FrontendType.TEAPOT, "hlsPlayback", false)
                options.setOption(origin, FrontendType.TEAPOT, "squareAvatars", false)
                CookieManager.getInstance().setCookie(origin, "unrelated=retained; Path=/")
            }
        }

        ReleaseDefaultsMigration().migrate(androidx.datastore.preferences.core.emptyPreferences())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (origin in origins) {
                assertTrue(options.read(origin, FrontendType.NITTER).proxyVideos)
                assertTrue(options.read(origin, FrontendType.TEAPOT).proxyVideos)
                assertFalse(options.read(origin, FrontendType.TEAPOT).hlsPlayback)
                assertEquals(false, options.read(origin, FrontendType.TEAPOT).squareAvatars)
                assertTrue(CookieManager.getInstance().getCookie(origin).contains("unrelated=retained"))
                options.setOption(origin, FrontendType.NITTER, "proxyVideos", true)
                options.setOption(origin, FrontendType.TEAPOT, "proxyVideos", true)
                options.ensureProxyDefaults(origin)
                options.ensureDefaults(origin, FrontendType.NITTER)
                options.ensureDefaults(origin, FrontendType.TEAPOT)
                assertTrue(CookieInstanceOptionsStore().read(origin, FrontendType.NITTER).proxyVideos)
                assertTrue(CookieInstanceOptionsStore().read(origin, FrontendType.TEAPOT).proxyVideos)
            }
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
