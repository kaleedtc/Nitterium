package com.kaleedtc.nitterium.data.frontend

import com.kaleedtc.nitterium.data.model.NitterInstanceSettings

/** Cookie keys are shared by native controls and backup/restore. */
object InstanceSettingsCookies {
    internal val proxyDefaults: Map<String, Boolean> = listOf(FrontendAdapter.Nitter, FrontendAdapter.Teapot)
        .associate { it.proxyCookie to false }

    private val commonKeys = setOf(
        "hideTweetStats", "hideBanner", "hidePins", "infiniteScroll",
        "muteVideos", "autoplayGifs", "stickyProfile", "squareAvatars"
    )

    private fun parse(header: String): Map<String, String> = header.split(';').mapNotNull { cookie ->
        val parts = cookie.trim().split('=', limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()

    fun read(header: String, frontend: FrontendType): NitterInstanceSettings {
        val cookies = parse(header)
        val adapter = FrontendAdapter.forType(frontend)
        fun enabled(key: String, default: Boolean = false) = cookies[key]?.let { it == "on" } ?: default
        return NitterInstanceSettings(
            hideTweetStats = enabled("hideTweetStats"), hideBanner = enabled("hideBanner"),
            hidePins = enabled("hidePins"), hlsPlayback = enabled(adapter.playbackCookie, true),
            infiniteScroll = enabled("infiniteScroll"), proxyVideos = enabled(adapter.proxyCookie),
            muteVideos = enabled("muteVideos"), autoplayGifs = enabled("autoplayGifs", true),
            stickyProfile = enabled("stickyProfile", true),
            squareAvatars = enabled("squareAvatars", frontend == FrontendType.TEAPOT),
            teapotHlsPlayback = if (frontend == FrontendType.TEAPOT) enabled("hlsPlayback") else null
        )
    }

    fun values(settings: NitterInstanceSettings, frontend: FrontendType): Map<String, Boolean> {
        val adapter = FrontendAdapter.forType(frontend)
        return buildMap {
            put("hideTweetStats", settings.hideTweetStats)
            put("hideBanner", settings.hideBanner)
            put("hidePins", settings.hidePins)
            put(adapter.playbackCookie, settings.hlsPlayback)
            put("infiniteScroll", settings.infiniteScroll)
            put(adapter.proxyCookie, settings.proxyVideos)
            put("muteVideos", settings.muteVideos)
            put("autoplayGifs", settings.autoplayGifs)
            settings.stickyProfile?.let { put("stickyProfile", it) }
            settings.squareAvatars?.let { put("squareAvatars", it) }
            if (frontend == FrontendType.TEAPOT) settings.teapotHlsPlayback?.let { put("hlsPlayback", it) }
        }
    }

    fun missingDefaults(header: String, frontend: FrontendType): Map<String, Boolean> {
        val cookies = parse(header)
        val adapter = FrontendAdapter.forType(frontend)
        return mapOf(adapter.playbackCookie to true, adapter.proxyCookie to false).filterKeys { it !in cookies }
    }

    /** Seed both proxy keys before loading a page, while its frontend may still be unknown. */
    fun missingProxyDefaults(header: String): Map<String, Boolean> {
        val cookies = parse(header)
        return proxyDefaults.filterKeys { it !in cookies }
    }

    fun cookieKey(key: String, frontend: FrontendType): String? = when (key) {
        "hlsPlayback" -> FrontendAdapter.forType(frontend).playbackCookie
        "proxyVideos" -> FrontendAdapter.forType(frontend).proxyCookie
        "teapotHlsPlayback" -> if (frontend == FrontendType.TEAPOT) "hlsPlayback" else null
        in commonKeys -> key
        else -> null
    }

    fun update(settings: NitterInstanceSettings, key: String, value: Boolean): NitterInstanceSettings? = when (key) {
        "hideTweetStats" -> settings.copy(hideTweetStats = value)
        "hideBanner" -> settings.copy(hideBanner = value)
        "hidePins" -> settings.copy(hidePins = value)
        "hlsPlayback" -> settings.copy(hlsPlayback = value)
        "infiniteScroll" -> settings.copy(infiniteScroll = value)
        "proxyVideos" -> settings.copy(proxyVideos = value)
        "muteVideos" -> settings.copy(muteVideos = value)
        "autoplayGifs" -> settings.copy(autoplayGifs = value)
        "stickyProfile" -> settings.copy(stickyProfile = value)
        "squareAvatars" -> settings.copy(squareAvatars = value)
        "teapotHlsPlayback" -> settings.copy(teapotHlsPlayback = value)
        else -> null
    }
}
