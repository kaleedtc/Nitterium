package com.kaleedtc.nitterium.data.repository

import android.content.Context
import com.kaleedtc.nitterium.data.model.FeedGroup
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
import java.util.UUID

class FeedGroupRepository(context: Context) {
    private val context: Context = context.applicationContext
    private val fileName = "feed_groups.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _groups = MutableSharedFlow<List<FeedGroup>>(
        replay = 1, 
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val groups = _groups.asSharedFlow()

    init {
        scope.launch {
            loadGroups()
        }
    }

    private fun loadGroups() {
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            try {
                val content = file.readText()
                val list = json.decodeFromString<List<FeedGroup>>(content)
                _groups.tryEmit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                _groups.tryEmit(emptyList())
            }
        } else {
            _groups.tryEmit(emptyList())
        }
    }

    suspend fun addGroup(name: String, icon: String? = null, color: String? = null) {
        withContext(Dispatchers.IO) {
            val currentList = _groups.replayCache.firstOrNull() ?: emptyList()
            val newGroup = FeedGroup(
                id = UUID.randomUUID().toString(),
                name = name,
                icon = icon,
                color = color
            )
            saveList(currentList + newGroup)
        }
    }

    suspend fun updateGroup(group: FeedGroup) {
        withContext(Dispatchers.IO) {
            val currentList = _groups.replayCache.firstOrNull() ?: emptyList()
            val newList = currentList.map {
                if (it.id == group.id) group else it
            }
            saveList(newList)
        }
    }

    suspend fun removeGroup(groupId: String) {
        withContext(Dispatchers.IO) {
            val currentList = _groups.replayCache.firstOrNull() ?: emptyList()
            val newList = currentList.filterNot { it.id == groupId }
            saveList(newList)
        }
    }

    private fun saveList(list: List<FeedGroup>) {
        try {
            val file = File(context.filesDir, fileName)
            val content = json.encodeToString(list)
            file.writeText(content)
            _groups.tryEmit(list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
