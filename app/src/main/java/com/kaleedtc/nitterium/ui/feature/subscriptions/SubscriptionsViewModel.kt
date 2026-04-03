package com.kaleedtc.nitterium.ui.feature.subscriptions

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SubscriptionsViewModel(
    private val subscriptionRepository: SubscriptionRepository
) : MviViewModel<SubscriptionsState, SubscriptionsEvent, SubscriptionsEffect>(SubscriptionsState()) {

    init {
        subscriptionRepository.subscriptions
            .onEach { subs ->
                setState { copy(subscriptions = subs) }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: SubscriptionsEvent) {
        when (event) {
            is SubscriptionsEvent.RequestDelete -> {
                setState { copy(subscriptionToDelete = event.subscription) }
            }
            is SubscriptionsEvent.ConfirmDelete -> {
                viewModelScope.launch {
                    state.value.subscriptionToDelete?.let { sub ->
                        subscriptionRepository.removeSubscription(sub.username)
                        setState { copy(subscriptionToDelete = null) }
                        setEffect { SubscriptionsEffect.ShowSnackbar("Unsubscribed from @${sub.username}") }
                    }
                }
            }
            is SubscriptionsEvent.CancelDelete -> {
                setState { copy(subscriptionToDelete = null) }
            }
            is SubscriptionsEvent.Refresh -> {
                viewModelScope.launch {
                    setState { copy(isRefreshing = true) }
                    // Local DB re-fetches automatically via flow, but we can simulate/ensure sync
                    kotlinx.coroutines.delay(500)
                    setState { copy(isRefreshing = false) }
                }
            }
            is SubscriptionsEvent.ExportClicked -> {
                setEffect { SubscriptionsEffect.LaunchExportIntent }
            }
            is SubscriptionsEvent.ImportClicked -> {
                setEffect { SubscriptionsEffect.LaunchImportIntent }
            }
            is SubscriptionsEvent.SubscriptionsExported -> {
                event.uri?.let { uri ->
                    viewModelScope.launch {
                        subscriptionRepository.exportSubscriptions(uri).fold(
                            onSuccess = {
                                setEffect { SubscriptionsEffect.ShowSnackbar("Subscriptions exported successfully") }
                            },
                            onFailure = {
                                setEffect { SubscriptionsEffect.ShowSnackbar("Failed to export subscriptions") }
                            }
                        )
                    }
                }
            }
            is SubscriptionsEvent.SubscriptionsImported -> {
                event.uri?.let { uri ->
                    viewModelScope.launch {
                        subscriptionRepository.importSubscriptions(uri).fold(
                            onSuccess = { count ->
                                setEffect { SubscriptionsEffect.ShowSnackbar("Imported $count new subscriptions") }
                            },
                            onFailure = {
                                setEffect { SubscriptionsEffect.ShowSnackbar("Failed to import subscriptions") }
                            }
                        )
                    }
                }
            }
            is SubscriptionsEvent.ReorderSubscriptions -> {
                val currentList = state.value.subscriptions.toMutableList()
                if (event.fromIndex in currentList.indices && event.toIndex in currentList.indices) {
                    val item = currentList.removeAt(event.fromIndex)
                    currentList.add(event.toIndex, item)
                    setState { copy(subscriptions = currentList) }
                }
            }
            is SubscriptionsEvent.SaveSubscriptionOrder -> {
                viewModelScope.launch {
                    subscriptionRepository.updateSubscriptionOrder(state.value.subscriptions)
                }
            }
        }
    }
}