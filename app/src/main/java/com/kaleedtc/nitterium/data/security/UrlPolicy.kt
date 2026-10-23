package com.kaleedtc.nitterium.data.security

import java.net.URI
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** URL checks shared by navigation, native media and incoming intents. No DNS lookups. */
object UrlPolicy {
    const val MAX_URL_LENGTH = 8192
    private val linkHosts = setOf(
        "twitter.com", "www.twitter.com", "mobile.twitter.com", "x.com", "nitter.net",
        "t.co", "xcancel.com", "rss.xcancel.com", "vxtwitter.com", "fxtwitter.com"
    )

    fun httpsUrl(value: String?): HttpUrl? {
        if (value == null || value.length > MAX_URL_LENGTH || value.any { it.isISOControl() }) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrEmpty() || uri.rawUserInfo != null) return null
        return value.toHttpUrlOrNull()?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
    }

    fun sameOrigin(instance: String, page: String?): Boolean {
        val a = httpsUrl(instance) ?: return false
        val b = httpsUrl(page) ?: return false
        return a.host == b.host && a.port == b.port
    }

    fun origin(url: String): String? = httpsUrl(url)?.newBuilder()
        ?.encodedPath("/")?.query(null)?.fragment(null)?.build()?.toString()?.trimEnd('/')

    fun isXHost(host: String): Boolean {
        val normalized = host.trimEnd('.').lowercase(Locale.ROOT)
        return listOf("twitter.com", "x.com", "twimg.com").any {
            normalized == it || normalized.endsWith(".$it")
        }
    }

    fun mediaUrl(value: String?, blockDirectX: Boolean): String? = httpsUrl(value)
        ?.takeUnless { blockDirectX && isXHost(it.host) }?.toString()

    fun instanceChoices(builtIn: List<String>, custom: Set<String>, selected: String): List<String> =
        (builtIn + custom + selected).filter { httpsUrl(it) != null }.distinctBy { it.trimEnd('/') }

    /** Move the current page to another instance without decoding its path or query. */
    fun switchInstance(previous: String, next: String, page: String): String? {
        val oldBase = httpsUrl(previous) ?: return null
        val newBase = httpsUrl(next) ?: return null
        val current = httpsUrl(page)?.takeIf { sameOrigin(previous, page) } ?: return null
        val oldPath = oldBase.encodedPath.trimEnd('/')
        val relativePath = if (oldPath.isNotEmpty() &&
            (current.encodedPath == oldPath || current.encodedPath.startsWith("$oldPath/"))) {
            current.encodedPath.removePrefix(oldPath)
        } else current.encodedPath
        return newBase.newBuilder()
            .encodedPath(newBase.encodedPath.trimEnd('/') + relativePath.ifEmpty { "/" })
            .encodedQuery(current.encodedQuery)
            .encodedFragment(current.encodedFragment)
            .build().toString()
    }

    fun rewriteDeepLink(instance: String, incoming: String): String? {
        if (incoming.length > MAX_URL_LENGTH) return null
        val uri = runCatching { URI(incoming) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.rawUserInfo != null) return null
        val source = incoming.toHttpUrlOrNull()?.takeIf {
            it.host in linkHosts && it.username.isEmpty() && it.password.isEmpty()
        } ?: return null
        val base = httpsUrl(instance) ?: return null
        return base.newBuilder()
            .encodedPath(base.encodedPath.trimEnd('/') + source.encodedPath)
            .encodedQuery(source.encodedQuery)
            .fragment(null)
            .build().toString()
    }

    fun profileUsername(page: String, instance: String? = null): String? {
        val url = httpsUrl(page) ?: return null
        val baseSegments = if (instance == null) emptyList() else {
            if (!sameOrigin(instance, page)) return null
            httpsUrl(instance)?.pathSegments?.dropLastWhile { it.isEmpty() } ?: return null
        }
        if (url.pathSegments.take(baseSegments.size) != baseSegments) return null
        val first = url.pathSegments.getOrNull(baseSegments.size) ?: return null
        return first.takeIf {
            it.lowercase(Locale.ROOT) !in setOf("search", "settings", "about", "pic", "status") &&
                Regex("[A-Za-z0-9_]{1,15}").matches(it)
        }
    }
}
