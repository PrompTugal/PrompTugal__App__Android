package ai.riskmandate.app

/** The RiskMandate pages the app opens. Only riskmandate.ai, over HTTPS. */
object Links {
    const val HOME = "https://riskmandate.ai/"

    fun isAllowed(url: String): Boolean {
        val u = url.trim().lowercase()
        if (!u.startsWith("https://")) return false
        val host = u.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
        val bare = host.substringBefore(':').substringAfter('@')
        return bare == "riskmandate.ai" || bare.endsWith(".riskmandate.ai")
    }
}
