package com.kaleedtc.nitterium.data.repository

import com.kaleedtc.nitterium.data.model.FeedItem
import com.kaleedtc.nitterium.data.RssFeedParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class FeedRepository {
    private val parser = RssFeedParser()
    private val _feedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val feedItems: StateFlow<List<FeedItem>> = _feedItems.asStateFlow()

    private val _avatarCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val avatarCache: StateFlow<Map<String, String>> = _avatarCache.asStateFlow()

    suspend fun fetchFeeds(instanceUrl: String, usernames: List<String>, clearFirst: Boolean = true) {
        if (clearFirst) {
            _feedItems.value = emptyList()
        }
        
        // Chunk usernames into groups of 15
        val chunks = usernames.chunked(15)
        
        val newItems = withContext(Dispatchers.IO) {
            val deferreds = chunks.map { chunk ->
                async {
                    val combinedUsers = chunk.joinToString(",")
                    val urlStr = "$instanceUrl/$combinedUsers/rss"
                    val (items, avatar) = fetchAndParse(urlStr)
                    
                    // If it's a single user, cache their discovered avatar
                    if (chunk.size == 1 && avatar != null) {
                        updateAvatar(chunk[0], avatar)
                    }
                    items
                }
            }
            deferreds.awaitAll().flatten()
        }

        // Combine with existing if not cleared, sort by date
        val currentItems = if (clearFirst) emptyList() else _feedItems.value
        val allItems = (currentItems + newItems)
            .distinctBy { it.id }
            .sortedByDescending { it.pubDate }

        _feedItems.value = allItems
    }

    fun updateAvatar(username: String, avatarUrl: String) {
        val current = _avatarCache.value.toMutableMap()
        current[username.lowercase()] = avatarUrl
        _avatarCache.value = current
    }

    private fun fetchAndParse(urlStr: String): Pair<List<FeedItem>, String?> {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.readTimeout = 15000
            connection.connectTimeout = 15000
            connection.requestMethod = "GET"
            connection.doInput = true
            
            // Add a User-Agent just in case
            connection.setRequestProperty("User-Agent", "Nitterium/1.3.0")

            connection.connect()
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                parser.parse(connection.inputStream)
            } else {
                Pair(emptyList(), null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), null)
        } finally {
            connection?.disconnect()
        }
    }
}
