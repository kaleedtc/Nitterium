package com.kaleedtc.nitterium.ui.feature.search

import com.kaleedtc.nitterium.data.frontend.FrontendType

data class SearchState(
    val currentUrl: String = "",
    val selectedInstanceUrl: String = "https://xitter.cf",
    val frontend: FrontendType = FrontendType.NITTER,
    val searchQuery: String = "",
    val isSearchBarActive: Boolean = false,
    val isLoading: Boolean = false, 
    val isRefreshing: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val canGoBack: Boolean = false,
    val currentUsername: String? = null, 
    val groups: List<com.kaleedtc.nitterium.data.model.FeedGroup> = emptyList(),
    val groupAssignmentIsSubscribed: Boolean = false,
    val groupAssignment: com.kaleedtc.nitterium.data.model.Subscription? = null,
    val isSavingGroups: Boolean = false,
    val isSubscribed: Boolean = false,
    val avatarUrl: String? = null,
    val isTrueBlack: Boolean = false,
    val isSiteHeaderEnabled: Boolean = false,
    val isBlockDirectXEnabled: Boolean = false,
    val showUnsubscribeDialog: Boolean = false,
    val isConnected: Boolean = true
)

sealed interface SearchEvent {
    data object EditGroups : SearchEvent
    data object DismissGroups : SearchEvent
    data class SaveGroups(val groupIds: List<String>) : SearchEvent
    data class UpdateQuery(val query: String) : SearchEvent
    data class PerformSearch(val query: String) : SearchEvent
    data class ToggleSearchBar(val active: Boolean) : SearchEvent
    data class ConnectivityChanged(val isConnected: Boolean) : SearchEvent
    
    data class LoadUrl(val url: String) : SearchEvent
    data class ProcessDeepLink(val url: String) : SearchEvent
    data class FrontendDetected(val url: String, val type: FrontendType) : SearchEvent
    data class OnPageStarted(val url: String) : SearchEvent
    data class OnPageFinished(val url: String) : SearchEvent
    data class OnPageError(val errorCode: Int, val description: String) : SearchEvent
    data class OnAvatarFound(val pageUrl: String, val url: String) : SearchEvent
    data class ToggleSubscription(val username: String) : SearchEvent
    object Refresh : SearchEvent
    object ClearError : SearchEvent
    object ConfirmUnsubscribe : SearchEvent
    object DismissUnsubscribeDialog : SearchEvent
}

sealed interface SearchEffect {
    data class ShowMessage(val resourceId: Int) : SearchEffect
    data class ShowSnackbar(val message: String) : SearchEffect
    data class NavigateToUrl(val url: String) : SearchEffect
}
