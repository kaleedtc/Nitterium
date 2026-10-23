package com.kaleedtc.nitterium.ui

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.model.*
import com.kaleedtc.nitterium.data.repository.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class BackupRepositoryTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(target.cacheDir, "backup-tests-${UUID.randomUUID()}").apply { mkdirs() }
    private val jobs = mutableListOf<Job>()
    private val savedOptions = NitterInstanceSettings(hideBanner = true, infiniteScroll = true, muteVideos = true,
        stickyProfile = false, squareAvatars = true, teapotHlsPlayback = true)
    private val savedSettings = AppSettingsBackup(
        instanceUrl = "https://teapot.example", darkTheme = true, defaultTab = "Feed", navLabels = false,
        customInstances = setOf("https://teapot.example"),
        frontendModes = mapOf("https://teapot.example" to FrontendType.TEAPOT),
        instanceOptions = mapOf("https://teapot.example" to InstanceOptionsBackup(FrontendType.TEAPOT, savedOptions))
    )
    private val saved = NitteriumBackup(
        subscriptions = listOf(Subscription("alice", "https://teapot.example", addedAt = 42, groupIds = listOf("news"))),
        groups = listOf(FeedGroup("news", "News", "Public", "#123456")), settings = savedSettings
    )

    private class Options : InstanceOptionsStore {
        val values = mutableMapOf<Pair<String, FrontendType>, NitterInstanceSettings>()
        override fun read(url: String, frontend: FrontendType) = values[url to frontend] ?: NitterInstanceSettings()
        override fun write(url: String, frontend: FrontendType, settings: NitterInstanceSettings) { values[url to frontend] = settings }
    }

    private inner class Fixture(name: String, invalidSubscriptions: Boolean = false) {
        val files = File(directory, name).apply { mkdirs() }
        fun context(location: File) = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = location
        }
        val job = SupervisorJob().also(jobs::add)
        val preferences = UserPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO)
        ) { File(files, "settings.preferences_pb") })
        val subscriptions = SubscriptionRepository(context(if (invalidSubscriptions) File(files, "invalid").apply { writeText("not a directory") } else files))
        val groups = FeedGroupRepository(context(files))
        val options = Options()
        val backups = BackupRepository(context(files), subscriptions, groups, preferences, options)
    }

    @After fun cleanUp() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }
        directory.deleteRecursively()
        Unit
    }

    @Test fun completeBackupRestoresOnFreshStorageAndSurvivesRepositoryRecreation() = runBlocking {
        val source = Fixture("source")
        source.backups.restore(saved).getOrThrow()
        val uri = Uri.fromFile(File(directory, "complete.json"))
        source.backups.export(uri, BackupScope.EVERYTHING, emptyList()).getOrThrow()
        val destination = Fixture("fresh")
        val preview = destination.backups.read(uri).getOrThrow()
        assertTrue(destination.subscriptions.subscriptions.first().isEmpty())
        assertEquals(AppSettingsBackup(), destination.preferences.backupSettings())
        destination.backups.restore(preview).getOrThrow()
        assertEquals(saved.subscriptions, SubscriptionRepository(destination.context(destination.files)).subscriptions.first())
        assertEquals(saved.groups, FeedGroupRepository(destination.context(destination.files)).groups.first())
        assertEquals(savedSettings.copy(instanceOptions = emptyMap()), destination.preferences.backupSettings())
        assertEquals(savedOptions, destination.options.read(savedSettings.instanceUrl, FrontendType.TEAPOT))
    }

    @Test fun repeatRestoreMergesWithoutRemovingProfilesOrDuplicatingGroups() = runBlocking {
        val fixture = Fixture("merge")
        val existing = Subscription("ALICE", "https://existing.example", avatarUrl = "https://existing.example/avatar", addedAt = 7)
        fixture.subscriptions.addSubscription(existing)
        fixture.subscriptions.addSubscription(Subscription("bob", "https://existing.example"))
        repeat(2) { fixture.backups.restore(saved).getOrThrow() }
        assertEquals(listOf("ALICE", "bob"), fixture.subscriptions.subscriptions.first().map { it.username })
        assertEquals(existing.copy(groupIds = listOf("news")), fixture.subscriptions.subscriptions.first().first())
        assertEquals(saved.groups, fixture.groups.groups.first())
    }

    @Test fun failedProfileWriteRollsBackGroupsPreferencesAndInstanceOptions() = runBlocking {
        val fixture = Fixture("failure", invalidSubscriptions = true)
        val original = AppSettingsBackup(instanceUrl = "https://original.example", darkTheme = false)
        fixture.preferences.restoreSettings(original)
        val oldOptions = fixture.options.read(savedSettings.instanceUrl, FrontendType.TEAPOT)
        assertTrue(fixture.backups.restore(saved).isFailure)
        assertEquals(original, fixture.preferences.backupSettings())
        assertEquals(oldOptions, fixture.options.read(savedSettings.instanceUrl, FrontendType.TEAPOT))
        assertTrue(fixture.groups.groups.first().isEmpty())
        assertTrue(FeedGroupRepository(fixture.context(fixture.files)).groups.first().isEmpty())
    }

    @Test fun invalidBackupDoesNotChangeStoredData() = runBlocking {
        val fixture = Fixture("invalid")
        fixture.backups.restore(saved).getOrThrow()
        val uri = Uri.fromFile(File(directory, "unsupported.json").apply {
            writeText("""{"format":"nitterium-backup","version":99,"subscriptions":[]}""")
        })
        assertTrue(fixture.backups.read(uri).isFailure)
        assertTrue(fixture.backups.restore(saved.copy(settings = savedSettings.copy(instanceUrl = "file:///invalid"))).isFailure)
        assertEquals(saved.subscriptions, fixture.subscriptions.subscriptions.first())
        assertEquals(savedSettings.copy(instanceOptions = emptyMap()), fixture.preferences.backupSettings())
    }

    @Test fun settingsOnlyRestoreKeepsProfilesAndExportsTeapotOptionsForTrailingSlashUrl() = runBlocking {
        val fixture = Fixture("settings-only")
        fixture.backups.restore(saved).getOrThrow()
        val settings = savedSettings.copy(instanceUrl = "https://teapot.example/", darkTheme = null)
        fixture.backups.restore(NitteriumBackup(settings = settings)).getOrThrow()
        assertEquals(saved.subscriptions, fixture.subscriptions.subscriptions.first())
        assertEquals(saved.groups, fixture.groups.groups.first())
        // Cookies apply to the same origin with or without a trailing slash.
        fixture.options.write(settings.instanceUrl, FrontendType.TEAPOT, savedOptions)
        val uri = Uri.fromFile(File(directory, "settings-only.json"))
        fixture.backups.export(uri, BackupScope.SETTINGS, emptyList()).getOrThrow()
        val exported = fixture.backups.read(uri).getOrThrow()
        assertNull(exported.subscriptions)
        assertNull(exported.groups)
        assertEquals(InstanceOptionsBackup(FrontendType.TEAPOT, savedOptions), exported.settings!!.instanceOptions[settings.instanceUrl])
    }

    @Test fun instanceOptionsRoundTripForBothFrontends() {
        val store = CookieInstanceOptionsStore()
        FrontendType.entries.filter { it != FrontendType.AUTO }.forEach { frontend ->
            val url = "https://backup-${frontend.name.lowercase()}.test"
            val options = savedOptions.copy(hlsPlayback = false, proxyVideos = false,
                teapotHlsPlayback = if (frontend == FrontendType.TEAPOT) true else null)
            store.write(url, frontend, options)
            assertEquals(options, store.read(url, frontend))
        }
    }
}
