package com.kaleedtc.nitterium.ui.feature.settings

import com.kaleedtc.nitterium.data.model.NitterInstanceSettings

data class SettingsState(
    val instanceUrl: String = "https://nitter.net",
    val isDynamicColor: Boolean = true,
    val isTrueBlack: Boolean = false,
    val isSiteHeaderEnabled: Boolean = false,
    val isNavLabelsEnabled: Boolean = true,
    val isBlockDirectXEnabled: Boolean = true,
    val isDarkTheme: Boolean? = null, // null = System
    val availableInstances: List<String> = emptyList(),
    val customInstances: List<String> = emptyList(),
    val instanceSettings: NitterInstanceSettings = NitterInstanceSettings(),
    val appVersion: String = ""
)

sealed interface SettingsEvent {
    data class UpdateInstanceUrl(val url: String) : SettingsEvent
    data class AddCustomInstance(val url: String) : SettingsEvent
    data class RemoveCustomInstance(val url: String) : SettingsEvent
    data class UpdateDynamicColor(val enabled: Boolean) : SettingsEvent
    data class UpdateTrueBlack(val enabled: Boolean) : SettingsEvent
    data class UpdateSiteHeader(val enabled: Boolean) : SettingsEvent
    data class UpdateNavLabels(val enabled: Boolean) : SettingsEvent
    data class UpdateBlockDirectX(val enabled: Boolean) : SettingsEvent
    data class UpdateDarkTheme(val isDark: Boolean?) : SettingsEvent
    data class UpdateInstanceSetting(val key: String, val value: Boolean) : SettingsEvent
    data class OpenUrl(val url: String) : SettingsEvent
}

sealed interface SettingsEffect {
    data class NavigateToUrl(val url: String) : SettingsEffect
}
