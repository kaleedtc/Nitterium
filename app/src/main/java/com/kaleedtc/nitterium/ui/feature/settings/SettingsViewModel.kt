package com.kaleedtc.nitterium.ui.feature.settings

import android.app.Application
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.frontend.InstanceSettingsCookies
import com.kaleedtc.nitterium.data.repository.CookieInstanceOptionsStore
import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.data.model.NitterInstanceSettings
import com.kaleedtc.nitterium.data.model.ReleaseNotesPreferences
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.data.repository.BackupRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val application: Application,
    private val backupRepository: BackupRepository
) : MviViewModel<SettingsState, SettingsEvent, SettingsEffect>(SettingsState()) {

    private val availableInstances = application.resources.getStringArray(R.array.default_instance_urls).toList()

    private val instanceOptions = CookieInstanceOptionsStore()
    private val _instanceSettings = MutableStateFlow(NitterInstanceSettings())
    private val _appVersion = try {
        val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        packageInfo.versionName ?: "Unknown"
    } catch (_: Exception) {
        "Unknown"
    }

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.instanceUrl,
                preferencesRepository.dynamicColor,
                preferencesRepository.trueBlack,
                preferencesRepository.enableSiteHeader,
                preferencesRepository.showNavLabels,
                preferencesRepository.defaultTab,
                preferencesRepository.darkTheme,
                preferencesRepository.blockDirectX,
                preferencesRepository.customInstances,
                _instanceSettings,
                preferencesRepository.frontendMode,
                preferencesRepository.frontend,
                preferencesRepository.releaseNotesPreferences
            ) { args ->
                val url = args[0] as String
                val dynamic = args[1] as Boolean
                val trueBlack = args[2] as Boolean
                val siteHeader = args[3] as Boolean
                val showLabels = args[4] as Boolean
                val defaultTab = args[5] as String
                val dark = args[6] as Boolean?
                val blockDirectX = args[7] as Boolean
                @Suppress("UNCHECKED_CAST")
                val customInstances = (args[8] as Set<String>).toList()
                val instanceSettings = args[9] as NitterInstanceSettings

                val allInstances = availableInstances + customInstances

                SettingsState(
                    frontendMode = args[10] as FrontendType,
                    frontend = args[11] as FrontendType,
                    isLoading = false,
                    instanceUrl = url,
                    isDynamicColor = dynamic,
                    isTrueBlack = trueBlack,
                    isSiteHeaderEnabled = siteHeader,
                    isNavLabelsEnabled = showLabels,
                    isBlockDirectXEnabled = blockDirectX,
                    defaultTab = defaultTab,
                    isDarkTheme = dark,
                    availableInstances = allInstances,
                    customInstances = customInstances,
                    instanceSettings = instanceSettings,
                    appVersion = _appVersion,
                    showReleaseNotesAfterUpdates = (args[12] as ReleaseNotesPreferences).enabled
                )
            }.collect { newState ->
                setState { newState.copy(isBackupBusy = isBackupBusy, backupToRestore = backupToRestore) }
            }
        }

        // Load settings when URL changes
        viewModelScope.launch {
            combine(preferencesRepository.instanceUrl, preferencesRepository.frontend) { url, frontend -> url to frontend }
                .collect { (url, frontend) -> loadSettingsFromCookies(url, frontend) }
        }
    }

    private fun loadSettingsFromCookies(url: String, frontend: FrontendType) {
        instanceOptions.ensureDefaults(url, frontend)
        _instanceSettings.value = instanceOptions.read(url, frontend)
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ExportBackup -> if (!state.value.isBackupBusy) {
                setEffect { SettingsEffect.LaunchBackupExport(event.scope) }
            }
            is SettingsEvent.ImportBackup -> if (!state.value.isBackupBusy) {
                setEffect { SettingsEffect.LaunchBackupImport }
            }
            is SettingsEvent.BackupDestinationChosen -> event.uri?.let { uri ->
                performBackupOperation(R.string.backup_export_failed) {
                    backupRepository.export(uri, event.scope, availableInstances).getOrThrow()
                    setEffect { SettingsEffect.ShowSnackbar(application.getString(R.string.backup_export_success)) }
                }
            }
            is SettingsEvent.BackupFileChosen -> event.uri?.let { uri ->
                performBackupOperation(R.string.backup_read_failed) {
                    val backup = backupRepository.read(uri).getOrThrow()
                    setState { copy(backupToRestore = backup) }
                }
            }
            is SettingsEvent.CancelRestoreBackup -> if (!state.value.isBackupBusy) {
                setState { copy(backupToRestore = null) }
            }
            is SettingsEvent.ConfirmRestoreBackup -> state.value.backupToRestore?.let { backup ->
                performBackupOperation(R.string.backup_restore_failed) {
                    backupRepository.restore(backup).getOrThrow()
                    setState { copy(backupToRestore = null) }
                    loadSettingsFromCookies(preferencesRepository.instanceUrl.first(), preferencesRepository.frontend.first())
                    setEffect { SettingsEffect.ShowSnackbar(application.getString(R.string.backup_restore_success)) }
                }
            }
            is SettingsEvent.UpdateFrontend -> viewModelScope.launch { preferencesRepository.setFrontend(event.type) }
            is SettingsEvent.OpenUrl -> {
                setEffect { SettingsEffect.NavigateToUrl(event.url) }
            }
            is SettingsEvent.UpdateInstanceUrl -> {
                if (com.kaleedtc.nitterium.data.security.UrlPolicy.httpsUrl(event.url) == null) {
                    setEffect { SettingsEffect.ShowSnackbar("Use an HTTPS instance URL without credentials") }
                    return
                }
                viewModelScope.launch {
                    preferencesRepository.setInstanceUrl(event.url)
                }
            }
            is SettingsEvent.AddCustomInstance -> {
                if (com.kaleedtc.nitterium.data.security.UrlPolicy.httpsUrl(event.url) == null) {
                    setEffect { SettingsEffect.ShowSnackbar("Use an HTTPS instance URL without credentials") }
                    return
                }
                viewModelScope.launch {
                    preferencesRepository.addCustomInstance(event.url)
                    // Optionally set it as current
                    preferencesRepository.setInstanceUrl(event.url)
                }
            }
            is SettingsEvent.RemoveCustomInstance -> {
                viewModelScope.launch {
                    preferencesRepository.removeCustomInstance(event.url)
                    // If the current URL is removed, fall back to the default instance.
                    if (state.value.instanceUrl == event.url) {
                        preferencesRepository.setInstanceUrl("https://xitter.cf")
                    }
                }
            }
            is SettingsEvent.UpdateDynamicColor -> {
                viewModelScope.launch {
                    preferencesRepository.setDynamicColor(event.enabled)
                }
            }
            is SettingsEvent.UpdateTrueBlack -> {
                viewModelScope.launch {
                    preferencesRepository.setTrueBlack(event.enabled)
                }
            }
            is SettingsEvent.UpdateSiteHeader -> {
                viewModelScope.launch {
                    preferencesRepository.setEnableSiteHeader(event.enabled)
                }
            }
            is SettingsEvent.UpdateNavLabels -> {
                viewModelScope.launch {
                    preferencesRepository.setShowNavLabels(event.enabled)
                }
            }
            is SettingsEvent.UpdateDefaultTab -> {
                viewModelScope.launch {
                    preferencesRepository.setDefaultTab(event.tab)
                }
            }
            is SettingsEvent.UpdateBlockDirectX -> {
                viewModelScope.launch {
                    preferencesRepository.setBlockDirectX(event.enabled)
                }
            }
            is SettingsEvent.UpdateDarkTheme -> {
                viewModelScope.launch {
                    preferencesRepository.setDarkTheme(event.isDark)
                }
            }
            is SettingsEvent.UpdateShowReleaseNotes -> {
                viewModelScope.launch {
                    preferencesRepository.setShowReleaseNotes(event.enabled)
                }
            }
            is SettingsEvent.UpdateInstanceSetting -> {
                val updated = InstanceSettingsCookies.update(_instanceSettings.value, event.key, event.value) ?: return
                if (instanceOptions.setOption(state.value.instanceUrl, state.value.frontend, event.key, event.value)) {
                    _instanceSettings.value = updated
                }
            }
        }
    }

    private fun performBackupOperation(failureMessage: Int, action: suspend () -> Unit) {
        if (state.value.isBackupBusy) return
        setState { copy(isBackupBusy = true) }
        viewModelScope.launch {
            try {
                action()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                setEffect { SettingsEffect.ShowSnackbar(application.getString(failureMessage)) }
            } finally {
                setState { copy(isBackupBusy = false) }
            }
        }
    }
}
