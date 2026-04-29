package com.kaleedtc.nitterium.ui.feature.search

data class SearchState(
    val currentUrl: String = "",
    val searchQuery: String = "",
    val isSearchBarActive: Boolean = false,
    val isLoading: Boolean = false, 
    val isRefreshing: Boolean = false,
    val isError: Boolean = false,
    val canGoBack: Boolean = false,
    val currentUsername: String? = null, 
    val isSubscribed: Boolean = false,
    val avatarUrl: String? = null,
    val isTrueBlack: Boolean = false,
    val isSiteHeaderEnabled: Boolean = false,
    val isBlockDirectXEnabled: Boolean = true,
    val useSystemFont: Boolean = false,
    val showUnsubscribeDialog: Boolean = false,
    val isConnected: Boolean = true
)

sealed interface SearchEvent {
    data class UpdateQuery(val query: String) : SearchEvent
    data class PerformSearch(val query: String) : SearchEvent
    data class ToggleSearchBar(val active: Boolean) : SearchEvent
    data class ConnectivityChanged(val isConnected: Boolean) : SearchEvent
    
    data class LoadUrl(val url: String) : SearchEvent
    data class ProcessDeepLink(val url: String) : SearchEvent
    data class OnPageStarted(val url: String) : SearchEvent
    data class OnPageFinished(val url: String) : SearchEvent
    data class OnPageError(val errorCode: Int, val description: String) : SearchEvent
    data class OnAvatarFound(val url: String) : SearchEvent
    data class ToggleSubscription(val username: String) : SearchEvent
    object Refresh : SearchEvent
    object ClearError : SearchEvent
    object ConfirmUnsubscribe : SearchEvent
    object DismissUnsubscribeDialog : SearchEvent
}

sealed interface SearchEffect {
    data class ShowSnackbar(val message: String) : SearchEffect
    data class NavigateToUrl(val url: String) : SearchEffect
}
