package com.kaleedtc.nitterium.ui.feature.search

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SearchViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val connectivityMonitor: ConnectivityMonitor
) : MviViewModel<SearchState, SearchEvent, SearchEffect>(SearchState()) {

    init {
        viewModelScope.launch {
            connectivityMonitor.isConnected.collect { isConnected ->
                onEvent(SearchEvent.ConnectivityChanged(isConnected))
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
    }

    fun loadUsername(username: String) {
        viewModelScope.launch {
            val instanceUrl = preferencesRepository.instanceUrl.first().trimEnd('/')
            val url = "$instanceUrl/$username"
            setState { copy(currentUrl = url, searchQuery = username, isSearchBarActive = false) }
        }
    }

    override fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.UpdateQuery -> {
                setState { copy(searchQuery = event.query) }
            }
            is SearchEvent.ToggleSearchBar -> {
                setState { copy(isSearchBarActive = event.active) }
            }
            is SearchEvent.ConnectivityChanged -> {
                setState { copy(isConnected = event.isConnected) }
                if (!event.isConnected) {
                    setState { copy(isLoading = false, isRefreshing = false) }
                }
                if (event.isConnected && state.value.isError) {
                    setState { copy(isError = false) }
                }
            }
            is SearchEvent.PerformSearch -> {
                performSearch(event.query)
            }
            is SearchEvent.LoadUrl -> {
                setState { copy(currentUrl = event.url, isError = false) }
            }
            is SearchEvent.ProcessDeepLink -> {
                processDeepLink(event.url)
            }
            is SearchEvent.OnPageStarted -> {
                setState { copy(isLoading = true, currentUrl = event.url, isError = false) }
                checkIfProfile(event.url)
            }
            is SearchEvent.OnPageFinished -> {
                setState { copy(isLoading = false, isRefreshing = false, currentUrl = event.url) }
                checkIfProfile(event.url)
            }
            is SearchEvent.OnPageError -> {
                setState { copy(isLoading = false, isRefreshing = false, isError = true) }
            }
            is SearchEvent.OnAvatarFound -> {
                setState { copy(avatarUrl = event.url) }
            }
            is SearchEvent.ToggleSubscription -> {
                toggleSubscription(event.username)
            }
            SearchEvent.Refresh -> {
                setState { copy(isRefreshing = state.value.isConnected, isError = false) }
            }
            SearchEvent.ClearError -> {
                setState { copy(isError = false) }
            }
            SearchEvent.ConfirmUnsubscribe -> unsubscribe()
            SearchEvent.DismissUnsubscribeDialog -> setState { copy(showUnsubscribeDialog = false) }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch {
            val instanceUrl = preferencesRepository.instanceUrl.first().trimEnd('/')
            val cleanQuery = query.trim()
            
            val url = if (cleanQuery.startsWith("@")) {
                // Direct profile navigation
                "$instanceUrl/${cleanQuery.substring(1)}"
            } else {
                // Standard search
                val encodedQuery = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.toString())
                "$instanceUrl/search?f=tweets&q=$encodedQuery"
            }
            
            setState { 
                copy(
                    currentUrl = url, 
                    searchQuery = query,
                    isSearchBarActive = false,
                    isError = false,
                    isLoading = state.value.isConnected
                ) 
            }
        }
    }

    private fun processDeepLink(deepLinkUrl: String) {
        viewModelScope.launch {
            try {
                val instanceUrl = preferencesRepository.instanceUrl.first()
                val deepLinkUri = URI(deepLinkUrl)
                val path = deepLinkUri.path
                val query = deepLinkUri.query
                
                val newUrl = buildString {
                    append(instanceUrl.trimEnd('/'))
                    if (path.isNotEmpty()) append(path)
                    if (!query.isNullOrEmpty()) append("?$query")
                }
                
                setState { copy(currentUrl = newUrl, isError = false) }
            } catch (e: Exception) {
                 e.printStackTrace()
                 val instanceUrl = preferencesRepository.instanceUrl.first()
                 setState { copy(currentUrl = instanceUrl) }
            }
        }
    }

    private fun checkIfProfile(url: String) {
        viewModelScope.launch {
            try {
                val instanceUrl = preferencesRepository.instanceUrl.first()
                val uri = URI(url)
                val instanceUri = URI(instanceUrl)

                if (uri.host == instanceUri.host) {
                    val path = uri.path.trim('/')
                    val segments = path.split('/')
                    
                    val reservedWords = setOf("search", "settings", "about", "pic", "status")
                    
                    if (segments.isNotEmpty() && !reservedWords.contains(segments[0])) {
                        val username = segments[0]
                        val isSubscribed = subscriptionRepository.isSubscribed(username)
                        setState { 
                            if (currentUsername != username) {
                                copy(currentUsername = username, isSubscribed = isSubscribed, avatarUrl = null)
                            } else {
                                copy(isSubscribed = isSubscribed)
                            }
                        }
                    } else {
                         setState { 
                             if (currentUsername != null) {
                                 copy(currentUsername = null, isSubscribed = false, avatarUrl = null)
                             } else {
                                 this
                             }
                         }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                setState { 
                    if (currentUsername != null) {
                        copy(currentUsername = null, avatarUrl = null)
                    } else {
                        this
                    }
                }
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
        setEffect { SearchEffect.ShowSnackbar("Subscribed to $username") }
        setState { copy(isSubscribed = true) }
    }

    private fun unsubscribe() {
        viewModelScope.launch {
            state.value.currentUsername?.let { username ->
                subscriptionRepository.removeSubscription(username)
                setEffect { SearchEffect.ShowSnackbar("Unsubscribed from $username") }
                setState { copy(isSubscribed = false, showUnsubscribeDialog = false) }
            }
        }
    }
}