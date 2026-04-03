package com.kaleedtc.nitterium.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.kaleedtc.nitterium.ui.common.LoadingIndicator
import org.junit.Rule
import org.junit.Test

class LoadingIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingIndicator_isDisplayed_whenLoading() {
        composeTestRule.setContent {
            LoadingIndicator(isLoading = true)
        }
        
        composeTestRule.onNodeWithTag("LoadingIndicator").assertIsDisplayed()
    }

    @Test
    fun loadingIndicator_isNotDisplayed_whenNotLoading() {
        composeTestRule.setContent {
            LoadingIndicator(isLoading = false)
        }
        
        composeTestRule.onNodeWithTag("LoadingIndicator").assertDoesNotExist()
    }
}
