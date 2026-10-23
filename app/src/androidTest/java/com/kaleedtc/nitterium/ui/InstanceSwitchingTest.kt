package com.kaleedtc.nitterium.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.repository.FeedGroupRepository
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.feature.feed.FeedEvent
import com.kaleedtc.nitterium.ui.feature.feed.FeedViewModel
import com.kaleedtc.nitterium.ui.feature.profile.ProfileEvent
import com.kaleedtc.nitterium.ui.feature.profile.ProfileViewModel
import com.kaleedtc.nitterium.ui.feature.search.SearchEvent
import com.kaleedtc.nitterium.ui.feature.search.SearchViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class InstanceSwitchingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val directory = File(target.cacheDir, "instance-switching-${UUID.randomUUID()}").apply { mkdirs() }
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
    }
    private val job = SupervisorJob()
    private val preferences = UserPreferencesRepository(PreferenceDataStoreFactory.create(
        scope = CoroutineScope(job + Dispatchers.IO)
    ) { directory.resolve("settings.preferences_pb") })
    private val subscriptions = SubscriptionRepository(context)
    private val groups = FeedGroupRepository(context)
    private val models = ViewModelStore()
    private val connection = object : ConnectivityMonitor { override val isConnected = MutableStateFlow(true) }
    private val old = "https://old-instance.test"
    private val next = "https://later-added.test/frontend"
    private val message = "Instance has been rate limited.\nUse another instance or try again later."

    @After fun close() = runBlocking {
        instrumentation.runOnMainSync { models.clear() }
        job.cancelAndJoin()
        directory.deleteRecursively()
        Unit
    }

    private suspend fun prepare() {
        subscriptions.subscriptions.first()
        groups.groups.first()
        preferences.setInstanceUrl(old)
        preferences.addCustomInstance(next)
    }

    private fun <T : ViewModel> create(factory: () -> T): T {
        lateinit var result: T
        instrumentation.runOnMainSync {
            result = factory()
            models.put("model", result)
        }
        return result
    }

    @Test fun searchKeepsEncodedQueryAndClearsTheServerErrorAfterSwitching() = runBlocking {
        prepare()
        val model = create { SearchViewModel(preferences, subscriptions, connection, groups) }
        await { model.state.value.selectedInstanceUrl == old }
        val path = "/search?f=tweets&q=a%26b&cursor=one%2Ftwo"
        instrumentation.runOnMainSync {
            model.onEvent(SearchEvent.OnPageStarted(old + path))
            model.onEvent(SearchEvent.OnPageError(503, message))
        }
        assertEquals(message, model.state.value.errorMessage)
        preferences.setInstanceUrl(next)
        await { model.state.value.currentUrl == next + path }
        assertFalse(model.state.value.isError)
        assertNull(model.state.value.errorMessage)
    }

    @Test fun profileKeepsTheDisplayedTweetInsteadOfReturningToTheProfileRoot() = runBlocking {
        prepare()
        val model = create { ProfileViewModel(preferences, subscriptions, connection, groups) }
        instrumentation.runOnMainSync { model.onEvent(ProfileEvent.LoadProfile("alice")) }
        await { model.state.value.currentUrl == "$old/alice" }
        val path = "/alice/status/123?cursor=one%2Ftwo"
        instrumentation.runOnMainSync {
            model.onEvent(ProfileEvent.OnPageStarted(old + path))
            model.onEvent(ProfileEvent.OnPageError(503, message))
        }
        assertEquals(message, model.state.value.errorMessage)
        preferences.setInstanceUrl(next)
        await { model.state.value.currentUrl == next + path }
        val avatar = "$next/pic/avatar.png"
        instrumentation.runOnMainSync {
            model.onEvent(ProfileEvent.OnPageStarted(next + path))
            model.onEvent(ProfileEvent.OnAvatarFound(next + path, avatar))
        }
        await { model.state.value.avatarUrl == avatar }
        assertEquals("alice", model.state.value.username)
        assertFalse(model.state.value.isError)
        assertNull(model.state.value.errorMessage)
    }

    @Test fun groupFeedKeepsGroupAndPaginationWhenTheInstanceChanges() = runBlocking {
        prepare()
        subscriptions.addSubscription(Subscription("alice", old, groupIds = listOf("news")))
        subscriptions.addSubscription(Subscription("bob", old, groupIds = listOf("tech")))
        val model = create { FeedViewModel(preferences, subscriptions, connection) }
        instrumentation.runOnMainSync { model.setGroup("news", "News") }
        await { model.state.value.currentUrl == "$old/alice" }
        val path = "/alice?cursor=one%2Ftwo"
        instrumentation.runOnMainSync {
            model.onEvent(FeedEvent.OnPageStarted(old + path))
            model.onEvent(FeedEvent.OnPageError(message))
        }
        assertEquals(message, model.state.value.errorMessage)
        preferences.setInstanceUrl(next)
        await { model.state.value.currentUrl == next + path }
        assertEquals("news", model.state.value.groupId)
        assertFalse(model.state.value.isError)
        assertNull(model.state.value.errorMessage)
    }

    private suspend fun await(condition: () -> Boolean) {
        kotlinx.coroutines.withTimeout(5_000) {
            while (!condition()) kotlinx.coroutines.delay(20)
        }
    }
}
