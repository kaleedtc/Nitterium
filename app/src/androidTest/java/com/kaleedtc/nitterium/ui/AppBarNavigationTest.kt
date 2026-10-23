package com.kaleedtc.nitterium.ui

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.feature.feed.FeedContent
import com.kaleedtc.nitterium.ui.feature.feed.FeedEvent
import com.kaleedtc.nitterium.ui.feature.feed.FeedState
import com.kaleedtc.nitterium.ui.feature.profile.ProfileContent
import com.kaleedtc.nitterium.ui.feature.profile.ProfileEvent
import com.kaleedtc.nitterium.ui.feature.profile.ProfileState
import com.kaleedtc.nitterium.ui.theme.NitteriumTheme
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppBarNavigationTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var webView: WebView
    private val finishedUrl = AtomicReference<String>()
    private var nativeBackCount = 0
    private val origin = "https://app-bar-navigation.test"
    private val backLabel: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.back)

    @Test fun profileArrowReturnsToSubscriptionsAfterBrowsingTabs() {
        openDetailWithHistory(isProfile = true)

        compose.onNodeWithContentDescription(backLabel).performClick()

        compose.onNodeWithText("Subscriptions").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, nativeBackCount) }
    }

    @Test fun feedArrowReturnsToGroupsAfterBrowsingPages() {
        openDetailWithHistory(isProfile = false)

        compose.onNodeWithContentDescription(backLabel).performClick()

        compose.onNodeWithText("Feed groups").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, nativeBackCount) }
    }

    @Test fun profileSystemBackStillFollowsPageHistory() {
        openDetailWithHistory(isProfile = true)

        pressBack()

        awaitPage("$origin/alice/with_replies")
        compose.onNodeWithContentDescription(backLabel).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, nativeBackCount) }
    }

    @Test fun feedSystemBackStillFollowsPageHistory() {
        openDetailWithHistory(isProfile = false)

        pressBack()

        awaitPage("$origin/alice/with_replies")
        compose.onNodeWithContentDescription(backLabel).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, nativeBackCount) }
    }

    private fun openDetailWithHistory(isProfile: Boolean) {
        compose.setContent {
            // Create the platform view without making a request before the fixture client is attached.
            // The selected-origin guard rejects this sentinel; the fixture then loads HTTPS pages.
            var currentUrl by remember { mutableStateOf("about:blank") }
            var isLoading by remember { mutableStateOf(false) }
            var detailVisible by remember { mutableStateOf(true) }
            val snackbarHostState = remember { SnackbarHostState() }
            val navigateBack = { nativeBackCount++; detailVisible = false }
            NitteriumTheme {
                CompositionLocalProvider(LocalFullScreenMode provides remember { mutableStateOf(false) }) {
                    if (!detailVisible) {
                        Text(if (isProfile) "Subscriptions" else "Feed groups")
                    } else if (isProfile) {
                        ProfileContent(
                            state = ProfileState(username = "alice", currentUrl = currentUrl, selectedInstanceUrl = origin, isLoading = isLoading),
                            isDarkTheme = false,
                            onEvent = { event ->
                                when (event) {
                                    is ProfileEvent.OnPageStarted -> isLoading = true
                                    is ProfileEvent.OnPageFinished -> {
                                        currentUrl = event.url
                                        isLoading = false
                                        finishedUrl.set(event.url)
                                    }
                                    else -> Unit
                                }
                            },
                            snackbarHostState = snackbarHostState,
                            onNavigateBack = navigateBack,
                            onSwitchInstance = {}
                        )
                    } else {
                        FeedContent(
                            state = FeedState(groupName = "FOSS", currentUrl = currentUrl, selectedInstanceUrl = origin, isLoading = isLoading),
                            isDarkTheme = false,
                            onEvent = { event ->
                                when (event) {
                                    is FeedEvent.OnPageStarted -> isLoading = true
                                    is FeedEvent.OnPageFinished -> {
                                        currentUrl = event.url
                                        isLoading = false
                                        finishedUrl.set(event.url)
                                    }
                                    else -> Unit
                                }
                            },
                            snackbarHostState = snackbarHostState,
                            onNavigateBack = navigateBack,
                            onSwitchInstance = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        onView(isAssignableFrom(WebView::class.java)).check { view, _ ->
            webView = view as WebView
            val delegate = webView.webViewClient
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    if (request?.url?.host != "app-bar-navigation.test") return delegate.shouldInterceptRequest(view, request)
                    val html = "<html><body><div class='timeline'>${request.url.path}</div></body></html>"
                    return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) =
                    delegate.onPageStarted(view, url, favicon)

                override fun onPageFinished(view: WebView?, url: String?) =
                    delegate.onPageFinished(view, url)
            }
        }
        navigateTo("$origin/alice")
        compose.runOnUiThread { webView.clearHistory() }
        navigateTo("$origin/alice/with_replies")
        navigateTo("$origin/alice/media")
        compose.runOnIdle { assertTrue("Fixture must have browser history", webView.canGoBack()) }
    }

    private fun navigateTo(url: String) {
        compose.runOnUiThread { webView.loadUrl(url) }
        awaitPage(url)
    }

    private fun awaitPage(url: String) {
        compose.waitUntil(15_000) { finishedUrl.get() == url }
        compose.waitForIdle()
    }
}
