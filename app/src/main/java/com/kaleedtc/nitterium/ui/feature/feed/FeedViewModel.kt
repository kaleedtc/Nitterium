package com.kaleedtc.nitterium.ui.feature.feed

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
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
        
        // Observe subscriptions and instanceUrl to construct the feed URL
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                subscriptionRepository.subscriptions,
                preferencesRepository.instanceUrl
            ) { subscriptions, instanceUrl ->
                Pair(subscriptions, instanceUrl)
            }.collect { (subscriptions, instanceUrl) ->
                val groupId = state.value.groupId
                val filteredSubscriptions = if (groupId == null) {
                    subscriptions
                } else {
                    subscriptions.filter { it.groupIds.contains(groupId) }
                }

                if (filteredSubscriptions.isEmpty()) {
                    setState { 
                        copy(
                            hasSubscriptions = false, 
                            currentUrl = "", 
                            isLoading = false, 
                            isError = false
                        ) 
                    }
                } else {
                    val usernames = filteredSubscriptions.joinToString(",") { it.username }
                    val url = "${instanceUrl.trimEnd('/')}/$usernames"
                    
                    if (state.value.currentUrl != url) {
                        setState { 
                            copy(
                                hasSubscriptions = true,
                                currentUrl = url,
                                isError = false,
                                isLoading = isConnected
                            ) 
                        }
                    }
                }
            }
        }
    }

    fun setGroup(id: String?, name: String?) {
        setState { copy(groupId = id, groupName = name) }
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
