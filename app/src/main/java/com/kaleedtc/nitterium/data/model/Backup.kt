package com.kaleedtc.nitterium.data.model

import com.kaleedtc.nitterium.data.frontend.FrontendType
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class BackupScope { SUBSCRIPTIONS, SUBSCRIPTIONS_AND_GROUPS, SETTINGS, EVERYTHING }

@Serializable
data class AppSettingsBackup(
    val instanceUrl: String = "https://xitter.cf",
    val dynamicColor: Boolean = true,
    val darkTheme: Boolean? = null,
    val trueBlack: Boolean = false,
    val siteHeader: Boolean = false,
    val navLabels: Boolean = true,
    val defaultTab: String = "Search",
    val blockDirectX: Boolean = false,
    val customInstances: Set<String> = emptySet(),
    val frontendModes: Map<String, FrontendType> = emptyMap(),
    val detectedFrontends: Map<String, FrontendType> = emptyMap(),
    val instanceOptions: Map<String, InstanceOptionsBackup> = emptyMap(),
    val showReleaseNotes: Boolean = true,
    val subscriptionSortOrder: SubscriptionSortOrder = SubscriptionSortOrder.MANUAL
)

@Serializable
data class InstanceOptionsBackup(
    val frontend: FrontendType,
    val options: NitterInstanceSettings
)

@Serializable
data class NitteriumBackup(
    val format: String = "nitterium-backup",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val subscriptions: List<Subscription>? = null,
    val groups: List<FeedGroup>? = null,
    val settings: AppSettingsBackup? = null,
    // Only legacy imports can have skipped records. This is shown in the preview.
    val skipped: Int = 0
)

/** Versioned backups and legacy subscription files share one validation path. */
object BackupCodec {
    const val MAX_CHARACTERS = ImportLimits.MAX_CHARACTERS
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val username = Regex("[A-Za-z0-9_]{1,15}")

    fun encode(backup: NitteriumBackup, scope: BackupScope): String {
        val selected = when (scope) {
            BackupScope.SUBSCRIPTIONS -> backup.copy(
                subscriptions = backup.subscriptions.orEmpty().map { it.copy(groupIds = emptyList()) },
                groups = null, settings = null
            )
            BackupScope.SUBSCRIPTIONS_AND_GROUPS -> backup.copy(settings = null)
            BackupScope.SETTINGS -> backup.copy(subscriptions = null, groups = null)
            BackupScope.EVERYTHING -> backup
        }
        validate(selected)
        // Retain compatibility with the existing subscriptions Import action.
        val content = if (scope == BackupScope.SUBSCRIPTIONS) json.encodeToString(selected.subscriptions!!)
        else json.encodeToString(selected)
        require(content.length <= MAX_CHARACTERS) { "Backup file is too large" }
        return content
    }

    fun decode(content: String, instanceUrl: String): NitteriumBackup {
        ImportLimits.validate(content)
        val root = json.parseToJsonElement(content)
        val backup = if (root is JsonObject && "format" in root) {
            require(root["version"]?.jsonPrimitive?.intOrNull == 1) { "Unsupported backup version" }
            json.decodeFromJsonElement(NitteriumBackup.serializer(), root)
        } else {
            val legacy = SubscriptionImportParser.parse(content, instanceUrl)
            NitteriumBackup(subscriptions = legacy.subscriptions, groups = legacy.groups, skipped = legacy.skipped)
        }
        validate(backup)
        return backup
    }

    fun validate(backup: NitteriumBackup) {
        require(backup.format == "nitterium-backup" && backup.version == 1) { "Unsupported backup format" }
        require(backup.subscriptions != null || backup.settings != null) { "The backup contains no supported data" }
        require(backup.groups == null || backup.subscriptions != null) { "Groups require subscriptions" }
        require(backup.subscriptions.orEmpty().all { username.matches(it.username) }) { "Invalid subscription" }
        val groups = backup.groups.orEmpty()
        require(groups.all { it.id.isNotBlank() && it.name.isNotBlank() }) { "Invalid group" }
        require(groups.map { it.id }.distinct().size == groups.size) { "Duplicate group IDs" }
        backup.settings?.let { settings ->
            require(settings.defaultTab in setOf("Search", "Subscriptions", "Feed", "Settings")) { "Invalid default tab" }
            val urls = settings.customInstances + settings.instanceUrl + settings.frontendModes.keys +
                settings.detectedFrontends.keys + settings.instanceOptions.keys
            require(urls.all(::isInstanceUrl)) { "Invalid instance URL" }
            require(settings.detectedFrontends.values.none { it == FrontendType.AUTO }) { "Invalid detected frontend" }
            require(settings.instanceOptions.values.none { it.frontend == FrontendType.AUTO }) { "Invalid instance options" }
        }
    }

    private fun isInstanceUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
