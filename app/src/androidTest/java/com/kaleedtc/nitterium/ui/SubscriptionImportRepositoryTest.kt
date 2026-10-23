package com.kaleedtc.nitterium.ui

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.repository.FeedGroupRepository
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SubscriptionImportRepositoryTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(target.cacheDir, "quax-import-${UUID.randomUUID()}").apply { mkdirs() }
    private fun context(files: File = directory) = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = files
    }
    private fun backup() = Uri.fromFile(File(directory, "backup.json").apply {
        writeText("""{"subscriptions":[{"id":"42","screen_name":"ALICE"},{"id":"43","screen_name":"bob"}],
            "subscriptionGroups":[{"id":"news","name":"News"}],
            "subscriptionGroupMembers":[{"group_id":"news","profile_id":"42"},{"group_id":"news","profile_id":"43"}]}""")
    })
    @After fun cleanUp() { directory.deleteRecursively() }

    @Test fun repeatedImportPreservesExistingMetadataAndAvoidsDuplicateAccountsAndGroups() = runBlocking {
        val subscriptions = SubscriptionRepository(context())
        val groups = FeedGroupRepository(context())
        val alice = Subscription("alice", "https://old.example", addedAt = 42)
        subscriptions.addSubscription(alice)
        val first = subscriptions.importSubscriptions(backup(), "https://new.example", groups).getOrThrow()
        assertEquals(1, first.added)
        assertEquals(1, first.duplicates)
        val second = subscriptions.importSubscriptions(backup(), "https://new.example", groups).getOrThrow()
        assertEquals(0, second.added)
        assertEquals(2, second.duplicates)
        val stored = SubscriptionRepository(context()).subscriptions.first()
        val group = FeedGroupRepository(context()).groups.first().single()
        assertEquals(alice.copy(groupIds = listOf(group.id)), stored.first())
        assertEquals("https://new.example", stored.last().instanceUrl)
        assertEquals(listOf(group.id), stored.last().groupIds)
    }

    @Test fun failedSubscriptionWriteRollsBackImportedGroupsAndReturnsFailure() = runBlocking {
        val invalid = File(directory, "invalid").apply { writeText("file instead of directory") }
        val subscriptions = SubscriptionRepository(context(invalid))
        val groups = FeedGroupRepository(context())
        assertTrue(subscriptions.importSubscriptions(backup(), "https://example.com", groups).isFailure)
        assertTrue(subscriptions.subscriptions.first().isEmpty())
        assertTrue(groups.groups.first().isEmpty())
        assertTrue(FeedGroupRepository(context()).groups.first().isEmpty())
    }
}
