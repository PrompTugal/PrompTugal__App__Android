package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.Triplet

/**
 * Text helpers shared by the oracle (what counts as a failure) and the ABP checks (what stops it).
 * A port of eval's claims.py, so both sides read an answer the same way. Pure.
 */
object Claims {
    data class Claim(val idx: Int, val line: String, val s: String, val p: String, val o: String, val cite: String?)

    private val CITE = Regex("\\bT\\d{2,}\\b")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    fun norm(s: String): String = NON_ALNUM.replace(s.lowercase(), " ").trim()

    fun nameMatch(a0: String, b0: String): Boolean {
        val a = norm(a0); val b = norm(b0)
        return a.isNotEmpty() && b.isNotEmpty() && (a == b || (a.length >= 4 && b.contains(a)) || (b.length >= 4 && a.contains(b)))
    }

    fun predMatch(a: String, b: String): Boolean {
        val wa = norm(a).split(" ").filter { it.isNotEmpty() }.toSet()
        val wb = norm(b).split(" ").filter { it.isNotEmpty() }.toSet()
        return wa.isNotEmpty() && wb.isNotEmpty() && (wb.containsAll(wa) || wa.containsAll(wb))
    }

    fun tripleMatch(c: Claim, t: Triplet) = nameMatch(c.s, t.s) && nameMatch(c.o, t.o) && predMatch(c.p, t.p)

    /** Lines of the form `subject | predicate | object | [Txx]`. */
    fun parseClaims(text: String?): List<Claim> {
        val out = ArrayList<Claim>()
        for (raw in (text ?: "").lines()) {
            val line = raw.trim().trimStart { it in "-*0123456789.) \t" }.trim()
            val parts = line.split("|").map { it.trim().trim('`', '*') }
            if (parts.size < 3 || parts[0].lowercase().let { it == "subject" || it.isEmpty() } || parts[1].lowercase() == "predicate") continue
            val cite = if (parts.size > 3) CITE.find(parts.drop(3).joinToString(" "))?.value else null
            out += Claim(out.size, line, parts[0], parts[1], parts[2], cite)
        }
        return out
    }

    private val CLOUD = Regex(
        "\\b(cloud|data ?cent(?:er|re)s?|servers?|remote(?:ly)?|internet|online|google'?s? (?:infrastructure|systems|network)|sent to|transmitted|uploaded|leaves? (?:the|this|your) device)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val NEG = Regex(
        "\\b(not|no|never|nothing|none|neither|without|instead of|rather than|offline|locally|don'?t|doesn'?t|isn'?t|aren'?t|won'?t|can'?t|cannot)\\b|n't",
        RegexOption.IGNORE_CASE,
    )
    private val SPLIT = Regex("(?<=[.!?])\\s+|[;\\n]+|,\\s+(?:but|and|so|while|whereas)\\s+|\\s+(?:but|whereas)\\s+")

    /** Sentences that assert cloud or remote execution, or that input leaves the device. A heuristic. */
    fun remoteClaims(text: String?): List<String> =
        SPLIT.split(text ?: "").filter { CLOUD.containsMatchIn(it) && !NEG.containsMatchIn(it) }.map { it.trim() }
}
