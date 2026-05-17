package com.kaleedtc.nitterium.ui.feature.feed

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.data.repository.FeedRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FeedViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val feedRepository: FeedRepository
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
        viewModelScope.launch {
            preferencesRepository.useSystemFont.collect { enabled ->
                setState { copy(useSystemFont = enabled) }
            }
        }
        
        viewModelScope.launch {
            feedRepository.feedItems.collect { items ->
                setState { 
                    copy(
                        items = items,
                        isLoading = false,
                        isRefreshing = false
                    ) 
                }
            }
        }

        viewModelScope.launch {
            feedRepository.avatarCache.collect { avatars ->
                setState { copy(avatars = avatars) }
            }
        }
        
        // Observe subscriptions to construct the feed
        viewModelScope.launch {
            subscriptionRepository.subscriptions.collect { subscriptions ->
                if (subscriptions.isEmpty()) {
                    setState { 
                        copy(
                            hasSubscriptions = false, 
                            items = emptyList(),
                            isLoading = false, 
                            isError = false
                        ) 
                    }
                } else {
                    setState { copy(hasSubscriptions = true) }
                    
                    // Update avatar cache with known avatars from subscriptions
                    subscriptions.forEach { sub ->
                        sub.avatarUrl?.let { url ->
                            feedRepository.updateAvatar(sub.username, url)
                        }
                    }
                    
                    fetchFeeds()
                    discoverMissingAvatars(subscriptions)
                }
            }
        }
    }

    private fun discoverMissingAvatars(subscriptions: List<com.kaleedtc.nitterium.data.model.Subscription>) {
        val missing = subscriptions.filter { sub ->
            sub.avatarUrl == null && !feedRepository.avatarCache.value.containsKey(sub.username.lowercase())
        }
        
        if (missing.isNotEmpty() && state.value.isConnected) {
            viewModelScope.launch {
                val instanceUrl = preferencesRepository.instanceUrl.first().trimEnd('/')
                missing.forEach { sub ->
                    // Fetch individual feed to discover avatar via RssFeedParser
                    feedRepository.fetchFeeds(instanceUrl, listOf(sub.username), clearFirst = false)
                }
            }
        }
    }

    private fun fetchFeeds() {
        if (!state.value.isConnected) return
        
        viewModelScope.launch {
            setState { copy(isLoading = true, isError = false) }
            try {
                val instanceUrl = preferencesRepository.instanceUrl.first().trimEnd('/')
                val subscriptions = subscriptionRepository.subscriptions.first()
                val usernames = subscriptions.map { it.username }
                
                feedRepository.fetchFeeds(instanceUrl, usernames, clearFirst = true)
            } catch (_: Exception) {
                setState { copy(isError = true, isLoading = false, isRefreshing = false) }
            }
        }
    }

    override fun onEvent(event: FeedEvent) {
        when (event) {
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
                fetchFeeds()
            }
            is FeedEvent.ClearError -> setState { copy(isError = false) }
        }
    }
}
