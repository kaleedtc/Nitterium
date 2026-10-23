package com.kaleedtc.nitterium

import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val settings = AppSettingsBackup(
        instanceUrl = "https://teapot.example", dynamicColor = false, darkTheme = null,
        trueBlack = true, siteHeader = true, navLabels = false, defaultTab = "Feed", blockDirectX = false,
        customInstances = setOf("https://teapot.example"),
        frontendModes = mapOf("https://teapot.example" to FrontendType.TEAPOT),
        detectedFrontends = mapOf("https://nitter.example" to FrontendType.NITTER),
        instanceOptions = mapOf("https://teapot.example" to InstanceOptionsBackup(
            FrontendType.TEAPOT, NitterInstanceSettings(hideBanner = true, infiniteScroll = true, muteVideos = true,
                stickyProfile = false, squareAvatars = true, teapotHlsPlayback = true)
        ))
    )
    private val backup = NitteriumBackup(
        createdAt = 42,
        subscriptions = listOf(Subscription("alice", "https://teapot.example", addedAt = 10, groupIds = listOf("news"))),
        groups = listOf(FeedGroup("news", "News", "Public", "#123456")),
        settings = settings
    )

    @Test fun fullBackupRoundTripsProfilesGroupsMembershipAndAllSettings() {
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup, BackupScope.EVERYTHING), "https://unused.example"))
    }

    @Test fun eachScopeIncludesOnlyTheChosenData() {
        val profiles = BackupCodec.encode(backup, BackupScope.SUBSCRIPTIONS)
        assertTrue(profiles.trimStart().startsWith("["))
        val legacy = SubscriptionImportParser.parse(profiles, "https://unused.example")
        assertEquals(backup.subscriptions!!.single().copy(groupIds = emptyList()), legacy.subscriptions.single())
        assertTrue(legacy.groups.isEmpty())

        val groups = BackupCodec.decode(BackupCodec.encode(backup, BackupScope.SUBSCRIPTIONS_AND_GROUPS), "https://unused.example")
        assertEquals(backup.subscriptions, groups.subscriptions)
        assertEquals(backup.groups, groups.groups)
        assertNull(groups.settings)

        val preferences = BackupCodec.decode(BackupCodec.encode(backup, BackupScope.SETTINGS), "https://unused.example")
        assertEquals(settings, preferences.settings)
        assertNull(preferences.subscriptions)
        assertNull(preferences.groups)
    }

    @Test fun subscriptionImporterAlsoUnderstandsNewBackupsWithGroups() {
        val imported = SubscriptionImportParser.parse(BackupCodec.encode(backup, BackupScope.EVERYTHING), "https://unused.example")
        assertEquals(backup.subscriptions, imported.subscriptions)
        assertEquals(backup.groups, imported.groups)
    }

    @Test fun legacyNativeAndQuaxFilesCanBePreviewed() {
        val native = BackupCodec.decode("""[{"username":"alice","instanceUrl":"https://old.example"}]""", "https://new.example")
        assertEquals("alice", native.subscriptions!!.single().username)
        assertNull(native.settings)
        val quax = BackupCodec.decode("""{"subscriptions":[{"screen_name":"bob"},{"screen_name":"invalid username"}]}""", "https://new.example")
        assertEquals("bob", quax.subscriptions!!.single().username)
        assertEquals(1, quax.skipped)
    }

    @Test fun malformedUnknownAndInvalidBackupsAreRejected() {
        listOf(
            "{}", "not json",
            """{"format":"nitterium-backup","version":2,"subscriptions":[]}""",
            """{"format":"different-app","version":1,"subscriptions":[]}""",
            """{"format":"nitterium-backup","version":1,"groups":[]}"""
        ).forEach { content -> assertTrue(content, runCatching { BackupCodec.decode(content, "https://example.com") }.isFailure) }
        assertTrue(runCatching { BackupCodec.validate(backup.copy(settings = settings.copy(instanceUrl = "file:///tmp/x"))) }.isFailure)
        assertTrue(runCatching { BackupCodec.validate(backup.copy(groups = backup.groups!! + backup.groups)) }.isFailure)
        assertTrue(runCatching { BackupCodec.validate(backup.copy(subscriptions = listOf(Subscription("bad username", "https://example.com")))) }.isFailure)
    }
}
