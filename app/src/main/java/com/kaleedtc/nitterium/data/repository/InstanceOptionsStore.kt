package com.kaleedtc.nitterium.data.repository

import android.webkit.CookieManager
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.frontend.InstanceSettingsCookies
import com.kaleedtc.nitterium.data.model.NitterInstanceSettings

interface InstanceOptionsStore {
    fun read(url: String, frontend: FrontendType): NitterInstanceSettings
    fun write(url: String, frontend: FrontendType, settings: NitterInstanceSettings)
}

/** Supported instance-option cookies, including legacy backup fields. */
class CookieInstanceOptionsStore : InstanceOptionsStore {
    override fun read(url: String, frontend: FrontendType): NitterInstanceSettings =
        InstanceSettingsCookies.read(CookieManager.getInstance().getCookie(url).orEmpty(), frontend)

    fun ensureDefaults(url: String, frontend: FrontendType) {
        writeCookies(url, InstanceSettingsCookies.missingDefaults(CookieManager.getInstance().getCookie(url).orEmpty(), frontend))
    }

    fun ensureProxyDefaults(url: String) {
        writeCookies(url, InstanceSettingsCookies.missingProxyDefaults(CookieManager.getInstance().getCookie(url).orEmpty()))
    }

    fun setOption(url: String, frontend: FrontendType, key: String, value: Boolean): Boolean {
        val cookie = InstanceSettingsCookies.cookieKey(key, frontend) ?: return false
        writeCookies(url, mapOf(cookie to value))
        return true
    }

    override fun write(url: String, frontend: FrontendType, settings: NitterInstanceSettings) {
        writeCookies(url, InstanceSettingsCookies.values(settings, frontend))
    }

    private fun writeCookies(url: String, values: Map<String, Boolean>) {
        if (values.isEmpty()) return
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        values.forEach { (key, value) ->
            manager.setCookie(url, cookieValue(key, value))
        }
        manager.flush()
    }

    private fun cookieValue(key: String, value: Boolean) =
        "$key=${if (value) "on" else "off"}; Path=/; SameSite=Lax"
}
