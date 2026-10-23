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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.AtomicFile
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class FeedGroupRepository(context: Context) {
    private val context: Context = context.applicationContext
    private val fileName = "feed_groups.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
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
        if (file.exists() || File(file.path + ".bak").exists()) {
            try {
                val content = AtomicFile(file).openRead().bufferedReader().use { it.readText() }
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

    private suspend fun change(transform: (List<FeedGroup>) -> List<FeedGroup>) = withContext(Dispatchers.IO) {
        groups.first()
        mutex.withLock { saveList(transform(groups.first())) }
    }

    suspend fun addGroup(name: String, icon: String? = null, color: String? = null) {
        change { it + FeedGroup(UUID.randomUUID().toString(), name, icon, color) }
    }

    suspend fun updateGroup(group: FeedGroup) {
        change { current -> current.map { if (it.id == group.id) group else it } }
    }

    suspend fun removeGroup(groupId: String) {
        change { current -> current.filterNot { it.id == groupId } }
    }

    private fun saveList(list: List<FeedGroup>, publish: Boolean = true): Result<Unit> {
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
            if (publish) _groups.tryEmit(list)
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun <T> withImportedGroups(
        imported: List<FeedGroup>,
        commitSubscriptions: (List<FeedGroup>) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        groups.first()
        mutex.withLock {
            val current = groups.first()
            val merged = current + imported.filter { candidate -> current.none { it.id == candidate.id } }
            val changed = merged != current
            if (changed) {
                val saved = saveList(merged, publish = false)
                if (saved.isFailure) return@withLock Result.failure(saved.exceptionOrNull()!!)
            }
            val result = try {
                commitSubscriptions(merged)
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (changed) {
                if (result.isSuccess) _groups.tryEmit(merged)
                else saveList(current, publish = false).exceptionOrNull()?.let { result.exceptionOrNull()?.addSuppressed(it) }
            }
            result
        }
    }
}
