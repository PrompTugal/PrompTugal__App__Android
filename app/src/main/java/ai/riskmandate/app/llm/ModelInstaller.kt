package ai.riskmandate.app.llm

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** What an install attempt ended with. */
sealed interface InstallResult {
    data class Installed(val file: File) : InstallResult
    data class Failed(val reason: InstallFailure, val detail: String) : InstallResult
}

enum class InstallFailure {
    /** Not enough free space for the rest of the file plus a margin. */
    STORAGE,
    /** Connection lost or timed out; the partial file is kept, so the next try resumes. */
    NETWORK,
    /** The server answered with an error status. */
    HTTP,
    /** A redirect went somewhere the policy does not allow, or too many redirects. */
    REFUSED,
    /** The server sent more or other bytes than the pinned size. The partial file is deleted. */
    SIZE,
    /** The finished file's SHA-256 does not match the pin. The file is deleted, never loaded. */
    HASH,
}

/**
 * Downloads, imports, checks and deletes model files in the internal folder ([ModelFiles.internalDir],
 * `noBackupFilesDir/models/`), following security checklist G and architect's rules:
 * - HttpURLConnection only, redirects followed by hand ([DownloadPolicy]), no Authorization header, no cookies,
 *   no caches, timeouts on connect and read;
 * - resumable with HTTP Range into `<file>.part`; the pinned size is enforced while streaming;
 * - SHA-256 over the finished file, then an atomic rename to the final name and a verified marker; a mismatch
 *   deletes the file;
 * - an adb-pushed file is copied into the internal folder and verified again after the copy (A-llm-9, R-llm-4).
 * Runs in the caller's coroutine (feature G's screen); leaving the screen cancels and keeps the partial file.
 */
class ModelInstaller(
    private val dir: File,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val availableBytes: (File) -> Long = { it.usableSpace },
) {
    fun finalFile(spec: ModelSpec) = File(dir, spec.fileName)
    fun partFile(spec: ModelSpec) = File(dir, spec.fileName + ".part")

    /** Bytes already downloaded for [spec] (a partial file), for "resume" and progress. */
    fun partialBytes(spec: ModelSpec): Long = partFile(spec).takeIf { it.isFile }?.length() ?: 0

    fun isInstalled(spec: ModelSpec): Boolean = finalFile(spec).isFile

    /** Removes the model, its marker and any partial file. */
    fun delete(spec: ModelSpec) {
        finalFile(spec).delete()
        ModelFiles.markerFile(finalFile(spec)).delete()
        partFile(spec).delete()
    }

    /** [onProgress] gets (bytes done, total) and, during the final check, ([total], [total]) with [checking] true. */
    suspend fun download(spec: ModelSpec, onProgress: (done: Long, total: Long, checking: Boolean) -> Unit = { _, _, _ -> }): InstallResult =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            if (isInstalled(spec) && ModelFiles.isMarkedVerified(finalFile(spec), spec)) return@withContext InstallResult.Installed(finalFile(spec))
            val part = partFile(spec)
            var have = partialBytes(spec)
            if (have > spec.sizeBytes) {
                part.delete()
                have = 0
            }
            if (!DownloadPolicy.hasRoom(availableBytes(dir), spec, have)) {
                return@withContext InstallResult.Failed(InstallFailure.STORAGE, "needs ${spec.sizeBytes - have + DownloadPolicy.MARGIN_BYTES} bytes free")
            }
            if (have < spec.sizeBytes) {
                fetch(spec, part, have, onProgress)?.let { return@withContext it }
            }
            onProgress(spec.sizeBytes, spec.sizeBytes, true)
            finish(spec, part)
        }

    /** Copies an adb-pushed [source] in, checks it after the copy, and on success deletes [source] to free space. */
    suspend fun importFrom(source: File, spec: ModelSpec, onProgress: (done: Long, total: Long, checking: Boolean) -> Unit = { _, _, _ -> }): InstallResult =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            if (source.length() != spec.sizeBytes) return@withContext InstallResult.Failed(InstallFailure.SIZE, "the adb file is not the expected size")
            if (!DownloadPolicy.hasRoom(availableBytes(dir), spec, 0)) {
                return@withContext InstallResult.Failed(InstallFailure.STORAGE, "needs ${spec.sizeBytes + DownloadPolicy.MARGIN_BYTES} bytes free")
            }
            val part = partFile(spec)
            part.delete()
            try {
                source.inputStream().use { input -> FileOutputStream(part).use { out -> copy(input, out, 0, spec, onProgress) } }
            } catch (e: IOException) {
                part.delete()
                return@withContext InstallResult.Failed(InstallFailure.NETWORK, "copy failed: ${e.message}".take(200))
            }
            onProgress(spec.sizeBytes, spec.sizeBytes, true)
            val result = finish(spec, part)
            if (result is InstallResult.Installed) source.delete()
            result
        }

    /** Verifies the complete [part] and renames it atomically, or deletes it. */
    private fun finish(spec: ModelSpec, part: File): InstallResult {
        if (part.length() != spec.sizeBytes) {
            part.delete()
            return InstallResult.Failed(InstallFailure.SIZE, "the file is not the expected size")
        }
        if (ModelFiles.verify(part, spec) != Verdict.OK) {
            part.delete()
            return InstallResult.Failed(InstallFailure.HASH, "the file's SHA-256 does not match the pinned value; it was deleted")
        }
        val target = finalFile(spec)
        ModelFiles.markerFile(target).delete()
        if (!part.renameTo(target)) {
            part.delete()
            return InstallResult.Failed(InstallFailure.STORAGE, "could not move the checked file into place")
        }
        ModelFiles.markVerified(target, spec)
        return InstallResult.Installed(target)
    }

    /** Streams the rest of the file into [part]. Null when the part is complete; a failure otherwise. */
    private suspend fun fetch(spec: ModelSpec, part: File, start: Long, onProgress: (Long, Long, Boolean) -> Unit): InstallResult? {
        var have = start
        var url = URL(spec.url)
        DownloadPolicy.refusal(url)?.let { return InstallResult.Failed(InstallFailure.REFUSED, it) }
        var hops = 0
        while (true) {
            coroutineContext.ensureActive()
            val conn = try {
                open(url)
            } catch (e: IOException) {
                return InstallResult.Failed(InstallFailure.NETWORK, e.javaClass.simpleName)
            }
            try {
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.setRequestProperty("Accept-Encoding", "identity")
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                val code = conn.responseCode
                when {
                    code in REDIRECTS -> {
                        if (++hops > DownloadPolicy.MAX_REDIRECTS) return InstallResult.Failed(InstallFailure.REFUSED, "too many redirects")
                        url = DownloadPolicy.redirect(url, conn.getHeaderField("Location")).getOrElse {
                            return InstallResult.Failed(InstallFailure.REFUSED, it.message ?: "redirect refused")
                        }
                        continue
                    }
                    code == 416 && have == spec.sizeBytes -> return null
                    code == 206 -> {
                        val range = conn.getHeaderField("Content-Range") ?: ""
                        if (!range.startsWith("bytes $have-") || !range.endsWith("/${spec.sizeBytes}")) {
                            // The server did not resume where asked: start again from zero.
                            part.delete()
                            have = 0
                            continue
                        }
                    }
                    code == 200 -> {
                        if (have > 0) part.delete()
                        have = 0
                    }
                    else -> return InstallResult.Failed(InstallFailure.HTTP, "HTTP $code")
                }
                val length = conn.getHeaderField("Content-Length")?.toLongOrNull()
                if (length != null && length != spec.sizeBytes - have) {
                    part.delete()
                    return InstallResult.Failed(InstallFailure.SIZE, "the server offers a different size")
                }
                return try {
                    conn.inputStream.use { input -> FileOutputStream(part, have > 0).use { out -> copy(input, out, have, spec, onProgress) } }
                    if (part.length() == spec.sizeBytes) null
                    else InstallResult.Failed(InstallFailure.NETWORK, "the download stopped early; try again to resume")
                } catch (e: SizeExceeded) {
                    part.delete()
                    InstallResult.Failed(InstallFailure.SIZE, "the server sent more than the pinned size")
                } catch (e: IOException) {
                    InstallResult.Failed(InstallFailure.NETWORK, "${e.javaClass.simpleName}; try again to resume")
                }
            } catch (e: IOException) {
                return InstallResult.Failed(InstallFailure.NETWORK, e.javaClass.simpleName)
            } finally {
                conn.disconnect()
            }
        }
    }

    private class SizeExceeded : IOException()

    /** Copies until EOF, never past the pinned size; cancellable between buffers. */
    private suspend fun copy(input: InputStream, out: FileOutputStream, start: Long, spec: ModelSpec, onProgress: (Long, Long, Boolean) -> Unit) {
        val buffer = ByteArray(BUFFER)
        var done = start
        var lastReport = -1L
        while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            if (done + n > spec.sizeBytes) throw SizeExceeded()
            out.write(buffer, 0, n)
            done += n
            if (done - lastReport >= REPORT_EVERY || done == spec.sizeBytes) {
                lastReport = done
                onProgress(done, spec.sizeBytes, false)
            }
        }
        out.fd.sync()
    }

    companion object {
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER = 256 * 1024
        private const val REPORT_EVERY = 4L * 1024 * 1024
    }
}
