package com.kaleedtc.nitterium

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaleedtc.nitterium.data.model.BackupCodec
import com.kaleedtc.nitterium.data.model.BackupScope
import com.kaleedtc.nitterium.data.model.NitteriumBackup
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.model.SubscriptionSortOrder
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.feature.subscriptions.SubscriptionsState
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionSortOrderTest {
    private val subscriptions = listOf(
        Subscription("charlie", "https://example.com", addedAt = 20, groupIds = listOf("news")),
        Subscription("alice", "https://example.com", addedAt = 10),
        Subscription("Bob", "https://example.com", avatarUrl = "https://example.com/bob.png", addedAt = 30)
    )

    @Test fun usernameSortingIgnoresCaseAndPreservesManualOrderAndMetadata() {
        assertEquals(
            listOf(subscriptions[1], subscriptions[2], subscriptions[0]),
            SubscriptionSortOrder.USERNAME.sort(subscriptions)
        )
        assertEquals(listOf("charlie", "alice", "Bob"), subscriptions.map { it.username })
        assertEquals(subscriptions, SubscriptionSortOrder.MANUAL.sort(subscriptions))
    }

    @Test fun subscriptionDateUsesNewestFirstAndKeepsTiedDatesStable() {
        val tied = subscriptions[1].copy(username = "dave", addedAt = 30)
        assertEquals(
            listOf(subscriptions[2], tied, subscriptions[0], subscriptions[1]),
            SubscriptionSortOrder.SUBSCRIPTION_DATE.sort(subscriptions + tied)
        )
    }

    @Test fun draggingRequiresManualOrderAndAnUnfilteredList() {
        val state = SubscriptionsState(subscriptions = subscriptions)
        assertTrue(state.canReorder)
        assertFalse(state.copy(searchQuery = "@bob").canReorder)
        assertFalse(state.copy(sortOrder = SubscriptionSortOrder.USERNAME).canReorder)
        assertFalse(state.copy(sortOrder = SubscriptionSortOrder.SUBSCRIPTION_DATE).canReorder)
    }

    @Test fun preferenceSurvivesStoreReopeningAndSettingsBackupRoundTrip() = runBlocking {
        val directory = Files.createTempDirectory("nitterium-subscription-sort").toFile()
        var job = SupervisorJob()
        try {
            val file = directory.resolve("settings.preferences_pb")
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            val preferences = UserPreferencesRepository(store)
            assertEquals(SubscriptionSortOrder.MANUAL, preferences.subscriptionSortOrder.first())
            preferences.setSubscriptionSortOrder(SubscriptionSortOrder.SUBSCRIPTION_DATE)
            val encoded = BackupCodec.encode(NitteriumBackup(settings = preferences.backupSettings()), BackupScope.SETTINGS)
            job.cancelAndJoin()

            job = SupervisorJob()
            val reopened = UserPreferencesRepository(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            )
            assertEquals(SubscriptionSortOrder.SUBSCRIPTION_DATE, reopened.subscriptionSortOrder.first())
            reopened.setSubscriptionSortOrder(SubscriptionSortOrder.USERNAME)
            reopened.restoreSettings(BackupCodec.decode(encoded, "https://example.com").settings!!)
            assertEquals(SubscriptionSortOrder.SUBSCRIPTION_DATE, reopened.subscriptionSortOrder.first())

            val legacy = BackupCodec.decode("""{"format":"nitterium-backup","version":1,"settings":{}}""", "https://example.com")
            reopened.restoreSettings(legacy.settings!!)
            assertEquals(SubscriptionSortOrder.MANUAL, reopened.subscriptionSortOrder.first())
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test fun unknownStoredPreferenceFallsBackToManual() = runBlocking {
        val directory = Files.createTempDirectory("nitterium-unknown-sort").toFile()
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
                directory.resolve("settings.preferences_pb")
            }
            store.edit { it[stringPreferencesKey("subscription_sort_order")] = "unknown" }
            val preferences = UserPreferencesRepository(store)
            assertEquals(SubscriptionSortOrder.MANUAL, preferences.subscriptionSortOrder.first())
            assertEquals(SubscriptionSortOrder.MANUAL, preferences.backupSettings().subscriptionSortOrder)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
