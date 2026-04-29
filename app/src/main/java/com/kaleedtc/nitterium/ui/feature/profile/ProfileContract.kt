package com.kaleedtc.nitterium.ui.feature.profile

sealed interface ProfileEvent {
    data class LoadProfile(val username: String) : ProfileEvent
    data class ToggleSubscription(val username: String) : ProfileEvent
    data class OnPageStarted(val url: String) : ProfileEvent
    data class OnPageFinished(val url: String) : ProfileEvent
    data class OnPageError(val errorCode: Int, val description: String) : ProfileEvent
    data class OnAvatarFound(val url: String) : ProfileEvent
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
    val avatarUrl: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isError: Boolean = false,
    val isSubscribed: Boolean = false,
    val isTrueBlack: Boolean = false,
    val isSiteHeaderEnabled: Boolean = false,
    val isBlockDirectXEnabled: Boolean = true,
    val useSystemFont: Boolean = false,
    val showUnsubscribeDialog: Boolean = false,
    val isConnected: Boolean = true
)

sealed interface ProfileEffect {
    data class ShowSnackbar(val message: String) : ProfileEffect
    data object NavigateBack : ProfileEffect
}
