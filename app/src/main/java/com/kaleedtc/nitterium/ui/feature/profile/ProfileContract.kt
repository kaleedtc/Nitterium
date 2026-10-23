package com.kaleedtc.nitterium.ui.feature.profile

import com.kaleedtc.nitterium.data.frontend.FrontendType

sealed interface ProfileEvent {
    data object EditGroups : ProfileEvent
    data object DismissGroups : ProfileEvent
    data class SaveGroups(val groupIds: List<String>) : ProfileEvent
    data class LoadProfile(val username: String) : ProfileEvent
    data class ToggleSubscription(val username: String) : ProfileEvent
    data class FrontendDetected(val url: String, val type: FrontendType) : ProfileEvent
    data class OnPageStarted(val url: String) : ProfileEvent
    data class OnPageFinished(val url: String) : ProfileEvent
    data class OnPageError(val errorCode: Int, val description: String) : ProfileEvent
    data class OnAvatarFound(val pageUrl: String, val url: String) : ProfileEvent
    data class ConnectivityChanged(val isConnected: Boolean) : ProfileEvent
    data object Refresh : ProfileEvent
    data object ClearError : ProfileEvent
    data object NavigateBack : ProfileEvent
    data object ConfirmUnsubscribe : ProfileEvent
    data object DismissUnsubscribeDialog : ProfileEvent
}

data class ProfileState(
    val username: String = "",
    val currentUrl: String = "",
    val selectedInstanceUrl: String = "https://xitter.cf",
    val displayedPageUrl: String? = null,
    val frontend: FrontendType = FrontendType.NITTER,
    val avatarUrl: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val groups: List<com.kaleedtc.nitterium.data.model.FeedGroup> = emptyList(),
    val groupAssignmentIsSubscribed: Boolean = false,
    val groupAssignment: com.kaleedtc.nitterium.data.model.Subscription? = null,
    val isSavingGroups: Boolean = false,
    val isSubscribed: Boolean = false,
    val isTrueBlack: Boolean = false,
    val isSiteHeaderEnabled: Boolean = false,
    val isBlockDirectXEnabled: Boolean = false,
    val showUnsubscribeDialog: Boolean = false,
    val isConnected: Boolean = true
)

sealed interface ProfileEffect {
    data class ShowMessage(val resourceId: Int) : ProfileEffect
    data class ShowSnackbar(val message: String) : ProfileEffect
    data object NavigateBack : ProfileEffect
}
