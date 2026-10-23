package com.kaleedtc.nitterium.ui

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.ui.common.ErrorScreen
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NitterWebView
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WebViewVerificationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rateLimitMessageIsPreservedAndOffersSwitchInstance() = verifyUnavailablePage(
        "<div class='error-panel'>Instance has been rate limited.<br>Use another instance or try again later.</div>",
        status = 429, expectedError = true,
        expectedMessage = "Instance has been rate limited.\nUse another instance or try again later."
    )

    @Test fun rateLimitMessageIsDetectedWithSuccessfulHttpStatus() = verifyUnavailablePage(
        "<p>Instance has been rate limited.<br>Use another instance or try again later.</p>",
        status = 200, expectedError = true,
        expectedMessage = "Instance has been rate limited.\nUse another instance or try again later."
    )

    @Test fun tweetQuotingRateLimitMessageRemainsReadable() = verifyUnavailablePage(
        "<div class='timeline'><div class='timeline-item'>Instance has been rate limited.</div></div>",
        status = 200, expectedError = false
    )

    @Test fun profileVerificationCanCompleteAfterHttp418() = verifyChallenge(418, "/alice")
    @Test fun groupVerificationCanCompleteAfterHttp403() = verifyChallenge(403, "/alice,bob")
    @Test fun serviceUnavailableVerificationCanComplete() = verifyChallenge(503, "/carol")

    @Test fun tokenExhaustionOffersSwitchInstanceAndRetry() = verifyUnavailablePage(
        "<div class='error-panel'>Instance has no auth tokens, or is fully rate limited.<br>Use another instance or try again later.</div>",
        status = 503, expectedError = true
    )

    @Test fun tokenExhaustionIsDetectedEvenWithSuccessfulHttpStatus() = verifyUnavailablePage(
        "<p>Instance has no auth tokens,<br>or is fully rate limited.</p>",
        status = 200, expectedError = true
    )

    @Test fun tweetQuotingTokenErrorRemainsReadable() = verifyUnavailablePage(
        "<div class='timeline'><div class='timeline-item'><div class='tweet-content'>Instance has no auth tokens, or is fully rate limited.</div></div></div>",
        status = 200, expectedError = false
    )

    @Test fun otherServerErrorsRemainReadable() = verifyUnavailablePage(
        "<div class='error-panel'>User not found</div>",
        status = 404, expectedError = false
    )

    private fun verifyUnavailablePage(content: String, status: Int, expectedError: Boolean, expectedMessage: String? = null) {
        val origin = "https://instance-unavailable.test"
        val errors = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        val failed = mutableStateOf(false)
        val message = mutableStateOf("")
        val recovered = AtomicBoolean(false)
        val switched = AtomicBoolean(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFullScreenMode provides remember { mutableStateOf(false) }) {
                    if (failed.value) ErrorScreen(
                        message = message.value,
                        onRetry = { recovered.set(true); failed.value = false },
                        onSwitchInstance = { switched.set(true) }
                    ) else NitterWebView(
                        url = "$origin/alice,bob",
                        selectedInstanceUrl = origin,
                        isTrueBlack = false,
                        isSiteHeaderEnabled = false,
                        isBlockDirectXEnabled = false,
                        darkTheme = false,
                        onPageStarted = {},
                        onPageFinished = { url, _ -> finished += url },
                        onPageError = { code, description -> errors += code; message.value = description; failed.value = true },
                        onWebViewCreated = { view ->
                            val delegate = view.webViewClient
                            view.webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    if (request?.url?.host != "instance-unavailable.test") return delegate.shouldInterceptRequest(view, request)
                                    val html = if (recovered.get()) "<div class='timeline'>Loaded tweets</div>" else content
                                    return WebResourceResponse("text/html", "UTF-8", if (recovered.get()) 200 else status,
                                        "Fixture", emptyMap(), ByteArrayInputStream("<html><body>$html</body></html>".toByteArray()))
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = delegate.onPageStarted(view, url, favicon)
                                override fun onPageFinished(view: WebView?, url: String?) = delegate.onPageFinished(view, url)
                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) = delegate.onReceivedError(view, request, error)
                                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) = delegate.onReceivedHttpError(view, request, response)
                            }
                        }
                    )
                }
            }
        }
        compose.waitUntil(15_000) { errors.isNotEmpty() || finished.isNotEmpty() }
        if (expectedError) {
            assertTrue("Exhausted instances must enter the native error flow", errors == listOf(503))
            expectedMessage?.let { compose.onNodeWithText(it).assertIsDisplayed() }
            assertTrue("An error page must not also report successful loading", finished.isEmpty())
            compose.onNodeWithText(context.getString(R.string.switch_instance)).assertIsDisplayed().performClick()
            assertTrue("Switch Instance must invoke the app navigation callback", switched.get())
            compose.onNodeWithText(context.getString(R.string.retry)).performClick()
            compose.waitUntil(15_000) { finished.isNotEmpty() }
            assertTrue("Retry must load a recovered instance", errors.size == 1)
        } else {
            assertTrue("Ordinary content must remain visible", errors.isEmpty() && finished.isNotEmpty())
        }
    }

    private fun verifyChallenge(status: Int, path: String) {
        val origin = "https://webview-verification.test"
        val finished = CopyOnWriteArrayList<String>()
        val httpStatuses = CopyOnWriteArrayList<Int>()
        val errors = CopyOnWriteArrayList<Int>()
        val detectedUrls = CopyOnWriteArrayList<String>()
        val failed = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFullScreenMode provides remember { mutableStateOf(false) }) {
                    if (failed.value) Text("Native error replaced verification")
                    else NitterWebView(
                        url = origin + path,
                        selectedInstanceUrl = origin,
                        isTrueBlack = false,
                        isSiteHeaderEnabled = false,
                        isBlockDirectXEnabled = true,
                        darkTheme = false,
                        onFrontendDetected = { url, _ -> detectedUrls += url },
                        onPageStarted = {},
                        onPageFinished = { url, _ -> finished += url },
                        onPageError = { code, _ -> errors += code; failed.value = true },
                        onWebViewCreated = { view ->
                            val delegate = view.webViewClient
                            view.webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    if (request?.url?.host != "webview-verification.test") return delegate.shouldInterceptRequest(view, request)
                                    val verified = request.url.getQueryParameter("verified") == "yes"
                                    val html = if (verified) {
                                        "<html><body><div class='timeline'><div class='timeline-item'>Loaded tweets</div></div></body></html>"
                                    } else {
                                        // A verification page is a valid HTML document despite its HTTP status.
                                        "<html><head><meta http-equiv='refresh' content='0;url=$path?verified=yes'></head><body>Checking your browser</body></html>"
                                    }
                                    return WebResourceResponse("text/html", "UTF-8", if (verified) 200 else status,
                                        if (verified) "OK" else "Browser Verification", emptyMap(), ByteArrayInputStream(html.toByteArray()))
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = delegate.onPageStarted(view, url, favicon)
                                override fun onPageFinished(view: WebView?, url: String?) = delegate.onPageFinished(view, url)
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = delegate.shouldOverrideUrlLoading(view, request)
                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) = delegate.onReceivedError(view, request, error)
                                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                                    if (request?.isForMainFrame == true) httpStatuses += response?.statusCode ?: 0
                                    delegate.onReceivedHttpError(view, request, response)
                                }
                            }
                        }
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) { errors.isNotEmpty() ||
            (finished.any { it.contains("verified=yes") } && detectedUrls.any { it.contains("verified=yes") }) }
        assertTrue("Verification must be allowed to render; errors=$errors; HTTP=$httpStatuses", errors.isEmpty())
        assertTrue("The verification response must exercise HTTP $status", status in httpStatuses)
        assertTrue("Verification must reach the timeline", finished.any { it.contains("verified=yes") })
        assertTrue("Verification pages must not change the frontend setting", detectedUrls.all { it.contains("verified=yes") })
    }
}
