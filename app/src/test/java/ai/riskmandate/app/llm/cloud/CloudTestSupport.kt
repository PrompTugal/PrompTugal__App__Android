package ai.riskmandate.app.llm.cloud

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Not a real key and not the shape of one: it must never trip a secret scanner. */
const val TEST_KEY = "TESTKEY-not-a-real-key-0000-aaaa"

/** AES-GCM in software, the same format as the Keystore one, so JVM tests exercise the real file handling. */
class SoftwareCipher(secret: ByteArray = ByteArray(32) { it.toByte() }) : KeyCipher {
    private val key = SecretKeySpec(secret, "AES")
    var destroyed = 0

    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return iv + c.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 28)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12)) }
        return c.doFinal(blob, 12, blob.size - 12)
    }

    override fun destroy() { destroyed++ }
}

/** A scripted response. [linesRead] and [closed] let tests check that error bodies are never read and that a stop closes the connection. */
class FakeResponse(override val status: Int, private val body: Sequence<String>) : CloudResponse {
    constructor(status: Int, lines: List<String>) : this(status, lines.asSequence())

    var closed = false
    var linesCalled = false

    override fun lines(): Sequence<String> { linesCalled = true; return body }
    override fun close() { closed = true }
}

class FakeTransport(private val respond: (url: String, headers: Map<String, String>, body: String) -> FakeResponse) : CloudTransport {
    data class Call(val url: String, val headers: Map<String, String>, val body: String)

    val calls = mutableListOf<Call>()
    val responses = mutableListOf<FakeResponse>()
    var failWith: java.io.IOException? = null

    override fun post(url: String, headers: Map<String, String>, body: String): CloudResponse {
        calls += Call(url, headers, body)
        failWith?.let { throw it }
        return respond(url, headers, body).also { responses += it }
    }
}

/** Server-sent events as OpenRouter sends them: a comment line first, then `data:` lines separated by blank lines. */
fun sse(vararg data: String, done: Boolean = true): List<String> =
    buildList {
        add(": OPENROUTER PROCESSING")
        add("")
        for (d in data) { add("data: $d"); add("") }
        if (done) { add("data: [DONE]"); add("") }
    }

fun content(text: String) = """{"choices":[{"delta":{"content":${org.json.JSONObject.quote(text)}}}]}"""
const val FINISH = """{"choices":[{"delta":{},"finish_reason":"stop"}]}"""
fun usage(prompt: Int, completion: Int, cost: Double?) =
    """{"choices":[],"usage":{"prompt_tokens":$prompt,"completion_tokens":$completion${if (cost != null) ""","cost":$cost""" else ""}}}"""
