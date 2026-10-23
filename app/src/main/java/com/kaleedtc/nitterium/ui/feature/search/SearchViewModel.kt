package com.kaleedtc.nitterium.ui.feature.search

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.frontend.FrontendAdapter
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.data.repository.FeedGroupRepository
import com.kaleedtc.nitterium.data.ConnectivityMonitor
import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.kaleedtc.nitterium.data.security.UrlPolicy

class SearchViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val groupRepository: FeedGroupRepository
) : MviViewModel<SearchState, SearchEvent, SearchEffect>(SearchState()) {

    init {
        viewModelScope.launch {
            preferencesRepository.frontend.collect { type -> setState { copy(frontend = type) } }
        }
        viewModelScope.launch {
            groupRepository.groups.collect { groups -> setState { copy(groups = groups) } }
        }
        viewModelScope.launch {
            subscriptionRepository.subscriptions.collect { subscriptions ->
                setState {
                    copy(isSubscribed = subscriptions.any { it.username.equals(currentUsername, ignoreCase = true) })
                }
            }
        }
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
        viewModelScope.launch {
            preferencesRepository.blockDirectX.collect { enabled ->
                setState { copy(isBlockDirectXEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.instanceUrl.collect { newInstanceUrl ->
                setState {
                    val moved = if (selectedInstanceUrl != newInstanceUrl) {
                        UrlPolicy.switchInstance(selectedInstanceUrl, newInstanceUrl, currentUrl)
                    } else null
                    copy(
                        selectedInstanceUrl = newInstanceUrl,
                        currentUrl = moved ?: currentUrl,
                        isError = if (moved != null) false else isError,
                        errorMessage = if (moved != null) null else errorMessage,
                        isLoading = if (moved != null) isConnected else isLoading,
                        avatarUrl = if (moved != null) null else avatarUrl
                    )
                }
            }
        }
    }

    fun loadUsername(username: String) {
        viewModelScope.launch {
            val instanceUrl = preferencesRepository.instanceUrl.first().trimEnd('/')
            val url = FrontendAdapter.forType(state.value.frontend).profileUrl(instanceUrl, username)
            setState { copy(currentUrl = url, selectedInstanceUrl = instanceUrl, searchQuery = username, isSearchBarActive = false) }
        }
    }

    override fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.FrontendDetected -> viewModelScope.launch {
                preferencesRepository.recordDetectedFrontend(event.url, event.type)
            }
            SearchEvent.EditGroups -> editGroups()
            SearchEvent.DismissGroups -> if (!state.value.isSavingGroups) setState { copy(groupAssignment = null) }
            is SearchEvent.SaveGroups -> saveGroups(event.groupIds)
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
                setState { copy(isLoading = true, currentUrl = event.url, isError = false, errorMessage = null) }
                checkIfProfile(event.url)
            }
            is SearchEvent.OnPageFinished -> {
                setState { copy(isLoading = false, isRefreshing = false, currentUrl = event.url) }
                checkIfProfile(event.url)
            }
            is SearchEvent.OnPageError -> {
                setState { copy(isLoading = false, isRefreshing = false, isError = true,
                    errorMessage = event.description.takeIf { event.errorCode == 503 }) }
            }
            is SearchEvent.OnAvatarFound -> {
                val snapshot = state.value
                if (snapshot.currentUrl != event.pageUrl ||
                    !UrlPolicy.sameOrigin(snapshot.selectedInstanceUrl, event.pageUrl)) return
                val username = UrlPolicy.profileUsername(event.pageUrl, snapshot.selectedInstanceUrl) ?: return
                val avatar = UrlPolicy.mediaUrl(event.url, snapshot.isBlockDirectXEnabled) ?: return
                if (!username.equals(snapshot.currentUsername, ignoreCase = true)) return
                setState { copy(avatarUrl = avatar) }
                // The checked username is captured before launching persistence work.
                viewModelScope.launch {
                    subscriptionRepository.updateSubscriptionAvatar(username, avatar)
                }
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
            if (cleanQuery.startsWith("@") && !Regex("[A-Za-z0-9_]{1,15}").matches(cleanQuery.substring(1))) {
                setEffect { SearchEffect.ShowSnackbar("Enter a valid @username") }
                return@launch
            }
            
            val url = if (cleanQuery.startsWith("@")) {
                // Direct profile navigation
                FrontendAdapter.forType(state.value.frontend).profileUrl(instanceUrl, cleanQuery.substring(1))
            } else {
                // Standard search
                FrontendAdapter.forType(state.value.frontend).searchUrl(instanceUrl, cleanQuery)
            }
            
            setState { 
                copy(
                    currentUrl = url, 
                    selectedInstanceUrl = instanceUrl,
                    searchQuery = query,
                    isSearchBarActive = false,
                    isError = false,
                    isLoading = state.value.isConnected
                ) 
            }
        }
    }

    private fun processDeepLink(deepLinkUrl: String) {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val instanceUrl = preferencesRepository.instanceUrl.first()
                val newUrl = UrlPolicy.rewriteDeepLink(instanceUrl, deepLinkUrl)
                if (newUrl == null) {
                    setState { copy(isLoading = false) }
                    setEffect { SearchEffect.ShowSnackbar("Unsupported link") }
                    return@launch
                }
                setState { copy(currentUrl = newUrl, selectedInstanceUrl = instanceUrl, isError = false) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                setState { copy(isLoading = false) }
                setEffect { SearchEffect.ShowSnackbar("Could not open link") }
            }
        }
    }

    private fun checkIfProfile(url: String) {
        viewModelScope.launch {
            try {
                val instanceUrl = preferencesRepository.instanceUrl.first()
                if (state.value.currentUrl != url) return@launch

                if (UrlPolicy.sameOrigin(instanceUrl, url)) {
                    val username = UrlPolicy.profileUsername(url, instanceUrl)
                    if (username != null) {
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

    private fun editGroups() {
        val username = state.value.currentUsername ?: return
        if (username.isBlank() || state.value.groupAssignment != null) return
        val avatarUrl = state.value.avatarUrl
        viewModelScope.launch {
            val subscriptions = subscriptionRepository.subscriptions.first()
            val groups = groupRepository.groups.first()
            val subscription = subscriptions.firstOrNull { it.username.equals(username, ignoreCase = true) }
                ?: Subscription(username, preferencesRepository.instanceUrl.first(), avatarUrl)
            // Navigation may have changed the displayed account while data was loading.
            if (state.value.currentUsername == username) {
                setState {
                    copy(
                        groupAssignment = subscription,
                        groups = groups,
                        groupAssignmentIsSubscribed = subscriptions.any {
                            it.username.equals(username, ignoreCase = true)
                        }
                    )
                }
            }
        }
    }

    private fun saveGroups(groupIds: List<String>) {
        val subscription = state.value.groupAssignment ?: return
        if (state.value.isSavingGroups) return
        setState { copy(isSavingGroups = true) }
        viewModelScope.launch {
            val groups = groupRepository.groups.first()
            val validIds = groupIds.filter { id -> groups.any { it.id == id } }
            subscriptionRepository.saveSubscriptionGroups(subscription, validIds).fold(
                onSuccess = {
                    setState { copy(groupAssignment = null, isSavingGroups = false) }
                    setEffect { SearchEffect.ShowMessage(R.string.groups_saved) }
                },
                onFailure = {
                    setState { copy(isSavingGroups = false) }
                    setEffect { SearchEffect.ShowMessage(R.string.groups_save_failed) }
                }
            )
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
