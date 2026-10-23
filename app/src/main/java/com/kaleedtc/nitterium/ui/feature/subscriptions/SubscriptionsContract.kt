package com.kaleedtc.nitterium.ui.feature.subscriptions

import com.kaleedtc.nitterium.data.model.Subscription
import com.kaleedtc.nitterium.data.model.SubscriptionSortOrder

data class SubscriptionsState(
    val subscriptions: List<Subscription> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: SubscriptionSortOrder = SubscriptionSortOrder.MANUAL,
    val groups: List<com.kaleedtc.nitterium.data.model.FeedGroup> = emptyList(),
    val subscriptionToDelete: Subscription? = null
) {
    val canReorder: Boolean
        get() = sortOrder == SubscriptionSortOrder.MANUAL && searchQuery.isBlank()
}

sealed interface SubscriptionsEvent {
    data class SearchQueryChanged(val query: String) : SubscriptionsEvent
    data class RequestDelete(val subscription: Subscription) : SubscriptionsEvent
    data object ConfirmDelete : SubscriptionsEvent
    data object CancelDelete : SubscriptionsEvent
    data class SortOrderChanged(val order: SubscriptionSortOrder) : SubscriptionsEvent
    data class ReorderSubscriptions(val fromIndex: Int, val toIndex: Int) : SubscriptionsEvent
    data object SaveSubscriptionOrder : SubscriptionsEvent
    data class UpdateSubscriptionGroups(val username: String, val groupIds: List<String>) : SubscriptionsEvent
}

sealed interface SubscriptionsEffect {
    data class ShowSnackbar(val message: String) : SubscriptionsEffect
}
