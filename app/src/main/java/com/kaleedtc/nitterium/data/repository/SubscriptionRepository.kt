package com.kaleedtc.nitterium.data.repository

import android.content.Context
import android.net.Uri
import com.kaleedtc.nitterium.data.model.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.AtomicFile
import com.kaleedtc.nitterium.data.model.ImportReport
import com.kaleedtc.nitterium.data.model.SubscriptionImportParser
import java.util.Locale
import kotlinx.serialization.json.Json
import java.io.File

class SubscriptionRepository(context: Context) {
    private val context: Context = context.applicationContext
    private val fileName = "subscriptions.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Cache in memory for speed
    private val _subscriptions = MutableSharedFlow<List<Subscription>>(
        replay = 1, 
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val subscriptions = _subscriptions.asSharedFlow()

    init {
        // Load initial data asynchronously
        scope.launch {
            loadSubscriptions()
        }
    }

    private fun loadSubscriptions() {
        val file = File(context.filesDir, fileName)
        if (file.exists() || File(file.path + ".bak").exists()) {
            try {
                val content = AtomicFile(file).openRead().bufferedReader().use { it.readText() }
                val list = json.decodeFromString<List<Subscription>>(content)
                _subscriptions.tryEmit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                _subscriptions.tryEmit(emptyList())
            }
        } else {
            _subscriptions.tryEmit(emptyList())
        }
    }

    private suspend fun change(transform: (List<Subscription>) -> List<Subscription>): Result<Unit> =
        withContext(Dispatchers.IO) {
            subscriptions.first()
            mutex.withLock {
                val current = subscriptions.first()
                val updated = transform(current)
                if (updated == current) Result.success(Unit) else saveList(updated)
            }
        }

    suspend fun addSubscription(subscription: Subscription) {
        change { current ->
            if (current.any { it.username.equals(subscription.username, ignoreCase = true) }) current
            else current + subscription
        }
    }

    suspend fun removeSubscription(username: String) {
        change { current -> current.filterNot { it.username.equals(username, ignoreCase = true) } }
    }

    suspend fun updateSubscriptionOrder(newList: List<Subscription>) {
        change { current ->
            // Reordering must not overwrite metadata updates or concurrent imports.
            val byName = current.associateBy { it.username.lowercase(Locale.ROOT) }
            val ordered = newList.mapNotNull { byName[it.username.lowercase(Locale.ROOT)] }.distinctBy { it.username.lowercase(Locale.ROOT) }
            ordered + current.filter { candidate -> ordered.none { it.username.equals(candidate.username, ignoreCase = true) } }
        }
    }

    suspend fun updateSubscriptionAvatar(username: String, avatarUrl: String) {
        change { current -> current.map {
            if (it.username.equals(username, ignoreCase = true) && avatarUrl.isNotBlank()) it.copy(avatarUrl = avatarUrl) else it
        } }
    }

    suspend fun updateSubscriptionGroups(username: String, groupIds: List<String>) {
        change { current -> current.map {
            if (it.username.equals(username, ignoreCase = true)) it.copy(groupIds = groupIds.distinct()) else it
        } }
    }

    suspend fun saveSubscriptionGroups(subscription: Subscription, groupIds: List<String>): Result<Unit> = change { current ->
        val existing = current.firstOrNull { it.username.equals(subscription.username, ignoreCase = true) }
        val updated = (existing ?: subscription).copy(groupIds = groupIds.distinct())
        if (existing == null) current + updated else current.map { if (it === existing) updated else it }
    }

    fun isSubscribed(username: String): Boolean {
        val currentList = _subscriptions.replayCache.firstOrNull() ?: emptyList()
        return currentList.any { it.username.equals(username, ignoreCase = true) }
    }

    private fun saveList(list: List<Subscription>): Result<Unit> {
        try {
            val file = File(context.filesDir, fileName)
            val content = json.encodeToString(list)
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(content.toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
            _subscriptions.tryEmit(list)
            return Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    suspend fun exportSubscriptions(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentList = subscriptions.first()
            val content = json.encodeToString(currentList)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun importSubscriptions(
        uri: Uri,
        instanceUrl: String,
        groupRepository: FeedGroupRepository
    ): Result<ImportReport> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use { com.kaleedtc.nitterium.data.model.ImportLimits.read(it) }
            } ?: error("Could not open input stream")
            val imported = SubscriptionImportParser.parse(content, instanceUrl)
            mergeImportedSubscriptions(imported, groupRepository)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun mergeImportedSubscriptions(
        imported: com.kaleedtc.nitterium.data.model.SubscriptionImport,
        groupRepository: FeedGroupRepository
    ): Result<ImportReport> = withContext(Dispatchers.IO) {
        try {
            subscriptions.first()
            mutex.withLock {
                val current = subscriptions.first()
                groupRepository.withImportedGroups(imported.groups) { groups ->
                    val knownGroups = groups.map { it.id }.toSet()
                    val merged = current.associateBy { it.username.lowercase(Locale.ROOT) }.toMutableMap()
                    var added = 0
                    var duplicates = 0
                    imported.subscriptions.forEach { subscription ->
                        val key = subscription.username.lowercase(Locale.ROOT)
                        val existing = merged[key]
                        val groupIds = subscription.groupIds.filter { it in knownGroups }
                        if (existing == null) {
                            merged[key] = subscription.copy(groupIds = groupIds)
                            added++
                        } else {
                            merged[key] = existing.copy(groupIds = (existing.groupIds + groupIds).distinct())
                            duplicates++
                        }
                    }
                    val updated = merged.values.toList()
                    val saved = if (updated != current) saveList(updated) else Result.success(Unit)
                    saved.map { ImportReport(added, duplicates, imported.skipped) }
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

}
