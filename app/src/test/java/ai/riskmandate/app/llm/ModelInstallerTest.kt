package ai.riskmandate.app.llm

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bytes = ByteArray(1_000_000) { (it * 31 % 251).toByte() }
    private val spec = ModelCatalog.GEMMA4_E2B.copy(sizeBytes = bytes.size.toLong(), sha256 = ModelFiles.sha256(bytes.inputStream()))
    private val cdn = "https://us.aws.cdn.hf.co/xet/blob?sig=abc"

    /** A scripted connection: what one URL answers. Records the request headers it was given. */
    private open class Fake(u: URL, val code: Int, val headers: Map<String, String>, val body: () -> InputStream) : HttpURLConnection(u) {
        val sent = mutableMapOf<String, String>()
        override fun setRequestProperty(key: String, value: String) { sent[key] = value }
        override fun getResponseCode() = code
        override fun getHeaderField(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        override fun getInputStream(): InputStream = body()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
    }

    private val opened = mutableListOf<Fake>()

    /** Hugging Face behaviour: the pinned URL redirects to the CDN, which serves the bytes and honours Range. */
    private fun hf(serve: (start: Long) -> Pair<Int, ByteArray> = { s -> (if (s > 0) 206 else 200) to bytes.copyOfRange(s.toInt(), bytes.size) },
                   location: String = cdn, extra: Map<String, String> = emptyMap()): (URL) -> HttpURLConnection = { u ->
        val f = if (u.host == "huggingface.co") {
            Fake(u, 302, mapOf("Location" to location)) { ByteArrayInputStream(ByteArray(0)) }
        } else {
            lateinit var me: Fake
            me = object : Fake(u, 0, emptyMap(), { ByteArrayInputStream(ByteArray(0)) }) {
                val start get() = sent["Range"]?.removePrefix("bytes=")?.removeSuffix("-")?.toLong() ?: 0L
                val answer by lazy { serve(start) }
                override fun getResponseCode() = answer.first
                override fun getHeaderField(name: String): String? = when {
                    name.equals("Content-Length", true) -> answer.second.size.toString()
                    name.equals("Content-Range", true) && answer.first == 206 -> "bytes $start-${bytes.size - 1}/${bytes.size}"
                    else -> extra.entries.firstOrNull { it.key.equals(name, true) }?.value
                }
                override fun getInputStream(): InputStream = ByteArrayInputStream(answer.second)
            }
            me
        }
        opened += f
        f
    }

    private fun installer(open: (URL) -> HttpURLConnection, free: Long = Long.MAX_VALUE) = ModelInstaller(tmp.root, open) { free }

    @Test fun downloadsThroughTheCdnChecksAndInstalls() = runBlocking {
        val i = installer(hf())
        val r = i.download(spec)
        assertTrue(r is InstallResult.Installed)
        assertTrue(i.isInstalled(spec))
        assertFalse(i.partFile(spec).exists())
        assertTrue(ModelFiles.isMarkedVerified(i.finalFile(spec), spec))
        assertEquals(listOf("huggingface.co", "us.aws.cdn.hf.co"), opened.map { it.url.host })
        opened.forEach {
            assertFalse("no Authorization header", it.sent.keys.any { k -> k.equals("Authorization", true) })
            assertFalse("no cookies", it.sent.keys.any { k -> k.equals("Cookie", true) })
            assertFalse(it.instanceFollowRedirects)
            assertFalse(it.useCaches)
        }
    }

    @Test fun resumesFromThePartialFileWithRange() = runBlocking {
        val i = installer(hf())
        i.partFile(spec).writeBytes(bytes.copyOfRange(0, 400_000))
        assertTrue(i.download(spec) is InstallResult.Installed)
        assertEquals("bytes=400000-", opened.last().sent["Range"])
        assertEquals(bytes.toList(), i.finalFile(spec).readBytes().toList())
    }

    @Test fun aServerThatIgnoresRangeStartsAgainCleanly() = runBlocking {
        val i = installer(hf(serve = { 200 to bytes }))
        i.partFile(spec).writeBytes(ByteArray(300_000) { 9 })
        assertTrue(i.download(spec) is InstallResult.Installed)
        assertEquals(bytes.toList(), i.finalFile(spec).readBytes().toList())
    }

    @Test fun aServerThatKeepsSendingTheWrongRangeIsGivenUpOn() = runBlocking {
        // Always answers 206 for bytes 0- whatever was asked: one restart from zero, then a clear failure.
        val wrong: (URL) -> HttpURLConnection = { u ->
            object : Fake(u, 206, mapOf("Content-Range" to "bytes 0-${bytes.size - 1}/${bytes.size}", "Content-Length" to "${bytes.size}"), { ByteArrayInputStream(bytes) }) {
                override fun getHeaderField(name: String): String? = if (sent["Range"] == null && name.equals("Content-Range", true)) "bytes 5-9/10" else super.getHeaderField(name)
            }.also { opened += it }
        }
        val i = installer(wrong)
        i.partFile(spec).writeBytes(bytes.copyOfRange(0, 1000))
        val r = i.download(spec) as InstallResult.Failed
        assertEquals(InstallFailure.HTTP, r.reason)
        assertTrue("bounded: ${opened.size} requests", opened.size <= 3)
        assertFalse(i.partFile(spec).exists())
    }

    @Test fun wrongBytesAreDeletedAndNeverInstalled() = runBlocking {
        val bad = bytes.copyOf().also { it[10] = (it[10] + 1).toByte() }
        val i = installer(hf(serve = { s -> 200 to bad.copyOfRange(s.toInt(), bad.size) }))
        val r = i.download(spec) as InstallResult.Failed
        assertEquals(InstallFailure.HASH, r.reason)
        assertFalse(i.isInstalled(spec))
        assertFalse(i.partFile(spec).exists())
    }

    @Test fun moreBytesThanPinnedAreRefused() = runBlocking {
        val i = installer(hf(serve = { 200 to bytes + ByteArray(10) }))
        assertEquals(InstallFailure.SIZE, (i.download(spec) as InstallResult.Failed).reason)
        assertFalse(i.partFile(spec).exists())
    }

    @Test fun aRedirectOffTheAllowListIsRefused() = runBlocking {
        val i = installer(hf(location = "https://evil.example.org/model"))
        assertEquals(InstallFailure.REFUSED, (i.download(spec) as InstallResult.Failed).reason)
        assertEquals(1, opened.size)
        assertFalse(i.partFile(spec).exists())
    }

    @Test fun redirectLoopsStop() = runBlocking {
        val loop: (URL) -> HttpURLConnection = { u -> Fake(u, 302, mapOf("Location" to "https://huggingface.co/again")) { ByteArrayInputStream(ByteArray(0)) }.also { opened += it } }
        assertEquals(InstallFailure.REFUSED, (installer(loop).download(spec) as InstallResult.Failed).reason)
        assertEquals(DownloadPolicy.MAX_REDIRECTS + 1, opened.size)
    }

    @Test fun aDroppedConnectionKeepsThePartForResume() = runBlocking {
        val dropping = hf(serve = { 200 to bytes })
        val i = installer(open = { u: URL ->
            val c = dropping(u)
            if (u.host == "huggingface.co") c else object : HttpURLConnection(u) {
                override fun setRequestProperty(key: String, value: String) = c.setRequestProperty(key, value)
                override fun getResponseCode() = c.responseCode
                override fun getHeaderField(name: String): String? = c.getHeaderField(name)
                override fun getInputStream(): InputStream = object : InputStream() {
                    val inner = c.inputStream
                    var n = 0
                    override fun read(): Int = if (++n > 500_000) throw IOException("reset") else inner.read()
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (n >= 500_000) throw IOException("reset")
                        val r = inner.read(b, off, minOf(len, 500_000 - n))
                        if (r > 0) n += r
                        return r
                    }
                }
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
            }
        })
        assertEquals(InstallFailure.NETWORK, (i.download(spec) as InstallResult.Failed).reason)
        assertEquals(500_000L, i.partialBytes(spec))
    }

    @Test fun httpErrorsAndStorageAreReported() = runBlocking {
        val forbidden: (URL) -> HttpURLConnection = { u -> Fake(u, 403, emptyMap()) { ByteArrayInputStream(ByteArray(0)) } }
        assertEquals(InstallFailure.HTTP, (installer(forbidden).download(spec) as InstallResult.Failed).reason)
        assertEquals(InstallFailure.STORAGE, (installer(hf(), free = 1000).download(spec) as InstallResult.Failed).reason)
    }

    @Test fun adbFileIsCopiedCheckedAndTheSourceRemoved() = runBlocking {
        val src = File(tmp.newFolder("adb"), spec.fileName).apply { writeBytes(bytes) }
        val i = installer(hf())
        assertTrue(i.importFrom(src, spec) is InstallResult.Installed)
        assertTrue(i.isInstalled(spec))
        assertFalse(src.exists())
        assertTrue(opened.isEmpty())
    }

    @Test fun aBadAdbFileIsNotInstalledAndIsKeptForInspection() = runBlocking {
        val src = File(tmp.newFolder("adb"), spec.fileName).apply { writeBytes(ByteArray(bytes.size) { 1 }) }
        val i = installer(hf())
        assertEquals(InstallFailure.HASH, (i.importFrom(src, spec) as InstallResult.Failed).reason)
        assertFalse(i.isInstalled(spec))
        assertFalse(i.partFile(spec).exists())
    }

    @Test fun deleteRemovesFileMarkerAndPart() = runBlocking {
        val i = installer(hf())
        i.download(spec)
        i.partFile(spec).writeBytes(ByteArray(3))
        i.delete(spec)
        assertFalse(i.isInstalled(spec))
        assertFalse(ModelFiles.markerFile(i.finalFile(spec)).exists())
        assertEquals(0L, i.partialBytes(spec))
        assertNull(tmp.root.listFiles()?.firstOrNull { it.name.contains(spec.fileName) })
    }
}
