package ai.riskmandate.app.llm

import android.content.Context
import android.os.Build
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** A model file found on the device, matched by exact file name to a [ModelSpec]. */
data class FoundModel(val spec: ModelSpec, val file: File) {
    val sizeBytes: Long get() = file.length()
}

/** The outcome of checking a file against its pinned size and SHA-256. */
enum class Verdict {
    OK,
    /** Wrong size: maybe an adb push still in progress. Not loaded, not deleted. */
    WRONG_SIZE,
    /** Right size, wrong bytes. Deleted, never loaded. */
    WRONG_HASH,
}

/**
 * Where model files live, and the checks they pass before the runtime may open them.
 *
 * Two folders, both excluded from backup and device transfer:
 * - `noBackupFilesDir/models/`: internal, app-private storage, where feature G's download will put files
 *   (security G.3 and G.5: `noBackupFilesDir`, never backed up).
 * - `getExternalFilesDir("models")`: `/sdcard/Android/data/ai.riskmandate.app/files/models/`, the folder
 *   `adb push` can reach on a release build (internal storage needs a debuggable build). On Android 11 and
 *   later only this app and adb can read or write it. Step 1's bench loads a verified file from here directly;
 *   feature G (step 3) copies it into the internal folder and verifies it again before use.
 */
object ModelFiles {
    const val DIR = "models"

    fun internalDir(context: Context): File = File(context.noBackupFilesDir, DIR)

    /**
     * The adb folder, or null when shared storage is not mounted or the device runs Android 10 or earlier:
     * only scoped storage (API 30+) keeps other apps out of it (security R-llm-4). Calling it creates the
     * folder, so adb can push into it.
     */
    fun adbDir(context: Context): File? = adbDir(context, Build.VERSION.SDK_INT)

    fun adbDir(context: Context, sdk: Int): File? = if (sdk >= 30) context.getExternalFilesDir(DIR) else null

    /** Every catalogue file present in either folder, internal first. Other files are ignored. */
    fun find(context: Context): List<FoundModel> = find(listOfNotNull(internalDir(context), adbDir(context)))

    fun find(dirs: List<File>): List<FoundModel> = dirs.flatMap { dir ->
        ModelCatalog.ALL.mapNotNull { spec -> File(dir, spec.fileName).takeIf { it.isFile }?.let { FoundModel(spec, it) } }
    }

    /**
     * Whether Diagnostics shows the "Model bench" entry (architect A-llm-5): on a debuggable build, or when a
     * catalogue file is already in the adb folder, so visitors on a stand tablet do not come across it.
     * Reads one directory listing; cheap enough for the main thread when Diagnostics opens.
     */
    fun benchAvailable(context: Context, debuggable: Boolean): Boolean =
        debuggable || find(listOfNotNull(adbDir(context))).isNotEmpty()

    /** The adb command that puts [spec]'s file where [find] looks. */
    fun adbCommand(spec: ModelSpec, dir: String): String = "adb push ${spec.fileName} $dir/"

    /**
     * Checks [file] against [spec]. A wrong hash deletes the file (it is never opened); a wrong size leaves it,
     * since an adb push may still be writing. [onProgress] gets 0..100. Blocking: call off the main thread.
     */
    fun verify(file: File, spec: ModelSpec, onProgress: (Int) -> Unit = {}): Verdict {
        if (file.length() != spec.sizeBytes) return Verdict.WRONG_SIZE
        val hash = file.inputStream().use { sha256(it, spec.sizeBytes, onProgress) }
        if (hash == spec.sha256) return Verdict.OK
        file.delete()
        return Verdict.WRONG_HASH
    }

    /** Lowercase hex SHA-256 of [input]. [total] (bytes expected) is only used for progress. */
    fun sha256(input: InputStream, total: Long = 0, onProgress: (Int) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 20)
        var done = 0L
        var lastPercent = -1
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            done += n
            if (total > 0) {
                val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
