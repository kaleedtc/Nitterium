package com.kaleedtc.nitterium.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NitterWebView
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Optional network check; ordinary test runs never contact an external instance. */
class LiveInstanceSmokeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun profileLoadsAfterBrowserVerification() = load("/elonmusk")
    @Test fun groupLoadsAfterBrowserVerification() = load("/elonmusk,OpenAI")

    private fun load(path: String) {
        val instance = InstrumentationRegistry.getArguments().getString("liveInstance")
        assumeTrue("Pass -Pandroid.testInstrumentationRunnerArguments.liveInstance=https://... to enable", !instance.isNullOrBlank())
        val loaded = AtomicBoolean(false)
        val lastDocument = AtomicReference("")
        val errors = CopyOnWriteArrayList<Int>()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFullScreenMode provides remember { mutableStateOf(false) }) {
                    NitterWebView(
                        url = instance!!.trimEnd('/') + path,
                        selectedInstanceUrl = requireNotNull(instance),
                        isTrueBlack = false,
                        isSiteHeaderEnabled = false,
                        isBlockDirectXEnabled = true,
                        darkTheme = false,
                        onPageStarted = {},
                        onPageFinished = { _, view ->
                            view.evaluateJavascript("document.title + ' | ' + location.pathname") { lastDocument.set(it) }
                            view.evaluateJavascript("!!document.querySelector('.timeline .timeline-item .tweet-content')") {
                                if (it == "true") loaded.set(true)
                            }
                        },
                        onPageError = { code, _ -> errors += code },
                        onWebViewCreated = {}
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 60_000) { loaded.get() || errors.isNotEmpty() }
        assertTrue("Timeline did not load; errors=$errors; page=${lastDocument.get()}", loaded.get())
    }
}
