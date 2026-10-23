package com.kaleedtc.nitterium.ui.common

import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebView
import com.kaleedtc.nitterium.data.model.ImportLimits
import com.kaleedtc.nitterium.data.security.UrlPolicy
import java.util.UUID
import org.json.JSONObject
import androidx.core.net.toUri

/** A capability sent only to the selected HTTPS origin's main frame, never to child frames. */
internal class SecurePageBridge(
    private val selectedInstance: () -> String,
    private val blockDirectX: () -> Boolean,
    private val onGallery: (List<String>, Int) -> Unit,
    private val onLongPress: (String) -> Unit
) {
    private var port: WebMessagePort? = null
    private var generation = 0L

    fun close() {
        generation++
        port?.close()
        port = null
    }

    fun attach(view: WebView, pageUrl: String) {
        close()
        val instance = selectedInstance()
        val origin = UrlPolicy.origin(instance) ?: return
        if (view.url != pageUrl || !UrlPolicy.sameOrigin(instance, pageUrl)) return
        val documentGeneration = generation
        val token = UUID.randomUUID().toString()
        val ports = view.createWebMessageChannel()
        port = ports[0]
        fun isCurrent() = generation == documentGeneration && view.url == pageUrl &&
            UrlPolicy.sameOrigin(selectedInstance(), pageUrl)

        ports[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
            override fun onMessage(port: WebMessagePort, message: WebMessage) {
                if (!isCurrent()) return
                receive(message.data)
            }
        })
        // The random handshake prevents an iframe from substituting its own message port.
        // The real port is transferred using postWebMessage's exact targetOrigin, not '*'.
        view.evaluateJavascript("""
            (function() {
                if (location.href !== ${JSONObject.quote(pageUrl)}) return false;
                function connect(event) {
                    if (event.data !== ${JSONObject.quote(token)} || event.ports.length !== 1) return;
                    window.removeEventListener('message', connect);
                    var port = event.ports[0];
                    window.NitterAndroid = {
                        openGallery: function(urls, index) {
                            try { port.postMessage(JSON.stringify({type: 'gallery', urls: JSON.parse(urls), index: index})); } catch (_) {}
                        },
                        handleLongPress: function(url) {
                            port.postMessage(JSON.stringify({type: 'longPress', url: url}));
                        }
                    };
                }
                window.addEventListener('message', connect);
                return true;
            })()
        """.trimIndent()) { ready ->
            if (ready == "true" && isCurrent()) {
                view.postWebMessage(WebMessage(token, arrayOf(ports[1])), origin.toUri())
            } else {
                ports[1].close()
                if (generation == documentGeneration) close()
            }
        }
    }

    private fun receive(data: String?) {
        if (data == null || data.length > 65_536) return
        try {
            ImportLimits.validate(data)
            val message = JSONObject(data)
            when (message.optString("type")) {
                "gallery" -> {
                    val values = message.optJSONArray("urls") ?: return
                    if (values.length() !in 1..50) return
                    val index = message.opt("index") as? Int ?: return
                    if (index !in 0 until values.length()) return
                    val urls = (0 until values.length()).map { i ->
                        UrlPolicy.mediaUrl(values.opt(i) as? String, blockDirectX()) ?: return
                    }
                    onGallery(urls, index)
                }
                "longPress" -> {
                    val url = UrlPolicy.httpsUrl(message.opt("url") as? String) ?: return
                    onLongPress(url.toString())
                }
            }
        } catch (_: Exception) {
            // Invalid remote input must never escape into Compose or repository state.
        }
    }
}
