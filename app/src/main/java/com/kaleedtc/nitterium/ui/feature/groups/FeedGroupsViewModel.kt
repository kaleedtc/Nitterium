package com.kaleedtc.nitterium.ui.feature.groups

import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.repository.FeedGroupRepository
import com.kaleedtc.nitterium.data.repository.SubscriptionRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FeedGroupsViewModel(
    private val groupRepository: FeedGroupRepository,
    private val subscriptionRepository: SubscriptionRepository
) : MviViewModel<FeedGroupsState, FeedGroupsEvent, FeedGroupsEffect>(FeedGroupsState()) {

    init {
        viewModelScope.launch {
            combine(
                groupRepository.groups,
                subscriptionRepository.subscriptions
            ) { groups, subscriptions ->
                val counts = groups.associate { group ->
                    group.id to subscriptions.count { it.groupIds.contains(group.id) }
                }
                FeedGroupsState(
                    groups = groups,
                    subscriptionCounts = counts,
                    totalSubscriptionsCount = subscriptions.size,
                    isLoading = false
                )
            }.collect { newState ->
                setState { newState }
            }
        }
    }

    override fun onEvent(event: FeedGroupsEvent) {
        when (event) {
            is FeedGroupsEvent.AddGroup -> {
                viewModelScope.launch {
                    groupRepository.addGroup(event.name, icon = event.icon, color = event.color)
                }
            }
            is FeedGroupsEvent.UpdateGroup -> {
                viewModelScope.launch {
                    groupRepository.updateGroup(com.kaleedtc.nitterium.data.model.FeedGroup(event.id, event.name, event.icon, event.color))
                }
            }
            is FeedGroupsEvent.DeleteGroup -> {
                viewModelScope.launch {
                    groupRepository.removeGroup(event.groupId)
                }
            }
            is FeedGroupsEvent.OpenGroup -> {
                setEffect { FeedGroupsEffect.NavigateToGroupFeed(event.groupId, event.groupName) }
            }
            is FeedGroupsEvent.OpenAllFeed -> {
                setEffect { FeedGroupsEffect.NavigateToAllFeed }
            }
        }
    }
}
