package com.kaleedtc.nitterium.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val username: String,
    val instanceUrl: String,
    val avatarUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
