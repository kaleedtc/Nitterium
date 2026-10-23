package com.kaleedtc.nitterium.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.feature.feed.FeedViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedSelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val directory = File(target.cacheDir, "feed-selection-${UUID.randomUUID()}").apply { mkdirs() }
    private val store = ViewModelStore()
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
    }

    @After fun cleanUp() {
        instrumentation.runOnMainSync { store.clear() }
        directory.deleteRecursively()
    }

    @Test fun selectingAGroupRebuildsTheUrlAfterSubscriptionsHaveAlreadyLoaded() = runBlocking {
        val repository = SubscriptionRepository(context)
        repository.subscriptions.first()
        repository.addSubscription(Subscription("alice", "https://example.com", groupIds = listOf("news")))
        repository.addSubscription(Subscription("bob", "https://example.com", groupIds = listOf("tech")))
        lateinit var viewModel: FeedViewModel
        instrumentation.runOnMainSync {
            viewModel = FeedViewModel(UserPreferencesRepository(target), repository, object : ConnectivityMonitor {
                override val isConnected = MutableStateFlow(true)
            })
            store.put("feed", viewModel)
        }
        await { viewModel.state.value.currentUrl.endsWith("/alice,bob") }
        instrumentation.runOnMainSync { viewModel.setGroup("news", "News") }
        await { viewModel.state.value.currentUrl.endsWith("/alice") }
        assertEquals("news", viewModel.state.value.groupId)
        instrumentation.runOnMainSync { viewModel.setGroup("tech", "Tech") }
        await { viewModel.state.value.currentUrl.endsWith("/bob") }
        instrumentation.runOnMainSync { viewModel.setGroup("empty", "Empty") }
        await { !viewModel.state.value.hasSubscriptions }
        assertEquals("", viewModel.state.value.currentUrl)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Feed URL did not update after group selection" }
            Thread.sleep(20)
        }
    }
}
