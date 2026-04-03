package com.kaleedtc.nitterium.ui.feature.subscriptions

import android.net.Uri
import com.kaleedtc.nitterium.data.model.Subscription

data class SubscriptionsState(
    val subscriptions: List<Subscription> = emptyList(),
    val isRefreshing: Boolean = false,
    val subscriptionToDelete: Subscription? = null
)

sealed interface SubscriptionsEvent {
    data class RequestDelete(val subscription: Subscription) : SubscriptionsEvent
    data object ConfirmDelete : SubscriptionsEvent
    data object CancelDelete : SubscriptionsEvent
    data object Refresh : SubscriptionsEvent
    data object ExportClicked : SubscriptionsEvent
    data object ImportClicked : SubscriptionsEvent
    data class SubscriptionsExported(val uri: Uri?) : SubscriptionsEvent
    data class SubscriptionsImported(val uri: Uri?) : SubscriptionsEvent
    data class ReorderSubscriptions(val fromIndex: Int, val toIndex: Int) : SubscriptionsEvent
    data object SaveSubscriptionOrder : SubscriptionsEvent
}

sealed interface SubscriptionsEffect {
    data class ShowSnackbar(val message: String) : SubscriptionsEffect
    data object LaunchExportIntent : SubscriptionsEffect
    data object LaunchImportIntent : SubscriptionsEffect
}