package com.kaleedtc.nitterium.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.ui.common.SecurePageBridge
import com.kaleedtc.nitterium.ui.common.shareTweetScript
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** All requests are fulfilled in memory; no fixture uses the network. */
class WebViewSecurityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var view: WebView
    private lateinit var bridge: SecurePageBridge
    private val galleries = CopyOnWriteArrayList<Pair<List<String>, Int>>()
    private val links = CopyOnWriteArrayList<String>()
    private var selectedInstance = "https://trusted.test"

    @After fun close() {
        instrumentation.runOnMainSync {
            if (::bridge.isInitialized) bridge.close()
            if (::view.isInitialized) view.destroy()
        }
    }

    @Test fun quoteBearingShareUrlRemainsData() {
        open()
        val payload = "https://evil.test/status/'+(window.__nitteriumAudit=1)+'"
        evaluate("document.getElementById('tweet-link').href = ${JSONObject.quote(payload)}")
        assertEquals("\"Safe tweet text\"", evaluate(shareTweetScript(payload)))
        assertEquals("0", evaluate("window.__nitteriumAudit || 0"))
    }

    @Test fun onlyMainFrameReceivesTheNativeCapabilityAndGalleryInputIsChecked() {
        open()
        awaitScript("!!window.NitterAndroid && window.frameReport === 'undefined/undefined/undefined'")
        evaluate("""
            NitterAndroid.openGallery('[]', 0);
            NitterAndroid.openGallery('["file:///data/local/image"]', 0);
            NitterAndroid.openGallery('["https://trusted.test/image"]', -1);
            NitterAndroid.openGallery('["https://trusted.test/image"]', 10);
            NitterAndroid.openGallery('["https://pbs.twimg.com/image"]', 0);
            NitterAndroid.openGallery('not JSON', 0);
            NitterAndroid.openGallery('["https://trusted.test/image"]', 0);
        """.trimIndent())
        awaitCondition { galleries.size == 1 }
        assertEquals(listOf("https://trusted.test/image") to 0, galleries.single())
        assertEquals("\"undefined\"", evaluate("typeof AndroidSearch"))
        assertEquals("\"undefined\"", evaluate("typeof AndroidProfile"))
    }

    @Test fun imageLookingUntrustedMainDocumentGetsNoBridge() {
        open("https://evil.test/?hint=.jpgfoo")
        assertEquals("\"undefined\"", evaluate("typeof NitterAndroid"))
        assertTrue(galleries.isEmpty())
        assertTrue(links.isEmpty())
    }

    @Test fun changingTheSelectedOriginRevokesAnExistingCapability() {
        open()
        awaitScript("!!window.NitterAndroid")
        evaluate("NitterAndroid.handleLongPress('https://trusted.test/status/first')")
        awaitCondition { links.size == 1 }
        instrumentation.runOnMainSync { selectedInstance = "https://other.test" }
        evaluate("NitterAndroid.handleLongPress('https://trusted.test/status/1')")
        SystemClock.sleep(200)
        instrumentation.runOnMainSync { bridge.close() }
        assertEquals(listOf("https://trusted.test/status/first"), links.toList())
    }

    private fun open(page: String = "https://trusted.test/alice") {
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext)
            view.settings.javaScriptEnabled = true
            bridge = SecurePageBridge({ selectedInstance }, { true },
                { urls, index -> galleries += urls to index }, { links += it })
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                    val html = if (request?.url?.path == "/frame") """
                        <html><script>
                        parent.postMessage('report:' + typeof NitterAndroid + '/' + typeof AndroidSearch + '/' + typeof AndroidProfile, '*');
                        </script></html>
                    """ else """
                        <html><body>
                        <script>addEventListener('message', function(e) {
                            if (typeof e.data === 'string' && e.data.indexOf('report:') === 0) window.frameReport = e.data.substring(7);
                        });</script>
                        <div class="timeline-item"><a id="tweet-link" href="/alice/status/1">Tweet</a>
                        <div class="tweet-content">Safe tweet text</div></div>
                        <iframe src="https://evil.test/frame"></iframe>
                        </body></html>
                    """
                    return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { bridge.close() }
                override fun onPageFinished(view: WebView, url: String) {
                    bridge.attach(view, url)
                    loaded.countDown()
                }
            }
            view.loadUrl(page)
        }
        assertTrue("Fixture document did not finish", loaded.await(10, TimeUnit.SECONDS))
    }

    private fun evaluate(script: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { view.evaluateJavascript(script) { result.set(it); done.countDown() } }
        assertTrue("JavaScript did not finish", done.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun awaitScript(script: String) = awaitCondition { evaluate(script) == "true" }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
        assertTrue("Fixture condition did not become true", condition())
    }
}
