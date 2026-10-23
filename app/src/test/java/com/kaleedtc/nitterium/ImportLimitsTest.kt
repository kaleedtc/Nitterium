package com.kaleedtc.nitterium

import com.kaleedtc.nitterium.data.model.BackupCodec
import com.kaleedtc.nitterium.data.model.ImportLimits
import com.kaleedtc.nitterium.data.model.SubscriptionImportParser
import java.io.Reader
import org.junit.Assert.*
import org.junit.Test

class ImportLimitsTest {
    @Test fun oversizedProviderStreamStopsBeforeParsingOrReadingToEnd() {
        var read = 0
        val source = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                buffer.fill(' ', offset, offset + length)
                read += length
                return length
            }
            override fun close() = Unit
        }
        assertThrows(IllegalArgumentException::class.java) { ImportLimits.read(source) }
        assertTrue(read <= ImportLimits.MAX_CHARACTERS + 8192)
    }

    @Test fun bothParsersRejectOversizedDirectInput() {
        val oversized = " ".repeat(ImportLimits.MAX_CHARACTERS + 1)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(oversized, "https://instance.test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionImportParser.parse(oversized, "https://instance.test")
        }
    }

    @Test fun hostileNestingIsRejectedButBracketsInsideStringsAreNotCounted() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("[".repeat(10000) + "]".repeat(10000), "https://instance.test")
        }
        ImportLimits.validate("\"" + "[".repeat(10000) + "\"")
        val content = """[{"username":"alice","instanceUrl":"https://instance.test"}]"""
        assertEquals("alice", SubscriptionImportParser.parse(content, "https://instance.test").subscriptions.single().username)
        assertEquals("[]", ImportLimits.read("[]".reader()))
    }
}
