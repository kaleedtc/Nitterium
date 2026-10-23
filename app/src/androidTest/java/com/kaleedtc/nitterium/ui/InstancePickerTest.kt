package com.kaleedtc.nitterium.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.InstancePicker
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.feature.feed.FeedContent
import com.kaleedtc.nitterium.ui.feature.feed.FeedState
import com.kaleedtc.nitterium.ui.feature.profile.ProfileContent
import com.kaleedtc.nitterium.ui.feature.profile.ProfileState
import com.kaleedtc.nitterium.ui.feature.search.SearchContent
import com.kaleedtc.nitterium.ui.feature.search.SearchState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InstancePickerTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "instance-picker-${UUID.randomUUID()}").apply { mkdirs() }
    private val job = SupervisorJob()
    private val preferences = UserPreferencesRepository(PreferenceDataStoreFactory.create(
        scope = CoroutineScope(job + Dispatchers.IO)
    ) { directory.resolve("settings.preferences_pb") })

    @After fun close() = runBlocking {
        job.cancelAndJoin()
        directory.deleteRecursively()
        Unit
    }

    @Test fun builtInAndLaterCustomInstancesCanBeSelectedWithoutOpeningSettings() = runBlocking {
        val builtIn = context.resources.getStringArray(R.array.default_instance_urls).first()
        var dismissed = false
        compose.setContent { MaterialTheme { InstancePicker(preferences) { dismissed = true } } }
        compose.onNodeWithText(builtIn).assertExists()
        // Add an instance after the chooser has opened; its list must update from DataStore.
        val later = "https://later-added.test"
        preferences.addCustomInstance(later)
        compose.waitForIdle()
        compose.onNodeWithTag("instance_choices").performScrollToNode(hasText(later))
        compose.onNodeWithText(later).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { dismissed }
        assertEquals(later, preferences.instanceUrl.first())
    }

    @Test fun cancelKeepsTheSelectedInstance() = runBlocking {
        val selected = "https://selected.test"
        preferences.setInstanceUrl(selected)
        var dismissed = false
        compose.setContent { MaterialTheme { InstancePicker(preferences) { dismissed = true } } }
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle { assertTrue(dismissed) }
        assertEquals(selected, preferences.instanceUrl.first())
    }

    @Test fun allBrowsingScreensKeepTheServerMessageAndOfferInstanceSelection() {
        val screen = mutableStateOf(0)
        val message = "Instance has been rate limited.\nUse another instance or try again later."
        val url = "https://unavailable.test/alice"
        var switchRequests = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFullScreenMode provides remember { mutableStateOf(false) }) {
                    val snackbar = remember { SnackbarHostState() }
                    val switch = { switchRequests++; Unit }
                    when (screen.value) {
                        0 -> SearchContent(SearchState(currentUrl = url, isError = true, errorMessage = message),
                            false, {}, snackbar, switch)
                        1 -> ProfileContent(ProfileState(username = "alice", currentUrl = url, isError = true,
                            isLoading = false, errorMessage = message), false, {}, snackbar, {}, switch)
                        else -> FeedContent(FeedState(currentUrl = url, isError = true, errorMessage = message),
                            false, {}, snackbar, switch, {})
                    }
                }
            }
        }
        for (index in 0..2) {
            compose.runOnIdle { screen.value = index }
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.switch_instance)).performClick()
            compose.runOnIdle { assertEquals(index + 1, switchRequests) }
        }
    }
}
