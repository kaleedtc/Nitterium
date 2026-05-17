package com.kaleedtc.nitterium.ui.feature.feed

import com.kaleedtc.nitterium.data.model.FeedItem

data class FeedState(
    val items: List<FeedItem> = emptyList(),
    val avatars: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val isRefreshing: Boolean = false,
    val isConnected: Boolean = true,
    val hasSubscriptions: Boolean = true,
    val isTrueBlack: Boolean = false,
    val isSiteHeaderEnabled: Boolean = false,
    val isBlockDirectXEnabled: Boolean = true,
    val useSystemFont: Boolean = false,
)

sealed interface FeedEvent {
    data class ConnectivityChanged(val isConnected: Boolean) : FeedEvent
    object Refresh : FeedEvent
    object ClearError : FeedEvent
}

sealed interface FeedEffect {
    data class ShowSnackbar(val message: String) : FeedEffect
}
