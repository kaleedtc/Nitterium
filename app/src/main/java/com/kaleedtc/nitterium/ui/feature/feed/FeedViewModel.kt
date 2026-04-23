package com.kaleedtc.nitterium.ui.feature.feed

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FeedViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val connectivityMonitor: ConnectivityMonitor
) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState()) {

    init {
        viewModelScope.launch {
            connectivityMonitor.isConnected.collect { isConnected ->
                onEvent(FeedEvent.ConnectivityChanged(isConnected))
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
        
        // Observe subscriptions to construct the feed URL
        viewModelScope.launch {
            subscriptionRepository.subscriptions.collect { subscriptions ->
                if (subscriptions.isEmpty()) {
                    setState { 
                        copy(
                            hasSubscriptions = false, 
                            currentUrl = "", 
                            isLoading = false, 
                            isError = false
                        ) 
                    }
                } else {
                    val instanceUrl = preferencesRepository.instanceUrl.first().trimEnd('/')
                    val usernames = subscriptions.joinToString(",") { it.username }
                    val url = "$instanceUrl/$usernames"
                    
                    setState { 
                        copy(
                            hasSubscriptions = true,
                            currentUrl = url,
                            // only trigger loading if we change the URL substantially or first load
                            isLoading = currentUrl != url && isConnected
                        ) 
                    }
                }
            }
        }
    }

    override fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.OnPageStarted -> {
                setState { copy(isLoading = true, isError = false) }
            }
            is FeedEvent.OnPageFinished -> {
                setState { copy(isLoading = false, isRefreshing = false) }
            }
            is FeedEvent.OnPageError -> {
                setState { copy(isLoading = false, isRefreshing = false, isError = true) }
            }
            is FeedEvent.ConnectivityChanged -> {
                setState { copy(isConnected = event.isConnected) }
                if (!event.isConnected) {
                    setState { copy(isLoading = false, isRefreshing = false) }
                }
                if (event.isConnected && state.value.isError) {
                    setState { copy(isError = false) }
                }
            }
            is FeedEvent.Refresh -> {
                setState { copy(isRefreshing = state.value.isConnected, isError = false) }
            }
            is FeedEvent.ClearError -> setState { copy(isError = false) }
        }
    }
}
