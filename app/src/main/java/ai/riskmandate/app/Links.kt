package ai.riskmandate.app

/** The RiskMandate pages the app opens. Only riskmandate.ai, over HTTPS. */
object Links {
    const val HOME = "https://riskmandate.ai/"

    private val ALLOWED_HOSTS = setOf("riskmandate.ai", "www.riskmandate.ai")
    private const val PREFIX = "https://"

    /**
     * Pure Kotlin allow-list check (no android.net.Uri), so it runs on the JVM.
     *
     * Accepted: scheme https (the scheme alone is matched case-insensitively, so `HTTPS://` is accepted;
     * the rest is compared after the authority is cut out), host exactly riskmandate.ai or
     * www.riskmandate.ai (case-insensitive, no trailing dot), optional port 443 only.
     * Refused: any '@', backslash, whitespace, control character or '%' in the authority, any
     * whitespace or control character or backslash anywhere, an empty host, any other host or port.
     */
    fun isAllowed(url: String): Boolean {
        val u = url.trim()
        if (u.any { it == '@' || it == '\\' || it.isWhitespace() || it.isISOControl() }) return false
        if (u.length < PREFIX.length || !u.regionMatches(0, PREFIX, 0, PREFIX.length, ignoreCase = true)) return false
        val rest = u.substring(PREFIX.length)
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isEmpty() || authority.contains('%')) return false
        val host: String
        val port: String?
        val colon = authority.indexOf(':')
        if (colon < 0) {
            host = authority; port = null
        } else {
            host = authority.substring(0, colon); port = authority.substring(colon + 1)
        }
        if (port != null && port != "443") return false
        return host.lowercase() in ALLOWED_HOSTS
    }

    private const val MAX_EXTERNAL_LENGTH = 2048

    /**
     * For links the user taps inside the site view that leave riskmandate.ai (Browser.openExternal):
     * https only, a host, no userinfo, no backslash, whitespace or control characters, no overlong URL.
     * Any host is fine here because the system browser (not the app) loads it. Everything else
     * (http, javascript:, intent:, file:, content:, data:, market:) is refused.
     */
    fun isExternalOk(url: String): Boolean {
        val u = url.trim()
        if (u.isEmpty() || u.length > MAX_EXTERNAL_LENGTH) return false
        if (u.any { it == '\\' || it.isWhitespace() || it.isISOControl() }) return false
        if (!u.regionMatches(0, PREFIX, 0, PREFIX.length, ignoreCase = true)) return false
        val authority = u.substring(PREFIX.length).takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.contains('@')) return false // userinfo: the real host would not be the one shown
        if (authority.isEmpty() || authority.startsWith(':') || authority.startsWith('.')) return false
        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', "")
        if (host.isEmpty() || !host.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '-' }) return false
        return port.isEmpty() || port.all { it.isDigit() } && port.length <= 5
    }
}
