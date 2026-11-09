package ai.riskmandate.app.cloud

import java.io.Closeable
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** One HTTP response whose body is read line by line (server-sent events). Close it to abort the transfer. */
interface CloudResponse : Closeable {
    val status: Int

    /** The body's lines, read lazily. Only call this for a 2xx response: error bodies are never read. */
    fun lines(): Sequence<String>
}

/** The HTTP layer, an interface so tests inject a fake and no test touches a network. Blocking: call it off the main thread. */
interface CloudTransport {
    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, body: String): CloudResponse
}

/**
 * The real transport: [HttpsURLConnection] to [CloudEndpoint] and nowhere else. Redirects are not followed (a
 * 3xx is just a status the caller treats as a failure), error bodies are not read, and the URL is checked again
 * here, so a bug elsewhere cannot send the key to another host. This is the only file in the package that touches
 * `java.net` (a test enforces that).
 */
class HttpUrlTransport(private val connectTimeoutMs: Int = 15_000, private val readTimeoutMs: Int = 90_000) : CloudTransport {
    override fun post(url: String, headers: Map<String, String>, body: String): CloudResponse {
        if (!CloudEndpoint.isAllowed(url)) throw IOException("destination not allowed")
        val connection = URL(url).openConnection() as? HttpsURLConnection ?: throw IOException("not an https connection")
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.useCaches = false
            if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) connection.setRequestProperty("User-Agent", CloudEndpoint.USER_AGENT)
            for ((k, v) in headers) connection.setRequestProperty(k, v)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (connection.url.host != CloudEndpoint.HOST) throw IOException("unexpected host")
            return object : CloudResponse {
                override val status = status
                override fun lines(): Sequence<String> {
                    val reader = connection.inputStream.bufferedReader(Charsets.UTF_8)
                    var total = 0
                    return generateSequence {
                        val line = reader.readLine()
                        if (line != null) {
                            total += line.length
                            if (line.length > CloudLimits.MAX_LINE_CHARS || total > CloudLimits.MAX_RESPONSE_CHARS) throw IOException("response too large")
                        }
                        line
                    }
                }
                override fun close() = connection.disconnect()
            }
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }
}
