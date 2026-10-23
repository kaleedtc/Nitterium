package com.kaleedtc.nitterium.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.mutableStateOf
import com.kaleedtc.nitterium.ui.feature.whatsnew.WhatsNewScreen
import com.kaleedtc.nitterium.ui.theme.NitteriumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WhatsNewScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun continueKeepsFutureReleaseNotesEnabledByDefault() {
        var hideFutureUpdates: Boolean? = null
        compose.setContent {
            NitteriumTheme {
                WhatsNewScreen("2.0.0", onContinue = { hideFutureUpdates = it }, onDonate = {})
            }
        }
        compose.onNodeWithText("What’s new").assertIsDisplayed()
        compose.onNodeWithText("Version 2.0.0").assertIsDisplayed()
        listOf("Nitter and Teapot", "Find and sort subscriptions", "Backups and imports", "Keep your place", "Privacy and security")
            .forEach { title -> compose.onNodeWithText(title).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText(
            "Links, image requests and imports have stricter checks. Existing choices are kept. " +
                "New installs start with direct-X blocking and media proxying off; you can change both in Settings."
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(false, hideFutureUpdates) }
    }

    @Test fun userCanHideFutureScreensAndOpenDonationOptions() {
        var hideFutureUpdates: Boolean? = null
        var donationOpened = false
        compose.setContent {
            NitteriumTheme(darkTheme = true) {
                WhatsNewScreen("2.0.0", onContinue = { hideFutureUpdates = it }, onDonate = { donationOpened = true })
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Donation options"))
        compose.onNodeWithText("Donation options").performClick()
        compose.runOnIdle { assertTrue(donationOpened) }
        compose.onNodeWithText("Don’t show this after updates").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(true, hideFutureUpdates) }
    }

    @Test fun failedSavingCanBeRetriedAndPendingSaveDisablesContinue() {
        val saving = mutableStateOf(false)
        var retries = 0
        compose.setContent {
            NitteriumTheme {
                WhatsNewScreen(
                    "2.0.0",
                    onContinue = { retries++; saving.value = true },
                    onDonate = {},
                    isSaving = saving.value,
                    saveFailed = true
                )
            }
        }
        compose.onNodeWithText("Could not save your preference. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }
}
