package com.kaleedtc.nitterium.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class SubscriptionSortOrder {
    MANUAL,
    USERNAME,
    SUBSCRIPTION_DATE;

    fun sort(subscriptions: List<Subscription>): List<Subscription> = when (this) {
        MANUAL -> subscriptions
        USERNAME -> subscriptions.sortedWith { first, second ->
            first.username.compareTo(second.username, ignoreCase = true)
        }
        SUBSCRIPTION_DATE -> subscriptions.sortedByDescending { it.addedAt }
    }

    companion object {
        fun fromPreference(value: String?): SubscriptionSortOrder =
            entries.firstOrNull { it.name == value } ?: MANUAL
    }
}
