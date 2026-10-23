package com.kaleedtc.nitterium.ui.feature.settings

import com.kaleedtc.nitterium.data.model.NitterInstanceSettings
import com.kaleedtc.nitterium.data.frontend.FrontendType
import android.net.Uri
import com.kaleedtc.nitterium.data.model.BackupScope
import com.kaleedtc.nitterium.data.model.NitteriumBackup

data class SettingsState(
    val frontendMode: FrontendType = FrontendType.AUTO,
    val frontend: FrontendType = FrontendType.NITTER,
    val isLoading: Boolean = true,
    val instanceUrl: String = "https://xitter.cf",
    val isDynamicColor: Boolean = true,
    val isTrueBlack: Boolean = false,
    val isSiteHeaderEnabled: Boolean = false,
    val isNavLabelsEnabled: Boolean = true,
    val isBlockDirectXEnabled: Boolean = false,
    val defaultTab: String = "Search",
    val isDarkTheme: Boolean? = null, // null = System
    val availableInstances: List<String> = emptyList(),
    val customInstances: List<String> = emptyList(),
    val instanceSettings: NitterInstanceSettings = NitterInstanceSettings(),
    val appVersion: String = "",
    val showReleaseNotesAfterUpdates: Boolean = true,
    val isBackupBusy: Boolean = false,
    val backupToRestore: NitteriumBackup? = null
)

sealed interface SettingsEvent {
    data class ExportBackup(val scope: BackupScope) : SettingsEvent
    data class BackupDestinationChosen(val uri: Uri?, val scope: BackupScope) : SettingsEvent
    data object ImportBackup : SettingsEvent
    data class BackupFileChosen(val uri: Uri?) : SettingsEvent
    data object ConfirmRestoreBackup : SettingsEvent
    data object CancelRestoreBackup : SettingsEvent
    data class UpdateFrontend(val type: FrontendType) : SettingsEvent
    data class UpdateInstanceUrl(val url: String) : SettingsEvent
    data class AddCustomInstance(val url: String) : SettingsEvent
    data class RemoveCustomInstance(val url: String) : SettingsEvent
    data class UpdateDynamicColor(val enabled: Boolean) : SettingsEvent
    data class UpdateTrueBlack(val enabled: Boolean) : SettingsEvent
    data class UpdateSiteHeader(val enabled: Boolean) : SettingsEvent
    data class UpdateNavLabels(val enabled: Boolean) : SettingsEvent
    data class UpdateBlockDirectX(val enabled: Boolean) : SettingsEvent
    data class UpdateDefaultTab(val tab: String) : SettingsEvent
    data class UpdateDarkTheme(val isDark: Boolean?) : SettingsEvent
    data class UpdateShowReleaseNotes(val enabled: Boolean) : SettingsEvent
    data class UpdateInstanceSetting(val key: String, val value: Boolean) : SettingsEvent
    data class OpenUrl(val url: String) : SettingsEvent
}

sealed interface SettingsEffect {
    data class LaunchBackupExport(val scope: BackupScope) : SettingsEffect
    data object LaunchBackupImport : SettingsEffect
    data class ShowSnackbar(val message: String) : SettingsEffect
    data class NavigateToUrl(val url: String) : SettingsEffect
}
