package ai.riskmandate.app.features.lab

import java.security.MessageDigest

/**
 * A strict, bounded JSON parser (RFC 8259): no comments, no trailing commas, no single quotes, no leading
 * zeros, no duplicate keys, control characters refused inside strings, nesting capped at [MAX_DEPTH] and
 * input at a caller-given size. Values: Map<String, Any?>, List<Any?>, String, Long, Double, Boolean, null.
 * Used for model output (tool calls) and for the scenario and recording files, so both are held to one rule.
 */
object StrictJson {
    const val MAX_DEPTH = 12
    const val MAX_CHARS = 2_000_000

    class JsonException(message: String) : Exception(message)

    fun parse(text: String, maxChars: Int = MAX_CHARS): Any? {
        if (text.length > maxChars) throw JsonException("input longer than $maxChars characters")
        val p = Parser(text)
        p.skipWs()
        val v = p.value(0)
        p.skipWs()
        if (p.i != text.length) throw JsonException("unexpected characters after the value at ${p.i}")
        return v
    }

    /** Parses one value starting exactly at [start]; returns it and the index just after it. */
    fun parseAt(text: String, start: Int): Pair<Any?, Int> {
        val p = Parser(text)
        p.i = start
        val v = p.value(0)
        return v to p.i
    }

    /** Compact text for showing a value (not a parser input): strings quoted, keys in order. */
    fun render(v: Any?): String = when (v) {
        null -> "null"
        is String -> quote(v)
        is Map<*, *> -> v.entries.joinToString(", ", "{", "}") { quote(it.key.toString()) + ": " + render(it.value) }
        is List<*> -> v.joinToString(", ", "[", "]") { render(it) }
        else -> v.toString()
    }

    /** Canonical form (sorted keys, no spaces, non-ASCII kept) as the eval harness hashes it. */
    fun canonical(v: Any?): String = when (v) {
        is Map<*, *> -> v.entries.sortedBy { it.key.toString() }.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + canonical(it.value) }
        is List<*> -> v.joinToString(",", "[", "]") { canonical(it) }
        else -> render(v)
    }

    /** First 16 hex characters of the SHA-256 of [canonical], as the harness's scenario hash. */
    fun hash16(v: Any?): String =
        MessageDigest.getInstance("SHA-256").digest(canonical(v).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c == '\b' -> sb.append("\\b")
            c == '\u000c' -> sb.append("\\f")
            c < ' ' -> sb.append("\\u%04x".format(c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    private class Parser(val s: String) {
        var i = 0

        fun skipWs() { while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++ }

        fun fail(msg: String): Nothing = throw JsonException("$msg at $i")

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("nesting deeper than $MAX_DEPTH")
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("unexpected '$c'")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("bad literal")
            i += word.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            i++ // {
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') fail("expected a key")
                val k = str()
                if (k in out) fail("duplicate key '$k'")
                skipWs()
                if (i >= s.length || s[i] != ':') fail("expected ':'")
                i++
                skipWs()
                out[k] = value(depth + 1)
                skipWs()
                if (i >= s.length) fail("unexpected end")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> { i--; fail("expected ',' or '}'") }
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            i++ // [
            val out = ArrayList<Any?>()
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                skipWs()
                out += value(depth + 1)
                skipWs()
                if (i >= s.length) fail("unexpected end")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> { i--; fail("expected ',' or ']'") }
                }
            }
        }

        private fun str(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c < ' ' -> { i--; fail("control character in string") }
                    c == '\\' -> {
                        if (i >= s.length) fail("unterminated escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("short \\u escape")
                                val hex = s.substring(i, i + 4)
                                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("bad \\u escape")
                                sb.append(hex.toInt(16).toChar()); i += 4
                            }
                            else -> { i--; fail("bad escape '\\$e'") }
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            if (i >= s.length) fail("bad number")
            if (s[i] == '0') i++ else if (s[i] in '1'..'9') { while (i < s.length && s[i] in '0'..'9') i++ } else fail("bad number")
            var integer = true
            if (i < s.length && s[i] == '.') {
                integer = false; i++
                val d = i; while (i < s.length && s[i] in '0'..'9') i++
                if (i == d) fail("bad fraction")
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                integer = false; i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                val d = i; while (i < s.length && s[i] in '0'..'9') i++
                if (i == d) fail("bad exponent")
            }
            val t = s.substring(start, i)
            return if (integer && t.length <= 18) t.toLong() else t.toDouble()
        }
    }
}
