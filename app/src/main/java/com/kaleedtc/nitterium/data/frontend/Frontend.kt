package com.kaleedtc.nitterium.data.frontend

import java.net.URI
import java.net.URLEncoder

enum class FrontendType { AUTO, NITTER, TEAPOT }

/** Both frontends share profile/search routes and Nitter-style timeline markup. */
sealed class FrontendAdapter(val type: FrontendType) {
    open val playbackCookie = "hlsPlayback"
    open val proxyCookie = "proxyVideos"

    fun profileUrl(instance: String, username: String): String {
        require(Regex("[A-Za-z0-9_]{1,15}").matches(username)) { "Invalid username" }
        return "${instance.trimEnd('/')}/$username"
    }

    fun timelineUrl(instance: String, usernames: List<String>): String {
        require(usernames.isNotEmpty())
        usernames.forEach { profileUrl(instance, it) }
        return "${instance.trimEnd('/')}/${usernames.joinToString(",")}"
    }

    fun searchUrl(instance: String, query: String): String =
        "${instance.trimEnd('/')}/search?f=tweets&q=${URLEncoder.encode(query, "UTF-8")}"

    open fun themeCookie(dark: Boolean, black: Boolean): String = when {
        dark && black -> "Twitter Black"
        dark -> "Twitter Dark"
        else -> "Twitter"
    }

    data object Nitter : FrontendAdapter(FrontendType.NITTER)
    data object Teapot : FrontendAdapter(FrontendType.TEAPOT) {
        override val playbackCookie = "mp4Playback"
        override val proxyCookie = "proxyMedia"
        override fun themeCookie(dark: Boolean, black: Boolean) = "teapot"
    }

    companion object {
        fun forType(type: FrontendType): FrontendAdapter = if (type == FrontendType.TEAPOT) Teapot else Nitter
        fun sameOrigin(first: String, second: String): Boolean = runCatching {
            val a = URI(first)
            val b = URI(second)
            fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
            a.scheme?.lowercase() in listOf("http", "https") && !a.host.isNullOrBlank() &&
                a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && port(a) == port(b)
        }.getOrDefault(false)
    }
}
