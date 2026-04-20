package com.kaleedtc.nitterium.data.model

data class NitterInstanceSettings(
    val hideTweetStats: Boolean = false,
    val hideBanner: Boolean = false,
    val hidePins: Boolean = false,
    val hlsPlayback: Boolean = true,
    val infiniteScroll: Boolean = false,
    val proxyVideos: Boolean = true,
    val muteVideos: Boolean = false,
    val autoplayGifs: Boolean = false
)