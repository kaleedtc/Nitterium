package com.kaleedtc.nitterium.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.model.SubscriptionSortOrder
import com.kaleedtc.nitterium.ui.feature.subscriptions.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SubscriptionsSearchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuSortsWithoutReplacingManualOrderAndKeepsFilteredActionsCorrect() {
        val state = mutableStateOf(SubscriptionsState(subscriptions = listOf(
            Subscription("charlie", "https://example.com", addedAt = 20),
            Subscription("alice", "https://example.com", addedAt = 10),
            Subscription("Bob", "https://example.com", addedAt = 30)
        )))
        var opened: String? = null
        compose.setContent {
            MaterialTheme {
                SubscriptionsContent(state.value, { event ->
                    state.value = when (event) {
                        is SubscriptionsEvent.SortOrderChanged -> state.value.copy(sortOrder = event.order)
                        is SubscriptionsEvent.SearchQueryChanged -> state.value.copy(searchQuery = event.query)
                        else -> state.value
                    }
                }, { opened = it }, remember { SnackbarHostState() })
            }
        }

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Import").assertDoesNotExist()
        compose.onNodeWithText("Export").assertDoesNotExist()
        compose.onNodeWithText("Manual order").assertIsSelected()
        compose.onNodeWithText("Sort by username (A–Z)").performClick()
        assertOrder("alice", "Bob", "charlie")

        compose.onNode(hasSetTextAction()).performTextInput("@BO")
        compose.onNodeWithText("@alice").assertDoesNotExist()
        compose.onNodeWithText("@Bob").performClick()
        compose.runOnIdle { assertEquals("Bob", opened) }
        compose.onNodeWithContentDescription("Clear search").performClick()

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Sort by username (A–Z)").assertIsSelected()
        compose.onNodeWithText("Sort by subscription date (newest first)").performClick()
        assertOrder("Bob", "charlie", "alice")
        compose.runOnIdle {
            state.value = state.value.copy(subscriptions = state.value.subscriptions +
                Subscription("dave", "https://example.com", addedAt = 40))
        }
        assertOrder("dave", "Bob", "charlie", "alice")

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Manual order").performClick()
        assertOrder("charlie", "alice", "Bob", "dave")
        compose.runOnIdle { assertEquals(SubscriptionSortOrder.MANUAL, state.value.sortOrder) }
    }

    private fun assertOrder(vararg usernames: String) {
        // Let LazyColumn's item-placement animation settle before comparing visible positions.
        compose.waitForIdle()
        val positions = usernames.map { compose.onNodeWithText("@$it").fetchSemanticsNode().boundsInRoot.top }
        assertTrue("Expected order: ${usernames.toList()}, positions: $positions", positions.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun filtersLocallyAndKeepsActionsBoundToTheVisibleAccount() {
        val state = mutableStateOf(SubscriptionsState(subscriptions = listOf(
            Subscription("alice", "https://example.com"), Subscription("bob", "https://example.com")
        )))
        var opened: String? = null
        compose.setContent {
            MaterialTheme {
                SubscriptionsContent(state.value, { event ->
                    if (event is SubscriptionsEvent.SearchQueryChanged) state.value = state.value.copy(searchQuery = event.query)
                }, { opened = it }, remember { SnackbarHostState() })
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("@BO")
        compose.onNodeWithText("@alice").assertDoesNotExist()
        compose.onNodeWithText("@bob").performClick()
        compose.runOnIdle { assertEquals("bob", opened) }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("@alice").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("unknown")
        compose.onNodeWithText("No matching subscriptions").assertExists()
    }
}
