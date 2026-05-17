package com.kaleedtc.nitterium.data

import android.util.Xml
import com.kaleedtc.nitterium.data.model.FeedItem
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

class RssFeedParser {
    // Nitter dates look like "Wed, 02 Aug 2023 15:04:00 GMT"
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)

    fun parse(inputStream: InputStream): Pair<List<FeedItem>, String?> {
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)
            
            // Advance to the first tag
            while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) {
                parser.next()
            }
            if (parser.eventType != XmlPullParser.START_TAG) {
                return Pair(emptyList(), null)
            }
            return readRss(parser)
        }
    }

    private fun readRss(parser: XmlPullParser): Pair<List<FeedItem>, String?> {
        val items = mutableListOf<FeedItem>()
        var channelAvatar: String? = null
        if (parser.name != "rss") {
            skip(parser)
            return Pair(emptyList(), null)
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "channel") {
                val (channelItems, avatar) = readChannel(parser)
                items.addAll(channelItems)
                channelAvatar = avatar
            } else {
                skip(parser)
            }
        }
        return Pair(items, channelAvatar)
    }

    private fun readChannel(parser: XmlPullParser): Pair<List<FeedItem>, String?> {
        val items = mutableListOf<FeedItem>()
        var avatarUrl: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "item" -> items.add(readItem(parser))
                "image" -> avatarUrl = readImage(parser)
                else -> skip(parser)
            }
        }
        return Pair(items, avatarUrl)
    }

    private fun readImage(parser: XmlPullParser): String? {
        var url: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "url") {
                url = readText(parser)
            } else {
                skip(parser)
            }
        }
        
        // Filter out Nitter's default "sticky" profile image used for combined feeds
        if (url != null && (url.contains("default_profile") || url.contains("sticky"))) {
            return null
        }
        return url
    }

    private fun readItem(parser: XmlPullParser): FeedItem {
        var title: String? = null
        var description: String? = null
        var link: String? = null
        var pubDateStr: String? = null
        var creator: String? = null
        var guid: String? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = readText(parser)
                "description" -> description = readText(parser)
                "link" -> link = readText(parser)
                "pubDate" -> pubDateStr = readText(parser)
                "creator" -> creator = readText(parser)
                "guid" -> guid = readText(parser)
                else -> {
                    // Try to catch dc:creator if namespace feature doesn't strip prefix
                    if (parser.name.contains("creator")) {
                        creator = readText(parser)
                    } else {
                        skip(parser)
                    }
                }
            }
        }

        val pubDate = try {
            pubDateStr?.let { dateFormat.parse(it)?.time } ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        
        // Extract author username from link if creator is null
        // https://nitter.net/username/status/123...
        val username = link?.let {
            val parts = it.split("/")
            if (parts.size > 3) parts[3] else ""
        } ?: ""

        val id = guid ?: link ?: System.nanoTime().toString()

        val titleStr = title ?: ""
        var retweetedBy: String? = null
        if (titleStr.startsWith("RT by @")) {
            val endIdx = titleStr.indexOf(':')
            if (endIdx != -1) {
                retweetedBy = titleStr.substring(7, endIdx)
            }
        }

        val html = description ?: ""
        val imageUrls = mutableListOf<String>()
        val srcRegex = """src="([^"]+)"""".toRegex()
        val matches = srcRegex.findAll(html)
        for (match in matches) {
            val url = match.groupValues[1]
            if (url.contains("/pic/") || url.endsWith(".jpg") || url.endsWith(".png")) {
                imageUrls.add(url)
            }
        }
        
        // Strip HTML tags and unescape common entities
        val contentText = html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

        return FeedItem(
            id = id,
            title = titleStr,
            author = creator ?: username,
            authorUsername = username,
            contentText = contentText,
            imageUrls = imageUrls,
            pubDate = pubDate,
            link = link ?: "",
            retweetedBy = retweetedBy
        )
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
