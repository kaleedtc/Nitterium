package com.kaleedtc.nitterium.ui

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NitterWebView
import com.kaleedtc.nitterium.ui.theme.NitteriumTheme
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WebViewThemeTest {
    @get:Rule val compose = createComposeRule()
    private val dark = mutableStateOf(false)
    private var navigation: NavHostController? = null
    private val headerEnabled = mutableStateOf(false)
    private val targetUrl = mutableStateOf("https://webview-theme.test/alice,bob")
    private var webView: WebView? = null

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            NitteriumTheme(darkTheme = dark.value, dynamicColor = false) {
                CompositionLocalProvider(LocalFullScreenMode provides remember { mutableStateOf(false) }) {
                    val controller = rememberNavController()
                    navigation = controller
                    NavHost(controller, startDestination = "feed") {
                      composable("settings") {}
                      composable("feed") {
                        NitterWebView(
                            url = targetUrl.value,
                            selectedInstanceUrl = "https://webview-theme.test",
                            frontend = FrontendType.TEAPOT,
                            isTrueBlack = true,
                            isSiteHeaderEnabled = headerEnabled.value,
                            isBlockDirectXEnabled = true,
                            darkTheme = dark.value,
                            isFeedView = true,
                            onPageStarted = {}, onPageFinished = { _, _ -> }, onPageError = { _, _ -> },
                            onWebViewCreated = { view ->
                                webView = view
                                val delegate = view.webViewClient
                                view.webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                        if (request?.url?.host != "webview-theme.test") return delegate.shouldInterceptRequest(view, request)
                                        val html = """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                                            <body><div class="multi-user-timeline"><h2>Combined timeline: @alice, @bob</h2>
                                            <div class="timeline"><div class="timeline-item" style="height:3000px">
                                            <div class="tweet-content">Loaded tweets</div><h3>Tweet heading stays visible</h3>
                                            </div></div></div></body></html>"""
                                        return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
                                    }
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = delegate.onPageStarted(view, url, favicon)
                                    override fun onPageFinished(view: WebView?, url: String?) = delegate.onPageFinished(view, url)
                                }
                            }
                        )
                      }
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    @Test fun returningFromSettingsAfterThemeChangesKeepsFeedVisible() {
        show()
        awaitTweets()
        repeat(3) {
            val previous = webView
            compose.runOnIdle {
                navigation!!.navigate("settings") { popUpTo("feed") { inclusive = true; saveState = true } }
            }
            compose.waitForIdle()
            compose.runOnIdle { dark.value = !dark.value }
            compose.runOnIdle {
                navigation!!.navigate("feed") {
                    popUpTo("settings") { inclusive = true }
                    restoreState = true
                }
            }
            compose.waitUntil(10_000) { webView !== previous }
            awaitTweets()
        }
    }

    @Test fun themeUpdatesAndStateRestorationKeepPageAndScroll() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        awaitTweets()
        compose.runOnIdle { webView!!.scrollTo(0, 500) }
        compose.runOnIdle { dark.value = true }
        awaitJs("document.documentElement.style.colorScheme === 'dark' && getComputedStyle(document.body).backgroundColor === 'rgb(0, 0, 0)'")
        compose.runOnIdle { assertEquals(500, webView!!.scrollY) }
        restoration.emulateSavedInstanceStateRestore()
        awaitTweets()
        compose.waitUntil(10_000) { webView!!.scrollY == 500 }
        compose.runOnIdle { dark.value = false }
        awaitJs("document.documentElement.style.colorScheme === 'light' && getComputedStyle(document.body).backgroundColor !== 'rgb(0, 0, 0)'")
        compose.runOnIdle { assertEquals(500, webView!!.scrollY) }
    }

    @Test fun returningToFeedWithNoSavedBrowserHistoryLoadsTheUrl() {
        // There is no browser history yet if a view is saved before its first
        // navigation commits. Scroll metadata must not count as a restored page.
        targetUrl.value = ""
        show()
        compose.waitUntil(10_000) { webView != null }
        val previous = webView
        compose.runOnIdle {
            navigation!!.navigate("settings") { popUpTo("feed") { inclusive = true; saveState = true } }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            dark.value = true
            targetUrl.value = "https://webview-theme.test/alice,bob"
            navigation!!.navigate("feed") {
                popUpTo("settings") { inclusive = true }
                restoreState = true
            }
        }
        compose.waitUntil(10_000) { webView !== previous }
        awaitTweets()
    }

    @Test fun feedHeadingIsHiddenWithoutHidingTweetContent() {
        show()
        awaitTweets()
        awaitJs("getComputedStyle(document.querySelector('.multi-user-timeline > h2')).display === 'none'")
        compose.runOnIdle { headerEnabled.value = true }
        awaitJs("getComputedStyle(document.querySelector('.multi-user-timeline > h2')).display === 'none'")
        awaitJs("getComputedStyle(document.querySelector('.tweet-content')).display !== 'none' && getComputedStyle(document.querySelector('h3')).display !== 'none'")
        compose.runOnIdle { webView!!.loadUrl("https://webview-theme.test/alice/status/123") }
        awaitJs("location.pathname === '/alice/status/123' && getComputedStyle(document.querySelector('.multi-user-timeline > h2')).display !== 'none'")
    }

    private fun awaitTweets() {
        awaitJs("!!document.querySelector('.tweet-content') && !!document.getElementById('app-theme-override')")
        compose.runOnIdle { assertTrue("The loaded page must be visible", webView!!.alpha == 1f) }
    }

    private fun awaitJs(expression: String) {
        val result = AtomicReference<String>()
        compose.waitUntil(15_000) {
            compose.runOnUiThread { webView?.evaluateJavascript("Boolean($expression)") { result.set(it) } }
            result.get() == "true"
        }
    }
}
