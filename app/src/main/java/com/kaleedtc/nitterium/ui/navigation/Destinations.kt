package com.kaleedtc.nitterium.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class Search(val query: String? = null)

@Serializable
data class Profile(val username: String)

@Serializable
object Subscriptions

@Serializable
object Settings