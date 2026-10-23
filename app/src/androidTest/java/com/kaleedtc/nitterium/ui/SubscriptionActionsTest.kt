package com.kaleedtc.nitterium.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.kaleedtc.nitterium.data.model.FeedGroup
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.ui.common.GroupAssignmentDialog
import com.kaleedtc.nitterium.ui.common.SubscriptionActionButton
import com.kaleedtc.nitterium.ui.feature.subscriptions.SubscriptionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SubscriptionActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longPressOpensGroupsWithoutTogglingSubscription() {
        var taps = 0
        var groupActions = 0
        compose.setContent {
            MaterialTheme {
                SubscriptionActionButton(false, { taps++ }, { groupActions++ })
            }
        }
        compose.onNodeWithContentDescription("Subscribe").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(0, taps)
            assertEquals(1, groupActions)
        }
        compose.onNodeWithContentDescription("Subscribe").performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun rowMenuRoutesActionsWithoutOpeningProfile() {
        var openedProfiles = 0
        var groupActions = 0
        var unsubscribeRequests = 0
        compose.setContent {
            MaterialTheme {
                SubscriptionItem(
                    Subscription("alice", "https://example.com"),
                    onClick = { openedProfiles++ },
                    onDelete = { unsubscribeRequests++ },
                    onManageGroups = { groupActions++ }
                )
            }
        }
        compose.onNodeWithContentDescription("Options for @alice").performClick()
        compose.onNodeWithText("Assign to Groups").performClick()
        compose.onNodeWithContentDescription("Options for @alice").performClick()
        compose.onNodeWithText("Unsubscribe").performClick()
        compose.runOnIdle {
            assertEquals(0, openedProfiles)
            assertEquals(1, groupActions)
            assertEquals(1, unsubscribeRequests)
        }
    }

    @Test
    fun existingMembershipIsPreselectedAndOnlySavedOnConfirmation() {
        var saved: List<String>? = null
        compose.setContent {
            MaterialTheme {
                GroupAssignmentDialog(
                    Subscription("alice", "https://example.com", groupIds = listOf("news")),
                    listOf(FeedGroup("news", "News"), FeedGroup("tech", "Tech")),
                    onDismiss = {},
                    onConfirm = { saved = it }
                )
            }
        }
        compose.onNodeWithText("News").assertIsOn()
        compose.onNodeWithText("Tech").performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf("news", "tech"), saved) }
    }

    @Test
    fun cancelDoesNotSaveDraft() {
        var saved: List<String>? = null
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                GroupAssignmentDialog(
                    Subscription("alice", "https://example.com"),
                    listOf(FeedGroup("news", "News")),
                    onDismiss = { dismissed = true },
                    onConfirm = { saved = it },
                    isSubscribed = false
                )
            }
        }
        compose.onNodeWithText("News").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertNull(saved)
            assertEquals(true, dismissed)
        }
    }

    @Test
    fun emptyGroupsDisableSubscribeAndSave() {
        compose.setContent {
            MaterialTheme {
                GroupAssignmentDialog(
                    Subscription("alice", "https://example.com"), emptyList(), {}, {},
                    isSubscribed = false
                )
            }
        }
        compose.onNodeWithText("Subscribe and Save").assertIsNotEnabled()
        compose.onNodeWithText("No groups created yet. Go to Feed to create one!").assertExists()
    }
}
