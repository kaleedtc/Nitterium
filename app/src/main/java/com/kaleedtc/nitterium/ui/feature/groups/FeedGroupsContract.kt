package com.kaleedtc.nitterium.ui.feature.groups

import com.kaleedtc.nitterium.data.model.FeedGroup

data class FeedGroupsState(
    val groups: List<FeedGroup> = emptyList(),
    val subscriptionCounts: Map<String, Int> = emptyMap(), // groupId -> count
    val totalSubscriptionsCount: Int = 0,
    val isLoading: Boolean = false
)

sealed interface FeedGroupsEvent {
    data class AddGroup(val name: String, val icon: String? = null, val color: String? = null) : FeedGroupsEvent
    data class UpdateGroup(val id: String, val name: String, val icon: String? = null, val color: String? = null) : FeedGroupsEvent
    data class DeleteGroup(val groupId: String) : FeedGroupsEvent
    data class OpenGroup(val groupId: String, val groupName: String) : FeedGroupsEvent
    object OpenAllFeed : FeedGroupsEvent
}

sealed interface FeedGroupsEffect {
    data class NavigateToGroupFeed(val groupId: String, val groupName: String) : FeedGroupsEffect
    object NavigateToAllFeed : FeedGroupsEffect
}
