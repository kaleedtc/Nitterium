package com.kaleedtc.nitterium.ui.feature.subscriptions

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import java.io.IOException
import kotlinx.coroutines.flow.combine
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SubscriptionsViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val groupRepository: com.kaleedtc.nitterium.data.repository.FeedGroupRepository,
    private val preferencesRepository: UserPreferencesRepository
) : MviViewModel<SubscriptionsState, SubscriptionsEvent, SubscriptionsEffect>(SubscriptionsState()) {

    init {
        combine(subscriptionRepository.subscriptions, preferencesRepository.subscriptionSortOrder) { subs, order ->
            subs to order
        }
            .onEach { (subs, order) ->
                setState { copy(subscriptions = subs, sortOrder = order) }
            }
            .launchIn(viewModelScope)

        groupRepository.groups
            .onEach { groups ->
                setState { copy(groups = groups) }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: SubscriptionsEvent) {
        when (event) {
            is SubscriptionsEvent.SearchQueryChanged -> setState { copy(searchQuery = event.query) }
            is SubscriptionsEvent.UpdateSubscriptionGroups -> {
                viewModelScope.launch {
                    subscriptionRepository.updateSubscriptionGroups(event.username, event.groupIds)
                }
            }
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
            is SubscriptionsEvent.SortOrderChanged -> {
                viewModelScope.launch {
                    try {
                        preferencesRepository.setSubscriptionSortOrder(event.order)
                    } catch (_: IOException) {
                        setEffect { SubscriptionsEffect.ShowSnackbar("Could not save subscription sort order") }
                    }
                }
            }
            is SubscriptionsEvent.ReorderSubscriptions -> {
                if (!state.value.canReorder) return
                val currentList = state.value.subscriptions.toMutableList()
                if (event.fromIndex in currentList.indices && event.toIndex in currentList.indices) {
                    val item = currentList.removeAt(event.fromIndex)
                    currentList.add(event.toIndex, item)
                    setState { copy(subscriptions = currentList) }
                }
            }
            is SubscriptionsEvent.SaveSubscriptionOrder -> {
                if (!state.value.canReorder) return
                val order = state.value.subscriptions
                viewModelScope.launch {
                    subscriptionRepository.updateSubscriptionOrder(order)
                }
            }
        }
    }
}
