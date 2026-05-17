package com.kaleedtc.nitterium.data.model

data class FeedItem(
    val id: String,
    val title: String,
    val author: String,
    val authorUsername: String,
    val contentText: String,
    val imageUrls: List<String>,
    val pubDate: Long,
    val link: String,
    val retweetedBy: String? = null
)
