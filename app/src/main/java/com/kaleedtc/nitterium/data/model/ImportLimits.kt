package com.kaleedtc.nitterium.data.model

import java.io.Reader

/** Enforced before allocating a complete document or entering the JSON parser. */
object ImportLimits {
    const val MAX_CHARACTERS = 10_000_000
    private const val MAX_NESTING = 64

    fun read(reader: Reader): String {
        val result = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) return result.toString()
            require(count <= MAX_CHARACTERS - result.length) { "Import file is too large" }
            result.appendRange(buffer, 0, count)
        }
    }

    fun validate(content: String) {
        require(content.length <= MAX_CHARACTERS) { "Import file is too large" }
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in content) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= MAX_NESTING) { "Import is too deeply nested" } }
                '}', ']' -> depth--
                else -> Unit
            }
        }
    }
}
