package com.kaleedtc.nitterium

import com.kaleedtc.nitterium.data.security.ImageRequestInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** A fake chain records connection attempts; these fixtures never use sockets. */
class ImageRequestInterceptorTest {
    @Test fun redirectsDropOriginalCookiesEvenWhenDestinationHasNoCookies() {
        val chain = RecordingChain("https://a.test/image", mapOf("a.test" to "https://b.test/image"))
        val interceptor = ImageRequestInterceptor({ false }, { if (it.startsWith("https://a.test/")) "secret=A" else null }, "test")
        interceptor.intercept(chain).close()
        assertEquals(2, chain.requests.size)
        assertEquals("secret=A", chain.requests[0].header("Cookie"))
        assertNull(chain.requests[1].header("Cookie"))
        assertEquals("https://b.test/", chain.requests[1].header("Referer"))
    }

    @Test fun destinationGetsOnlyItsOwnCookies() {
        val chain = RecordingChain("https://a.test/image", mapOf("a.test" to "https://b.test/image"))
        ImageRequestInterceptor({ false }, { if (it.startsWith("https://a.test/")) "secret=A" else "secret=B" }, "test")
            .intercept(chain).close()
        assertEquals("secret=B", chain.requests.last().header("Cookie"))
    }

    @Test fun directAndRedirectedBlockedHostsNeverReachTheConnectionChain() {
        val interceptor = ImageRequestInterceptor({ true }, { null }, "test")
        val direct = RecordingChain("https://pbs.twimg.com/image")
        assertThrows(IOException::class.java) { interceptor.intercept(direct) }
        assertTrue(direct.requests.isEmpty())
        val redirected = RecordingChain("https://a.test/image", mapOf("a.test" to "https://pbs.twimg.com/image"))
        assertThrows(IOException::class.java) { interceptor.intercept(redirected) }
        assertEquals(listOf("a.test"), redirected.requests.map { it.url.host })
    }

    @Test fun livePreferenceChangesAndInsecureRedirectsAreCheckedBeforeConnection() {
        var blocked = false
        val interceptor = ImageRequestInterceptor({ blocked }, { null }, "test")
        interceptor.intercept(RecordingChain("https://pbs.twimg.com/image")).close()
        blocked = true
        val next = RecordingChain("https://pbs.twimg.com/image")
        assertThrows(IOException::class.java) { interceptor.intercept(next) }
        assertTrue(next.requests.isEmpty())
        val downgrade = RecordingChain("https://a.test/image", mapOf("a.test" to "http://b.test/image"))
        assertThrows(IOException::class.java) { interceptor.intercept(downgrade) }
        assertEquals(1, downgrade.requests.size)
    }

    private class RecordingChain(url: String, private val redirects: Map<String, String> = emptyMap()) : Interceptor.Chain {
        val requests = mutableListOf<Request>()
        private val original = Request.Builder().url(url).header("Cookie", "untrusted=old").build()
        override fun request() = original
        override fun proceed(request: Request): Response {
            requests += request
            val location = redirects[request.url.host]
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (location == null) 200 else 302).message("Fixture")
                .apply { if (location != null) header("Location", location) }
                .body(ByteArray(0).toResponseBody()).build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = error("No sockets in this fixture")
        override fun connectTimeoutMillis() = 1000
        override fun readTimeoutMillis() = 1000
        override fun writeTimeoutMillis() = 1000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
