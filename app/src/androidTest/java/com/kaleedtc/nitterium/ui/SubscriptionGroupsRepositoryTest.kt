package com.kaleedtc.nitterium.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionGroupsRepositoryTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(target.cacheDir, "subscription-groups-${UUID.randomUUID()}").apply { mkdirs() }
    private fun context(files: File = directory) = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = files
    }

    @After fun cleanUp() { directory.deleteRecursively() }

    @Test
    fun subscribeWithGroupsPersistsInOneSave() = runBlocking {
        val repository = SubscriptionRepository(context())
        val subscription = Subscription("alice", "https://example.com", "avatar", addedAt = 42)
        assertTrue(repository.saveSubscriptionGroups(subscription, listOf("news", "tech")).isSuccess)
        val reloaded = SubscriptionRepository(context()).subscriptions.first()
        assertEquals(listOf(subscription.copy(groupIds = listOf("news", "tech"))), reloaded)
    }

    @Test
    fun editingGroupsPreservesLatestMetadataOrderAndOtherUsers() = runBlocking {
        val repository = SubscriptionRepository(context())
        repository.subscriptions.first()
        val alice = Subscription("alice", "https://example.com", addedAt = 42, groupIds = listOf("news"))
        val bob = Subscription("bob", "https://example.com")
        repository.addSubscription(alice)
        repository.addSubscription(bob)
        repository.updateSubscriptionAvatar("alice", "new-avatar")
        assertTrue(repository.saveSubscriptionGroups(alice.copy(username = "ALICE"), emptyList()).isSuccess)
        assertEquals(listOf(alice.copy(avatarUrl = "new-avatar", groupIds = emptyList()), bob), repository.subscriptions.first())
    }

    @Test
    fun failedSaveReturnsFailureWithoutPublishingSubscription() = runBlocking {
        val invalidDirectory = File(directory, "file-instead-of-directory").apply { writeText("occupied") }
        val repository = SubscriptionRepository(context(invalidDirectory))
        assertTrue(repository.saveSubscriptionGroups(Subscription("alice", "https://example.com"), listOf("news")).isFailure)
        assertTrue(repository.subscriptions.first().isEmpty())
    }
}
