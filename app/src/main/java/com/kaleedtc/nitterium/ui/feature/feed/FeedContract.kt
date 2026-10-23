package com.kaleedtc.nitterium.ui.feature.feed

import com.kaleedtc.nitterium.data.frontend.FrontendType

data class FeedState(
    val frontend: FrontendType = FrontendType.NITTER,
    val currentUrl: String = "",
    val displayedPageUrl: String? = null,
    val selectedInstanceUrl: String = "https://xitter.cf",
    val groupId: String? = null,
    val groupName: String? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
    val isConnected: Boolean = true,
    val hasSubscriptions: Boolean = true,
    val isTrueBlack: Boolean = false,
    val isSiteHeaderEnabled: Boolean = false,
    val isBlockDirectXEnabled: Boolean = false
)

sealed interface FeedEvent {
    data class FrontendDetected(val url: String, val type: FrontendType) : FeedEvent
    data class OnPageStarted(val url: String) : FeedEvent
    data class OnPageFinished(val url: String) : FeedEvent
    data class OnPageError(val description: String? = null) : FeedEvent
    data class ConnectivityChanged(val isConnected: Boolean) : FeedEvent
    object Refresh : FeedEvent
    object ClearError : FeedEvent
}

sealed interface FeedEffect {
    data class ShowSnackbar(val message: String) : FeedEffect
}
