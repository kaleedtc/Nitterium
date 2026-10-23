package com.kaleedtc.nitterium

import com.kaleedtc.nitterium.data.security.UrlPolicy
import org.junit.Assert.*
import org.junit.Test

class UrlPolicyTest {
    @Test fun switchingInstancesPreservesEncodedContentAndChangesBasePaths() {
        assertEquals(
            "https://new.example/frontend/alice/status/123?q=a%26b&cursor=one%2Ftwo#tweet",
            UrlPolicy.switchInstance("https://old.example/nitter", "https://new.example/frontend/",
                "https://old.example/nitter/alice/status/123?q=a%26b&cursor=one%2Ftwo#tweet")
        )
        assertNull(UrlPolicy.switchInstance("https://old.example", "https://new.example", "https://untrusted.example/alice"))
        assertNull(UrlPolicy.switchInstance("https://old.example", "http://new.example", "https://old.example/alice"))
        assertEquals("alice", UrlPolicy.profileUsername("https://new.example/frontend/alice/status/123", "https://new.example/frontend"))
        assertNull(UrlPolicy.profileUsername("https://new.example/frontendx/alice", "https://new.example/frontend"))
    }

    @Test fun instanceChoicesIncludeLaterCustomInstancesAndRejectInvalidUrls() {
        val builtIn = listOf("https://one.example", "https://two.example")
        assertEquals(
            builtIn + "https://later.example",
            UrlPolicy.instanceChoices(builtIn, setOf("https://one.example/", "https://later.example", "file:///tmp/no", "http://insecure.example"),
                "https://two.example")
        )
        assertEquals(builtIn + "https://selected.example", UrlPolicy.instanceChoices(builtIn, emptySet(), "https://selected.example"))
    }
    @Test fun explicitIntentCannotReplaceTheSelectedAuthority() {
        listOf(
            "@evil.example/alice", "//evil.example/alice", "javascript:alert(1)",
            "https://evil.example/alice", "https://x.com@evil.example/alice",
            "https://user@x.com/alice", "file:///alice", "https://x.com.evil.example/alice"
        ).forEach { assertNull(it, UrlPolicy.rewriteDeepLink("https://instance.test", it)) }
    }

    @Test fun supportedLinksPreserveEncodedDelimitersAndInstanceBasePath() {
        assertEquals("https://instance.test/base/search?q=a%26b%23c",
            UrlPolicy.rewriteDeepLink("https://instance.test/base", "http://x.com/search?q=a%26b%23c"))
        assertEquals("https://instance.test/%40evil.example/alice",
            UrlPolicy.rewriteDeepLink("https://instance.test", "https://x.com/%40evil.example/alice"))
    }

    @Test fun imagesAndSubdomainsDoNotGrantOriginTrust() {
        assertTrue(UrlPolicy.sameOrigin("https://instance.test", "https://instance.test:443/alice"))
        listOf("https://child.instance.test", "https://instance.test:444", "http://instance.test",
            "https://evil.test/?hint=.jpgfoo", "https://user@instance.test").forEach {
            assertFalse(it, UrlPolicy.sameOrigin("https://instance.test", it))
        }
    }

    @Test fun nativeMediaRejectsLocalUrisAndHonorsExactDomainBoundaries() {
        listOf("file:///data/user/0/app/image", "content://provider/image", "data:image/png;base64,x",
            "http://instance.test/image", "https://user:pass@instance.test/image").forEach {
            assertNull(it, UrlPolicy.mediaUrl(it, false))
        }
        assertNull(UrlPolicy.mediaUrl("https://PBS.TWIMG.COM./image", true))
        assertNull(UrlPolicy.mediaUrl("https://sub.x.com/image", true))
        assertNotNull(UrlPolicy.mediaUrl("https://notx.com/image", true))
        assertNotNull(UrlPolicy.mediaUrl("https://pbs.twimg.com.evil.test/image", true))
        assertNotNull(UrlPolicy.mediaUrl("https://pbs.twimg.com/image", false))
    }
}
