package com.kaleedtc.nitterium.ui.feature.profile

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

class ProfileViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val groupRepository: FeedGroupRepository
) : MviViewModel<ProfileState, ProfileEvent, ProfileEffect>(ProfileState()) {

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
                    copy(isSubscribed = subscriptions.any { it.username.equals(username, ignoreCase = true) })
                }
            }
        }
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
            preferencesRepository.instanceUrl.collect { newInstanceUrl ->
                setState {
                    val moved = if (selectedInstanceUrl != newInstanceUrl) {
                        UrlPolicy.switchInstance(selectedInstanceUrl, newInstanceUrl, displayedPageUrl ?: currentUrl)
                    } else null
                    val target = moved ?: if (username.isNotEmpty()) {
                        FrontendAdapter.forType(frontend).profileUrl(newInstanceUrl, username)
                    } else currentUrl
                    val changed = target != currentUrl || selectedInstanceUrl != newInstanceUrl
                    copy(
                        selectedInstanceUrl = newInstanceUrl,
                        currentUrl = target,
                        displayedPageUrl = if (changed) null else displayedPageUrl,
                        isError = if (changed) false else isError,
                        errorMessage = if (changed) null else errorMessage,
                        isLoading = if (changed) isConnected else isLoading,
                        avatarUrl = if (changed) null else avatarUrl
                    )
                }
            }
        }
    }

    override fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.FrontendDetected -> viewModelScope.launch {
                preferencesRepository.recordDetectedFrontend(event.url, event.type)
            }
            ProfileEvent.EditGroups -> editGroups()
            ProfileEvent.DismissGroups -> if (!state.value.isSavingGroups) setState { copy(groupAssignment = null) }
            is ProfileEvent.SaveGroups -> saveGroups(event.groupIds)
            is ProfileEvent.LoadProfile -> loadProfile(event.username)
            is ProfileEvent.ToggleSubscription -> toggleSubscription(event.username)
            is ProfileEvent.OnPageStarted -> {
                setState { copy(isLoading = true, isError = false, errorMessage = null, displayedPageUrl = event.url, avatarUrl = null) }
                checkIfProfile(event.url)
            }
            is ProfileEvent.OnPageFinished -> {
                setState { copy(isLoading = false, isRefreshing = false, displayedPageUrl = event.url) }
                checkIfProfile(event.url)
            }
            is ProfileEvent.OnPageError -> setState { copy(isLoading = false, isRefreshing = false, isError = true,
                errorMessage = event.description.takeIf { event.errorCode == 503 }) }
            is ProfileEvent.OnAvatarFound -> {
                val snapshot = state.value
                if (snapshot.displayedPageUrl != event.pageUrl ||
                    !UrlPolicy.sameOrigin(snapshot.selectedInstanceUrl, event.pageUrl)) return
                val username = UrlPolicy.profileUsername(event.pageUrl, snapshot.selectedInstanceUrl) ?: return
                val avatar = UrlPolicy.mediaUrl(event.url, snapshot.isBlockDirectXEnabled) ?: return
                if (!username.equals(snapshot.username, ignoreCase = true)) return
                setState { copy(avatarUrl = avatar) }
                // Persist if already subscribed
                viewModelScope.launch {
                    subscriptionRepository.updateSubscriptionAvatar(username, avatar)
                }
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
                val url = FrontendAdapter.forType(state.value.frontend).profileUrl(instanceUrl, username)
                val isSubscribed = subscriptionRepository.isSubscribed(username)
                val isConnected = state.value.isConnected
                
                setState { 
                    copy(
                        username = username,
                        currentUrl = url,
                        selectedInstanceUrl = instanceUrl,
                        displayedPageUrl = null,
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
                if (state.value.displayedPageUrl != url) return@launch

                if (UrlPolicy.sameOrigin(instanceUrl, url)) {
                    val username = UrlPolicy.profileUsername(url, instanceUrl)
                    if (username != null) {
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

    private fun editGroups() {
        val username = state.value.username
        if (username.isBlank() || state.value.groupAssignment != null) return
        val avatarUrl = state.value.avatarUrl
        viewModelScope.launch {
            val subscriptions = subscriptionRepository.subscriptions.first()
            val groups = groupRepository.groups.first()
            val subscription = subscriptions.firstOrNull { it.username.equals(username, ignoreCase = true) }
                ?: Subscription(username, preferencesRepository.instanceUrl.first(), avatarUrl)
            // Navigation may have changed the displayed account while data was loading.
            if (state.value.username == username) {
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
                    setEffect { ProfileEffect.ShowMessage(R.string.groups_saved) }
                },
                onFailure = {
                    setState { copy(isSavingGroups = false) }
                    setEffect { ProfileEffect.ShowMessage(R.string.groups_save_failed) }
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
