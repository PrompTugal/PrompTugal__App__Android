package ai.riskmandate.app.llm

/**
 * A strict parser for the one shape tool arguments may have: a flat JSON object (RFC 8259) whose values are
 * strings, numbers, true, false or null. Android's org.json is lenient (single quotes, unquoted words, comments,
 * a repeated key silently overwrites), which is wrong for text a model wrote, so tool calls use this instead
 * (security checklist I.2: strict, bounded size and depth).
 *
 * Integers (no fraction or exponent) become Long, other numbers Double. Nested objects or arrays, repeated keys,
 * trailing commas, control characters in strings and anything after the closing brace are errors.
 */
object StrictJson {
    class Error(message: String) : Exception(message)

    fun parseFlatObject(text: String, maxChars: Int = ToolSpec.MAX_ARGS_CHARS): Map<String, Any?> {
        if (text.length > maxChars) throw Error("longer than $maxChars characters")
        return Parser(text).run {
            ws()
            val m = obj()
            ws()
            if (i != s.length) throw Error("text after the object")
            m
        }
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i] in " \t\r\n") i++
        }

        fun expect(c: Char) {
            if (i >= s.length || s[i] != c) throw Error("expected '$c' at $i")
            i++
        }

        fun obj(): Map<String, Any?> {
            expect('{')
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') {
                i++
                return m
            }
            while (true) {
                ws()
                val key = string()
                if (key in m) throw Error("repeated key")
                ws()
                expect(':')
                ws()
                m[key] = value()
                ws()
                if (i >= s.length) throw Error("unterminated object")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw Error("expected ',' or '}' at $i")
                }
            }
        }

        fun value(): Any? {
            if (i >= s.length) throw Error("missing value")
            return when (val c = s[i]) {
                '"' -> string()
                '{', '[' -> throw Error("nested values are not allowed")
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else throw Error("unexpected character at $i")
            }
        }

        fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw Error("unexpected word at $i")
            i += w.length
            return v
        }

        fun number(): Any {
            val m = NUMBER.matchAt(s, i) ?: throw Error("bad number at $i")
            i += m.value.length
            val t = m.value
            return if (t.any { it == '.' || it == 'e' || it == 'E' }) {
                t.toDouble().takeIf { it.isFinite() } ?: throw Error("number out of range")
            } else {
                t.toLongOrNull() ?: throw Error("integer out of range")
            }
        }

        fun string(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) throw Error("unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return b.toString()
                    c < ' ' -> throw Error("control character in string")
                    c == '\\' -> {
                        if (i >= s.length) throw Error("unterminated escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> b.append(e)
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000C')
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw Error("bad \\u escape")
                                val hex = s.substring(i, i + 4)
                                b.append(hex.toIntOrNull(16)?.toChar() ?: throw Error("bad \\u escape"))
                                i += 4
                            }
                            else -> throw Error("bad escape")
                        }
                    }
                    else -> b.append(c)
                }
            }
        }
    }

    private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
}
