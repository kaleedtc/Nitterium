package com.kaleedtc.nitterium.data.security

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Automatic redirects must be disabled on the client using this interceptor. */
class ImageRequestInterceptor(
    private val blockDirectX: () -> Boolean,
    private val cookiesForUrl: (String) -> String?,
    private val userAgent: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.method != "GET") throw IOException("Unsupported image request")
        repeat(11) {
            val url = request.url
            if (UrlPolicy.mediaUrl(url.toString(), blockDirectX()) == null) {
                throw IOException("Image request blocked")
            }
            // Rebuild credentials for EVERY hop before connecting, including an empty cookie store.
            val builder = request.newBuilder()
                .removeHeader("Cookie")
                .removeHeader("Cookie2")
                .removeHeader("Authorization")
                .removeHeader("Proxy-Authorization")
                .header("User-Agent", userAgent)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString())
            cookiesForUrl(url.toString())?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
            val response = chain.proceed(builder.build())
            val location = response.header("Location")
            if (response.code !in setOf(301, 302, 303, 307, 308) || location == null) return response
            val target = url.resolve(location)
            response.close()
            if (target == null) throw IOException("Invalid image redirect")
            // Use the original request, not response.request (which includes this hop's cookies).
            request = request.newBuilder().url(target).build()
        }
        throw IOException("Too many image redirects")
    }
}
