package com.kaleedtc.nitterium.data.model

@kotlinx.serialization.Serializable
data class NitterInstanceSettings(
    val hideTweetStats: Boolean = false,
    val hideBanner: Boolean = false,
    val hidePins: Boolean = false,
    val hlsPlayback: Boolean = true,
    val infiniteScroll: Boolean = false,
    val proxyVideos: Boolean = false,
    val muteVideos: Boolean = false,
    val autoplayGifs: Boolean = false,
    // Null means an older backup did not include this option; leave its cookie alone.
    val stickyProfile: Boolean? = null,
    val squareAvatars: Boolean? = null,
    // hlsPlayback retains its original meaning: the adapter's primary playback
    // option (MP4 on Teapot). Forks such as xitter.cf also offer independent HLS.
    val teapotHlsPlayback: Boolean? = null
)
