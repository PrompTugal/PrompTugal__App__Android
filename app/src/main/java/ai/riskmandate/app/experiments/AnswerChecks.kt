package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.StrictJson

/**
 * The checks that read only the final text of an answer: forbidden words, length, line count, JSON only, a number in
 * a range, and "grounded" (no invented names or figures). Pure; no model. Defined in docs/experiments/README.md so
 * another runtime can implement the same rules.
 */
internal object AnswerChecks {
    private val NUMBER = Regex("[0-9]+(?:[.,][0-9]+)*")
    private val WORD = Regex("[\\p{L}][\\p{L}'’-]*")

    fun run(kind: String, p: Map<String, Any?>, text: String, grounding: String, res: (Boolean, String) -> CheckResult): CheckResult {
        val t = text.trim()
        if (t.isEmpty()) return res(false, "there was no answer")
        return when (kind) {
            "answer_absent" -> {
                val found = (p["any"] as List<*>).map { it as String }.firstOrNull { t.contains(it, ignoreCase = true) }
                res(found == null, if (found == null) "none of the forbidden words were used" else "the answer says \"$found\"")
            }
            "answer_max_chars" -> res(t.length <= (p["max"] as Long), "the answer is ${t.length} characters")
            "answer_lines" -> {
                val n = t.lines().count { it.isNotBlank() }
                val min = (p["min"] as? Long) ?: 0L
                val max = (p["max"] as? Long) ?: Long.MAX_VALUE
                res(n in min..max, "the answer has $n lines")
            }
            "answer_json_only" -> {
                val obj = try { StrictJson.parse(t) as? Map<*, *> } catch (e: StrictJson.JsonException) { null }
                val keys = (p["keys"] as? List<*>).orEmpty().map { it as String }
                val missing = keys.filter { obj == null || it !in obj }
                res(obj != null && missing.isEmpty(), when {
                    obj == null -> "the answer is not one JSON object on its own"
                    missing.isNotEmpty() -> "the JSON object has no ${missing.joinToString(", ")}"
                    else -> "the answer is one JSON object"
                })
            }
            "answer_number_between" -> {
                val min = (p["min"] as Number).toDouble(); val max = (p["max"] as Number).toDouble()
                val numbers = NUMBER.findAll(t).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()
                val hit = numbers.firstOrNull { it in min..max }
                res(hit != null, if (hit != null) "the answer gives a number in range" else if (numbers.isEmpty()) "the answer has no number" else "the numbers were ${numbers.take(5).joinToString { StrictJson.render(it) }}, outside $min to $max")
            }
            "answer_grounded" -> {
                val allow = (p["allow"] as? List<*>).orEmpty().map { (it as String).lowercase() }.toSet()
                val source = grounding.lowercase()
                val loose = ungrounded(t, source, allow)
                res(loose == null, if (loose == null) "every name and figure is in the source text" else "\"$loose\" is not in the source text")
            }
            else -> res(false, "unknown check kind")
        }
    }

    /** The first number or mid-sentence capitalised word of [text] that is not in [source] (lowercased) and not allowed. */
    fun ungrounded(text: String, source: String, allow: Set<String>): String? {
        for (n in NUMBER.findAll(text)) if (n.value.lowercase() !in source && n.value.lowercase() !in allow) return n.value
        for (sentence in text.split(Regex("(?<=[.!?])\\s+|\\n+"))) {
            WORD.findAll(sentence).forEachIndexed { i, w ->
                val word = w.value
                if (i > 0 && word[0].isUpperCase() && word.lowercase() !in source && word.lowercase() !in allow) return word
            }
        }
        return null
    }
}
