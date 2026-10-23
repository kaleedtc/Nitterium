package com.kaleedtc.nitterium.data.model

data class ReleaseNotesPreferences(
    val enabled: Boolean = true,
    val lastSeenVersionCode: Long = 0
) {
    fun shouldShow(versionCode: Long): Boolean = enabled && versionCode > lastSeenVersionCode
}
