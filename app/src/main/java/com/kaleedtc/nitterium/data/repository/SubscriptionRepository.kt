package com.kaleedtc.nitterium.data.repository

import android.content.Context
import android.net.Uri
import com.kaleedtc.nitterium.data.model.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class SubscriptionRepository(context: Context) {
    private val context: Context = context.applicationContext
    private val fileName = "subscriptions.json"
    private val json = Json { ignoreUnknownKeys = true }
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
        if (file.exists()) {
            try {
                val content = file.readText()
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

    suspend fun addSubscription(subscription: Subscription) {
        withContext(Dispatchers.IO) {
            val currentList = _subscriptions.replayCache.firstOrNull() ?: emptyList()
            if (currentList.none { it.username == subscription.username }) {
                val newList = currentList + subscription
                saveList(newList)
            }
        }
    }

    suspend fun removeSubscription(username: String) {
        withContext(Dispatchers.IO) {
            val currentList = _subscriptions.replayCache.firstOrNull() ?: emptyList()
            val newList = currentList.filterNot { it.username == username }
            saveList(newList)
        }
    }

    suspend fun updateSubscriptionOrder(newList: List<Subscription>) {
        withContext(Dispatchers.IO) {
            saveList(newList)
        }
    }
    
    fun isSubscribed(username: String): Boolean {
        val currentList = _subscriptions.replayCache.firstOrNull() ?: emptyList()
        return currentList.any { it.username == username }
    }

    private fun saveList(list: List<Subscription>) {
        try {
            val file = File(context.filesDir, fileName)
            val content = json.encodeToString(list)
            file.writeText(content)
            _subscriptions.tryEmit(list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun exportSubscriptions(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentList = _subscriptions.replayCache.firstOrNull() ?: emptyList()
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

    suspend fun importSubscriptions(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Could not open input stream"))

            val importedList = json.decodeFromString<List<Subscription>>(content)
            
            val currentList = _subscriptions.replayCache.firstOrNull() ?: emptyList()
            
            // Merge lists, avoiding duplicates based on username
            val currentMap = currentList.associateBy { it.username }.toMutableMap()
            var importedCount = 0
            
            for (subscription in importedList) {
                if (!currentMap.containsKey(subscription.username)) {
                    currentMap[subscription.username] = subscription
                    importedCount++
                }
            }
            
            if (importedCount > 0) {
                saveList(currentMap.values.toList())
            }
            
            Result.success(importedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
