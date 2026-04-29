package com.kaleedtc.nitterium.ui.feature.settings

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.data.model.NitterInstanceSettings
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.ui.common.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    application: Application
) : MviViewModel<SettingsState, SettingsEvent, SettingsEffect>(SettingsState()) {

    private val availableInstances = listOf(
        application.getString(R.string.nitter_net_url),
        application.getString(R.string.xcancel_com_url),
        application.getString(R.string.nitter_catsarch_com_url),
        application.getString(R.string.nitter_tiekoetter_com_url),
        application.getString(R.string.nitter_privacyredirect_com_url)
    )

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
                combine(
                    combine(
                        preferencesRepository.instanceUrl,
                        preferencesRepository.dynamicColor,
                        preferencesRepository.trueBlack
                    ) { a, b, c -> listOf(a, b, c) },
                    combine(
                        preferencesRepository.enableSiteHeader,
                        preferencesRepository.showNavLabels,
                        preferencesRepository.useSystemFont
                    ) { a, b, c -> listOf(a, b, c) },
                    combine(
                        preferencesRepository.defaultTab,
                        preferencesRepository.darkTheme,
                        preferencesRepository.blockDirectX
                    ) { a, b, c -> listOf(a, b, c) }
                ) { list1, list2, list3 -> list1 + list2 + list3 },
                combine(
                    _instanceSettings,
                    preferencesRepository.customInstances
                ) { instanceSettings, customInstances -> 
                    listOf(instanceSettings, customInstances) 
                }
            ) { group1, group2 ->
                val url = group1[0] as String
                val dynamic = group1[1] as Boolean
                val trueBlack = group1[2] as Boolean
                val siteHeader = group1[3] as Boolean
                val showLabels = group1[4] as Boolean
                val useSystemFont = group1[5] as Boolean
                val defaultTab = group1[6] as String
                
                val dark = group1[7] as Boolean?
                val blockDirectX = group1[8] as Boolean
                val instanceSettings = group2[0] as NitterInstanceSettings
                val customInstances = group2[1] as Set<*>
                
                val customInstancesList = customInstances.filterIsInstance<String>()
                val allInstances = availableInstances + customInstancesList
                
                SettingsState(
                    instanceUrl = url,
                    isDynamicColor = dynamic,
                    isTrueBlack = trueBlack,
                    isSiteHeaderEnabled = siteHeader,
                    isNavLabelsEnabled = showLabels,
                    isBlockDirectXEnabled = blockDirectX,
                    useSystemFont = useSystemFont,
                    defaultTab = defaultTab,
                    isDarkTheme = dark,
                    availableInstances = allInstances,
                    customInstances = customInstancesList,
                    instanceSettings = instanceSettings,
                    appVersion = _appVersion
                )
            }.collect { newState ->
                setState { newState }
            }
        }

        // Load settings when URL changes
        viewModelScope.launch {
            preferencesRepository.instanceUrl.collect { url ->
                loadSettingsFromCookies(url)
            }
        }
    }

    private fun loadSettingsFromCookies(url: String) {
        val cookieManager = CookieManager.getInstance()
        var cookies = cookieManager.getCookie(url) ?: ""

        var updated = false
        if (!cookies.contains("hlsPlayback=")) {
            cookieManager.setCookie(url, "hlsPlayback=on; Path=/; SameSite=Lax")
            updated = true
        }
        if (!cookies.contains("proxyVideos=")) {
            cookieManager.setCookie(url, "proxyVideos=on; Path=/; SameSite=Lax")
            updated = true
        }
        
        if (updated) {
            cookieManager.flush()
            cookies = cookieManager.getCookie(url) ?: ""
        }

        val settings = NitterInstanceSettings(
            hideTweetStats = cookies.contains("hideTweetStats=on"),
            hideBanner = cookies.contains("hideBanner=on"),
            hidePins = cookies.contains("hidePins=on"),
            hlsPlayback = cookies.contains("hlsPlayback=on"),
            infiniteScroll = cookies.contains("infiniteScroll=on"),
            proxyVideos = cookies.contains("proxyVideos=on"),
            muteVideos = cookies.contains("muteVideos=on"),
            autoplayGifs = cookies.contains("autoplayGifs=on")
        )

        _instanceSettings.value = settings
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OpenUrl -> {
                setEffect { SettingsEffect.NavigateToUrl(event.url) }
            }
            is SettingsEvent.UpdateInstanceUrl -> {
                viewModelScope.launch {
                    preferencesRepository.setInstanceUrl(event.url)
                }
            }
            is SettingsEvent.AddCustomInstance -> {
                viewModelScope.launch {
                    preferencesRepository.addCustomInstance(event.url)
                    // Optionally set it as current
                    preferencesRepository.setInstanceUrl(event.url)
                }
            }
            is SettingsEvent.RemoveCustomInstance -> {
                viewModelScope.launch {
                    preferencesRepository.removeCustomInstance(event.url)
                    // If the current URL is removed, fallback to nitter.net
                    if (state.value.instanceUrl == event.url) {
                        preferencesRepository.setInstanceUrl("https://nitter.net")
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
            is SettingsEvent.UpdateSystemFont -> {
                viewModelScope.launch {
                    preferencesRepository.setUseSystemFont(event.enabled)
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
            is SettingsEvent.UpdateInstanceSetting -> {
                val url = state.value.instanceUrl
                val cookieManager = CookieManager.getInstance()
                val cookieValue = if (event.value) "on" else "off"
                val cookieString = "${event.key}=$cookieValue; Path=/; SameSite=Lax"
                cookieManager.setCookie(url, cookieString)
                cookieManager.flush()

                // Update local state immediately
                val current = _instanceSettings.value
                val newSettings = when (event.key) {
                    "hideTweetStats" -> current.copy(hideTweetStats = event.value)
                    "hideBanner" -> current.copy(hideBanner = event.value)
                    "hidePins" -> current.copy(hidePins = event.value)
                    "hlsPlayback" -> current.copy(hlsPlayback = event.value)
                    "infiniteScroll" -> current.copy(infiniteScroll = event.value)
                    "proxyVideos" -> current.copy(proxyVideos = event.value)
                    "muteVideos" -> current.copy(muteVideos = event.value)
                    "autoplayGifs" -> current.copy(autoplayGifs = event.value)
                    else -> current
                }
                _instanceSettings.value = newSettings
            }
        }
    }
}
