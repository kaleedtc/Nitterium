package com.kaleedtc.nitterium.ui.feature.feed

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.frontend.FrontendAdapter
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import com.kaleedtc.nitterium.data.security.UrlPolicy

class FeedViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val connectivityMonitor: ConnectivityMonitor
) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState()) {
    private val selectedGroupId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            preferencesRepository.frontend.collect { frontend -> setState { copy(frontend = frontend) } }
        }
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
                preferencesRepository.instanceUrl,
                selectedGroupId
            ) { subscriptions, instanceUrl, groupId ->
                val filtered = if (groupId == null) {
                    subscriptions
                } else {
                    subscriptions.filter { it.groupIds.contains(groupId) }
                }
                Pair(filtered, instanceUrl)
            }.collect { (filteredSubscriptions, instanceUrl) ->
                if (filteredSubscriptions.isEmpty()) {
                    setState { 
                        copy(
                            hasSubscriptions = false, 
                            selectedInstanceUrl = instanceUrl,
                            currentUrl = "", 
                            isLoading = false, 
                            isError = false
                        ) 
                    }
                } else {
                    val rootUrl = FrontendAdapter.forType(state.value.frontend)
                        .timelineUrl(instanceUrl, filteredSubscriptions.map { it.username })
                    val snapshot = state.value
                    val changedInstance = snapshot.selectedInstanceUrl != instanceUrl
                    val url = if (changedInstance) {
                        UrlPolicy.switchInstance(snapshot.selectedInstanceUrl, instanceUrl,
                            snapshot.displayedPageUrl ?: snapshot.currentUrl) ?: rootUrl
                    } else rootUrl
                    if (snapshot.currentUrl != url || changedInstance) {
                        setState { 
                            copy(
                                hasSubscriptions = true,
                                selectedInstanceUrl = instanceUrl,
                                currentUrl = url,
                                displayedPageUrl = null,
                                isError = false,
                                errorMessage = null,
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
        selectedGroupId.value = id
    }

    override fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.FrontendDetected -> viewModelScope.launch {
                preferencesRepository.recordDetectedFrontend(event.url, event.type)
            }
            is FeedEvent.OnPageStarted -> {
                setState { copy(isLoading = true, isError = false, errorMessage = null, displayedPageUrl = event.url) }
            }
            is FeedEvent.OnPageFinished -> {
                setState { copy(isLoading = false, isRefreshing = false, displayedPageUrl = event.url) }
            }
            is FeedEvent.OnPageError -> {
                setState { copy(isLoading = false, isRefreshing = false, isError = true, errorMessage = event.description) }
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
