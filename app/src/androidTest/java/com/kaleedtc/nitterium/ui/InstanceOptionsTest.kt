package com.kaleedtc.nitterium.ui

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.frontend.InstanceSettingsCookies
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NitterWebView
import com.kaleedtc.nitterium.ui.feature.settings.*
import com.kaleedtc.nitterium.data.repository.CookieInstanceOptionsStore
import com.kaleedtc.nitterium.ui.theme.NitteriumTheme
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InstanceOptionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun teapotShowsAppearanceAndIndependentPlaybackControls() {
        val state = mutableStateOf(SettingsState(
            isLoading = false, frontend = FrontendType.TEAPOT,
            instanceSettings = InstanceSettingsCookies.read("", FrontendType.TEAPOT)
        ))
        val events = mutableListOf<SettingsEvent>()
        compose.setContent {
            NitteriumTheme {
                SettingsContent(state.value, { event ->
                    events += event
                    if (event is SettingsEvent.UpdateInstanceSetting) {
                        state.value = state.value.copy(instanceSettings = InstanceSettingsCookies.update(
                            state.value.instanceSettings, event.key, event.value
                        )!!)
                    }
                })
            }
        }
        compose.onNodeWithText("Instance Options").performClick()
        compose.onNodeWithText("Make profile sidebar stick to top").assertDoesNotExist()
        compose.onNodeWithText("Square profile pictures").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SettingsEvent.UpdateInstanceSetting("squareAvatars", false), events.last()) }
        compose.onNodeWithText("HLS Playback").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(SettingsEvent.UpdateInstanceSetting("teapotHlsPlayback", true), events.last())
            assertTrue(state.value.instanceSettings.hlsPlayback)
        }
        compose.onNodeWithText("MP4 playback").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(SettingsEvent.UpdateInstanceSetting("hlsPlayback", false), events.last())
            assertEquals(true, state.value.instanceSettings.teapotHlsPlayback)
        }
        compose.onNodeWithText("Proxy images and videos").performScrollTo().assertIsDisplayed()
    }

    @Test fun nitterKeepsItsExistingPlaybackControlAndNewAppearanceOptions() {
        compose.setContent {
            NitteriumTheme {
                SettingsContent(SettingsState(isLoading = false, frontend = FrontendType.NITTER,
                    instanceSettings = InstanceSettingsCookies.read("", FrontendType.NITTER)), {})
            }
        }
        compose.onNodeWithText("Instance Options").performClick()
        compose.onNodeWithText("Make profile sidebar stick to top").assertDoesNotExist()
        compose.onNodeWithText("Square profile pictures").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("HLS Playback").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("MP4 playback").assertDoesNotExist()
    }

    @Test fun cookiesPersistAppearanceAndBothTeapotPlaybackValuesIndependently() {
        val store = CookieInstanceOptionsStore()
        val origin = "https://instance-options-cookies.test"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            store.ensureDefaults(origin, FrontendType.TEAPOT)
            assertTrue(store.setOption(origin, FrontendType.TEAPOT, "stickyProfile", false))
            assertTrue(store.setOption(origin, FrontendType.TEAPOT, "squareAvatars", true))
            assertTrue(store.setOption(origin, FrontendType.TEAPOT, "hlsPlayback", false))
            assertTrue(store.setOption(origin, FrontendType.TEAPOT, "teapotHlsPlayback", true))
            val restored = CookieInstanceOptionsStore().read(origin, FrontendType.TEAPOT)
            assertEquals(false, restored.stickyProfile)
            assertEquals(true, restored.squareAvatars)
            assertFalse(restored.hlsPlayback)
            assertEquals(true, restored.teapotHlsPlayback)
            assertFalse(store.setOption(origin, FrontendType.TEAPOT, "unknown", true))
        }
    }

    @Test fun proxyDefaultsStayOffForNewUrlsAndPreserveEnabledOptions() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val store = CookieInstanceOptionsStore()
            for (frontend in listOf(FrontendType.NITTER, FrontendType.TEAPOT)) {
                val origin = "https://proxy-default-${frontend.name.lowercase()}-${java.util.UUID.randomUUID()}.test"
                store.ensureProxyDefaults(origin)
                assertFalse(store.read(origin, FrontendType.NITTER).proxyVideos)
                assertFalse(store.read(origin, FrontendType.TEAPOT).proxyVideos)
                store.ensureDefaults(origin, frontend)
                assertFalse(store.read(origin, frontend).proxyVideos)
                assertTrue(store.setOption(origin, frontend, "proxyVideos", true))
                store.ensureProxyDefaults(origin)
                store.ensureDefaults(origin, frontend)
                assertTrue(CookieInstanceOptionsStore().read(origin, frontend).proxyVideos)
            }
        }
    }

    @Test fun appearanceAppliesToProfileAndRestoredTimelineMarkup() {
        val origin = "https://instance-options-markup.test/alice"
        val initialCookies = AtomicReference<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val store = CookieInstanceOptionsStore()
            store.setOption(origin, FrontendType.TEAPOT, "squareAvatars", true)
            store.setOption(origin, FrontendType.TEAPOT, "stickyProfile", false)
        }
        var webView: WebView? = null
        compose.setContent {
            NitteriumTheme {
                CompositionLocalProvider(LocalFullScreenMode provides remember { mutableStateOf(false) }) {
                    NitterWebView(
                        url = origin, frontend = FrontendType.TEAPOT,
                        selectedInstanceUrl = origin,
                        isTrueBlack = false, isSiteHeaderEnabled = false, isBlockDirectXEnabled = true, darkTheme = false,
                        onPageStarted = {}, onPageFinished = { _, _ -> }, onPageError = { _, _ -> },
                        onWebViewCreated = { view ->
                            webView = view
                            val delegate = view.webViewClient
                            view.webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    if (request?.url?.host != "instance-options-markup.test") return delegate.shouldInterceptRequest(view, request)
                                    initialCookies.compareAndSet(null, android.webkit.CookieManager.getInstance().getCookie(origin).orEmpty())
                                    val html = """<html><body><div class="profile-tab sticky"><img class="avatar round"></div>
                                        <div class="timeline"><img class="avatar round"></div></body></html>"""
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
        fun awaitJs(expression: String) {
            val value = AtomicReference<String>()
            compose.waitUntil(15_000) {
                compose.runOnUiThread { webView?.evaluateJavascript("Boolean($expression)") { value.set(it) } }
                value.get() == "true"
            }
        }
        awaitJs("!!document.getElementById('app-theme-override') && !document.querySelector('.profile-tab').classList.contains('sticky') && document.querySelectorAll('img.avatar.round').length === 0")
        assertFalse(InstanceSettingsCookies.read(initialCookies.get(), FrontendType.NITTER).proxyVideos)
        assertFalse(InstanceSettingsCookies.read(initialCookies.get(), FrontendType.TEAPOT).proxyVideos)
        assertTrue(initialCookies.get().contains("proxyVideos=off"))
        assertTrue(initialCookies.get().contains("proxyMedia=off"))
        compose.runOnUiThread { webView!!.evaluateJavascript("document.querySelector('.timeline').innerHTML = '<img class=\"avatar round\">'", null) }
        awaitJs("document.querySelectorAll('.timeline img.avatar').length === 1 && document.querySelectorAll('.timeline img.avatar.round').length === 0")
    }
}
