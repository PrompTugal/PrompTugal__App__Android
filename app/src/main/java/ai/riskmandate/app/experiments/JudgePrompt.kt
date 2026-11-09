package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.StrictJson

/**
 * A judge prompt: a versioned data file (`abp-judge-prompt/1`: format, version, id, system, template). The template
 * has these placeholders and no others: `{{tests}}` (what the case tests), `{{ask}}` (the question for the judge),
 * `{{tools}}` (the offered tools), `{{request}}` (what the user asked) and `{{output}}` (the output under test, as
 * data). The version is recorded with every verdict, so a change of prompt is never mistaken for a change of model.
 */
data class JudgePrompt(val id: String, val version: String, val system: String, val template: String) {
    /** The user message for one case and output. Values are inserted as they are; the template decides where. */
    fun fill(values: Map<String, String>): String {
        var out = template
        for (name in PLACEHOLDERS) out = out.replace("{{$name}}", values[name].orEmpty())
        return out
    }

    companion object {
        const val FORMAT = "abp-judge-prompt/1"
        val PLACEHOLDERS = listOf("tests", "ask", "tools", "request", "output")
        private val ANY = Regex("\\{\\{(\\w+)}}")

        fun parse(text: String): JudgePrompt {
            val m = ToolFormats.parseObject(text)
            if (m["format"] != FORMAT) throw FormatException("format must be $FORMAT")
            fun str(k: String) = (m[k] as? String)?.takeIf { it.isNotBlank() } ?: throw FormatException("'$k' must be text")
            val id = str("id")
            if (!Regex("[a-z][a-z0-9-]{0,39}").matches(id)) throw FormatException("id '$id' is a short lowercase name")
            val template = str("template")
            val unknown = ANY.findAll(template).map { it.groupValues[1] }.filter { it !in PLACEHOLDERS }.toList()
            if (unknown.isNotEmpty()) throw FormatException("unknown placeholder {{${unknown.first()}}}")
            val missing = PLACEHOLDERS.filter { "{{$it}}" !in template }
            if (missing.isNotEmpty()) throw FormatException("the template lacks {{${missing.first()}}}")
            if (str("system").length > 2_000 || template.length > 2_000) throw FormatException("a judge prompt is at most 2,000 characters a part")
            return JudgePrompt(id, str("version"), str("system"), template)
        }
    }
}

/** The judge's reply, read strictly: one JSON object {"verdict": "pass"|"fail"|"unsure", "reason": "..."}; anything else is [JudgeOutcome.UNSURE]. */
object JudgeReply {
    private const val MAX_SCAN = 4_000

    fun parse(text: String, layer: JudgeLayer, modelId: String?, promptVersion: String?): JudgeVerdict {
        fun unsure(why: String) = JudgeVerdict(layer, JudgeOutcome.UNSURE, why, modelId, promptVersion)
        val t = text.take(MAX_SCAN)
        val start = t.indexOf('{')
        if (start < 0) return unsure("the judge did not answer with a JSON object")
        val parsed = try { StrictJson.parseAt(t, start) } catch (e: StrictJson.JsonException) { return unsure("the judge's answer was not valid JSON") }
        // Exactly one object: a second one anywhere after it means the answer is not the one we asked for.
        if (t.indexOf('{', parsed.second) >= 0) return unsure("the judge's answer held more than one JSON object")
        val obj = parsed.first as? Map<*, *> ?: return unsure("the judge's answer was not a JSON object")
        val outcome = when ((obj["verdict"] as? String)?.trim()?.lowercase()) {
            "pass" -> JudgeOutcome.PASS
            "fail" -> JudgeOutcome.FAIL
            "unsure" -> JudgeOutcome.UNSURE
            else -> return unsure("the judge's verdict was not pass, fail or unsure")
        }
        val reason = ((obj["reason"] as? String) ?: "").replace(Regex("\\s+"), " ").trim().take(JudgeVerdict.MAX_REASON)
        return JudgeVerdict(layer, outcome, reason.ifEmpty { "no reason given" }, modelId, promptVersion)
    }
}
