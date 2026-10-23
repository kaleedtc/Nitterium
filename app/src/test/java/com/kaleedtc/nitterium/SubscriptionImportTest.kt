package com.kaleedtc.nitterium

import com.kaleedtc.nitterium.data.model.SubscriptionImportParser
import org.junit.Assert.*
import org.junit.Test

class SubscriptionImportTest {
    @Test fun importsQuaXAccountsAndGroupMembershipsById() {
        val file = """{
          "subscriptions":[
            {"id":"42","screen_name":"Alice","name":"Alice","created_at":"2024-01-02 03:04:05","profile_image_url_https":"https://pbs.twimg.com/avatar"},
            {"id":"43","screen_name":"bob"},
            {"id":"44","screen_name":"not a username"}
          ],
          "subscriptionGroups":[{"id":"news","name":"News","color":4294901760}],
          "subscriptionGroupMembers":[{"group_id":"news","profile_id":"42"},{"group_id":"missing","profile_id":"43"}],
          "searchSubscriptions":[{"id":"android"}],
          "accounts":[{"auth_header":"must not be imported"}]
        }"""
        val imported = SubscriptionImportParser.parse(file, "https://custom.example", now = 123)
        assertEquals(listOf("Alice", "bob"), imported.subscriptions.map { it.username })
        assertEquals(2, imported.skipped)
        assertEquals("#FFFF0000", imported.groups.single().color)
        assertEquals(listOf(imported.groups.single().id), imported.subscriptions.first().groupIds)
        assertTrue(imported.subscriptions.last().groupIds.isEmpty())
        assertEquals(1704164645000L, imported.subscriptions.first().addedAt)
        assertEquals(123L, imported.subscriptions.last().addedAt)
        assertTrue(imported.subscriptions.all { it.instanceUrl == "https://custom.example" && it.avatarUrl == null })
        assertEquals(imported.groups, SubscriptionImportParser.parse(file, "https://custom.example").groups)
    }

    @Test fun preservesNativeExportFieldsAndHandlesOptionalQuaXSections() {
        val native = SubscriptionImportParser.parse("""[{"username":"alice","instanceUrl":"https://old.example","addedAt":42,"avatarUrl":"https://old.example/avatar","groupIds":["news"]}]""", "https://new.example")
        assertEquals("https://old.example", native.subscriptions.single().instanceUrl)
        assertEquals(listOf("news"), native.subscriptions.single().groupIds)
        assertEquals(42L, native.subscriptions.single().addedAt)
        assertTrue(SubscriptionImportParser.parse("""{"subscriptions":[],"subscriptionGroups":null}""", "https://example.com").subscriptions.isEmpty())
    }

    @Test fun rejectsUnknownFormatsInsteadOfClaimingSuccessfulEmptyImport() {
        listOf("{}", "null", "{", """{"accounts":[]}""").forEach { file ->
            assertTrue(runCatching { SubscriptionImportParser.parse(file, "https://example.com") }.isFailure)
        }
    }
}
