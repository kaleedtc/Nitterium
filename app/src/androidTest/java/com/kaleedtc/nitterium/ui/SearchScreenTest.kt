package com.kaleedtc.nitterium.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.feature.search.SearchContent
import com.kaleedtc.nitterium.ui.feature.search.SearchEvent
import com.kaleedtc.nitterium.ui.feature.search.SearchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(SearchState())
    private var submitted: String? = null

    private fun showSearch() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFullScreenMode provides remember { mutableStateOf(false) }) {
                    SearchContent(
                        state = state.value,
                        isDarkTheme = false,
                        snackbarHostState = remember { SnackbarHostState() },
                        onSwitchInstance = {},
                        onEvent = { event ->
                            when (event) {
                                is SearchEvent.UpdateQuery -> state.value = state.value.copy(searchQuery = event.query)
                                is SearchEvent.ToggleSearchBar -> state.value = state.value.copy(isSearchBarActive = event.active)
                                is SearchEvent.PerformSearch -> {
                                    submitted = event.query
                                    state.value = state.value.copy(isSearchBarActive = false)
                                }
                                else -> Unit
                            }
                        }
                    )
                }
            }
        }
    }

    @Test
    fun typingClearingAndSelectingSuggestionStaySynchronized() {
        showSearch()
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("alice")
        compose.runOnIdle {
            assertTrue(state.value.isSearchBarActive)
            assertEquals("alice", state.value.searchQuery)
        }
        compose.onNode(hasContentDescription("Clear") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals("", state.value.searchQuery) }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("@bob")
        compose.onNodeWithText("Search for \"@bob\"").performClick()
        compose.runOnIdle {
            assertEquals("@bob", submitted)
            assertFalse(state.value.isSearchBarActive)
        }
    }

    @Test
    fun systemBackClosesExpansionAndRetainsQueryForReopening() {
        showSearch()
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("saved query")
        // First back dismisses the keyboard; second closes the full-screen search dialog.
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.runOnIdle {
            assertFalse(state.value.isSearchBarActive)
            assertEquals("saved query", state.value.searchQuery)
            state.value = state.value.copy(isSearchBarActive = true)
        }
        compose.onNodeWithText("Search for \"saved query\"").assertExists()
        compose.onNode(hasContentDescription("Back") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertFalse(state.value.isSearchBarActive) }
    }
}
