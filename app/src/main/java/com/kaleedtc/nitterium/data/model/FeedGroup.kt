package com.kaleedtc.nitterium.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FeedGroup(
    val id: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null // Hex string like "#FF0000"
)
