package com.kaleedtc.nitterium.data.model

import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

data class SubscriptionImport(
    val subscriptions: List<Subscription>,
    val groups: List<FeedGroup> = emptyList(),
    val skipped: Int = 0
)

data class ImportReport(val added: Int, val duplicates: Int, val skipped: Int)

/** Converts document formats without touching storage or fetching profile metadata. */
object SubscriptionImportParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val usernamePattern = Regex("[A-Za-z0-9_]{1,15}")

    fun parse(content: String, instanceUrl: String, now: Long = System.currentTimeMillis()): SubscriptionImport {
        ImportLimits.validate(content)
        val root = json.parseToJsonElement(content)
        if (root is JsonObject && "format" in root) {
            val backup = BackupCodec.decode(content, instanceUrl)
            return SubscriptionImport(
                requireNotNull(backup.subscriptions) { "This backup contains settings only" },
                backup.groups.orEmpty()
            )
        }
        if (root is JsonArray) {
            val subscriptions = json.decodeFromJsonElement<List<Subscription>>(root)
            require(subscriptions.all { usernamePattern.matches(it.username) }) { "Invalid username" }
            return SubscriptionImport(subscriptions)
        }
        require(root is JsonObject && root["subscriptions"] is JsonArray) { "Unsupported subscriptions file" }
        val users = root.getValue("subscriptions").jsonArray
        val sourceGroups = (root["subscriptionGroups"] as? JsonArray).orEmpty()
        val groupsBySourceId = sourceGroups.mapNotNull { value ->
            val group = value as? JsonObject ?: return@mapNotNull null
            val id = group.string("id") ?: return@mapNotNull null
            val name = group.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val localId = UUID.nameUUIDFromBytes("quax-group:$id".toByteArray(Charsets.UTF_8)).toString()
            val color = group.string("color")?.toLongOrNull()?.let { "#%08X".format(it and 0xffffffffL) }
            id to FeedGroup(localId, name, color = color)
        }.toMap()
        val memberships = (root["subscriptionGroupMembers"] as? JsonArray).orEmpty().mapNotNull { value ->
            val member = value as? JsonObject ?: return@mapNotNull null
            val groupId = groupsBySourceId[member.string("group_id")]?.id ?: return@mapNotNull null
            val profileId = member.string("profile_id") ?: return@mapNotNull null
            profileId to groupId
        }.groupBy({ it.first }, { it.second })
        var skipped = (root["searchSubscriptions"] as? JsonArray)?.size ?: 0
        val subscriptions = users.mapNotNull { value ->
            val user = value as? JsonObject
            val username = user?.string("screen_name")?.trim()?.removePrefix("@")
            if (user == null || username == null || !usernamePattern.matches(username)) {
                skipped++
                return@mapNotNull null
            }
            val created = user.string("created_at")?.let {
                runCatching {
                    LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .toInstant(ZoneOffset.UTC).toEpochMilli()
                }.getOrNull()
            } ?: now
            Subscription(
                username = username,
                instanceUrl = instanceUrl,
                // Refresh avatars through the selected instance instead of importing direct X URLs.
                addedAt = created,
                groupIds = memberships[user.string("id")].orEmpty().distinct()
            )
        }
        return SubscriptionImport(subscriptions, groupsBySourceId.values.toList(), skipped)
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
}
