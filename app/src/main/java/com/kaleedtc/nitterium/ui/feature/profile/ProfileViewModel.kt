package com.kaleedtc.nitterium.ui.feature.profile

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URI

class ProfileViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val connectivityMonitor: ConnectivityMonitor
) : MviViewModel<ProfileState, ProfileEvent, ProfileEffect>(ProfileState()) {

    init {
        viewModelScope.launch {
            connectivityMonitor.isConnected.collect { isConnected ->
                onEvent(ProfileEvent.ConnectivityChanged(isConnected))
            }
        }
        viewModelScope.launch {
            preferencesRepository.trueBlack.collect { isTrueBlack ->
                setState { copy(isTrueBlack = isTrueBlack) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.enableSiteHeader.collect { enabled ->
                setState { copy(isSiteHeaderEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.blockDirectX.collect { enabled ->
                setState { copy(isBlockDirectXEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.useSystemFont.collect { enabled ->
                setState { copy(useSystemFont = enabled) }
            }
        }
    }

    override fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.LoadProfile -> loadProfile(event.username)
            is ProfileEvent.ToggleSubscription -> toggleSubscription(event.username)
            is ProfileEvent.OnPageStarted -> {
                setState { copy(isLoading = true, isError = false) }
                checkIfProfile(event.url)
            }
            is ProfileEvent.OnPageFinished -> {
                setState { copy(isLoading = false, isRefreshing = false) }
                checkIfProfile(event.url)
            }
            is ProfileEvent.OnPageError -> setState { copy(isLoading = false, isRefreshing = false, isError = true) }
            is ProfileEvent.OnAvatarFound -> {
                setState { copy(avatarUrl = event.url) }
            }
            is ProfileEvent.ConnectivityChanged -> {
                setState { copy(isConnected = event.isConnected) }
                if (!event.isConnected) {
                    setState { copy(isLoading = false, isRefreshing = false) }
                }
                if (event.isConnected && state.value.isError) {
                    setState { copy(isError = false) }
                }
            }
            is ProfileEvent.Refresh -> {
                setState { copy(isRefreshing = state.value.isConnected, isError = false) }
                // Triggers recomposition in WebView to reload
            }
            is ProfileEvent.ClearError -> setState { copy(isError = false) }
            is ProfileEvent.NavigateBack -> setEffect { ProfileEffect.NavigateBack }
            is ProfileEvent.ConfirmUnsubscribe -> unsubscribe()
            is ProfileEvent.DismissUnsubscribeDialog -> setState { copy(showUnsubscribeDialog = false) }
        }
    }

    private fun loadProfile(username: String) {
        viewModelScope.launch {
            try {
                val instanceUrl = preferencesRepository.instanceUrl.first().trimEnd('/')
                val url = "$instanceUrl/$username"
                val isSubscribed = subscriptionRepository.isSubscribed(username)
                val isConnected = state.value.isConnected
                
                setState { 
                    copy(
                        username = username,
                        currentUrl = url,
                        isSubscribed = isSubscribed,
                        isLoading = isConnected,
                        isError = false,
                        avatarUrl = null // Reset avatar on new load
                    ) 
                }
            } catch (e: Exception) {
                e.printStackTrace()
                setState { copy(isError = true, isLoading = false) }
            }
        }
    }

    private fun checkIfProfile(url: String) {
        viewModelScope.launch {
            try {
                val instanceUrl = preferencesRepository.instanceUrl.first()
                val uri = URI(url)
                val instanceUri = URI(instanceUrl)

                // Simple check: same host
                if (uri.host == instanceUri.host) {
                    val path = uri.path.trim('/')
                    val segments = path.split('/')
                    
                    val reservedWords = setOf("search", "settings", "about", "pic", "status", "pic")
                    
                    if (segments.isNotEmpty() && !reservedWords.contains(segments[0])) {
                        val username = segments[0]
                        val isSubscribed = subscriptionRepository.isSubscribed(username)
                        // Update the displayed username and subscription status
                        setState { copy(username = username, isSubscribed = isSubscribed) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun toggleSubscription(username: String) {
        viewModelScope.launch {
            if (state.value.isSubscribed) {
                setState { copy(showUnsubscribeDialog = true) }
            } else {
                subscribe(username)
            }
        }
    }

    private suspend fun subscribe(username: String) {
        val instanceUrl = preferencesRepository.instanceUrl.first()
        val avatarUrl = state.value.avatarUrl
        val sub = Subscription(username, instanceUrl, avatarUrl)
        subscriptionRepository.addSubscription(sub)
        setEffect { ProfileEffect.ShowSnackbar("Subscribed to $username") }
        setState { copy(isSubscribed = true) }
    }

    private fun unsubscribe() {
        viewModelScope.launch {
            val username = state.value.username
            subscriptionRepository.removeSubscription(username)
            setEffect { ProfileEffect.ShowSnackbar("Unsubscribed from $username") }
            setState { copy(isSubscribed = false, showUnsubscribeDialog = false) }
        }
    }
}