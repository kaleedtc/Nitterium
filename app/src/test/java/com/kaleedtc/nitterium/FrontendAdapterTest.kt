package com.kaleedtc.nitterium

import com.kaleedtc.nitterium.data.frontend.FrontendAdapter
import org.junit.Assert.*
import org.junit.Test

class FrontendAdapterTest {
    @Test fun preservesSharedRoutesAndFrontendSpecificCookies() {
        assertEquals("mp4Playback", FrontendAdapter.Teapot.playbackCookie)
        assertEquals("proxyMedia", FrontendAdapter.Teapot.proxyCookie)
        assertEquals("hlsPlayback", FrontendAdapter.Nitter.playbackCookie)
        assertEquals("https://custom.example/search?f=tweets&q=a%26b", FrontendAdapter.Teapot.searchUrl("https://custom.example/", "a&b"))
        assertTrue(FrontendAdapter.sameOrigin("https://custom.example", "https://custom.example:443/alice"))
        assertFalse(FrontendAdapter.sameOrigin("invalid", "other"))
    }
}
