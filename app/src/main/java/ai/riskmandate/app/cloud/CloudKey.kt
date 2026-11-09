package ai.riskmandate.app.cloud

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts the key's bytes. The app uses [KeystoreCipher]; tests use a software one. */
interface KeyCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws if [blob] is damaged or was made with another key. */
    fun decrypt(blob: ByteArray): ByteArray

    /** Removes the encryption key itself, where there is one. */
    fun destroy() {}
}

/** AES-256-GCM with a key that lives in the Android Keystore and never leaves it. Output is a random 12-byte IV, then the ciphertext. */
class KeystoreCipher(private val alias: String = ALIAS) : KeyCipher {
    private fun store() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun key(): SecretKey {
        val ks = store()
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES + 16) { "blob too short" }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    override fun destroy() {
        try { store().deleteEntry(alias) } catch (e: Exception) { /* nothing left to delete */ }
    }

    companion object {
        const val ALIAS = "riskmandate_cloud_key_v1"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

enum class KeyCheck { OK, EMPTY, TOO_SHORT, TOO_LONG, BAD_CHARACTERS, BAD_PREFIX }

/**
 * The OpenRouter key, stored encrypted in `noBackupFilesDir/cloud/` (never in a backup). The key is:
 * never logged, never in an exception message or `toString`, never shown (the screen says only "key set" or "no key"), and only handed to the
 * code that builds the `Authorization` header of a request to openrouter.ai, through [withBearer], which does not
 * return it. Staff paste it once in Diagnostics (host UI, app owns it); [clear] removes the file and the encryption key. The folder is `noBackupFilesDir/feature_cloud/`.
 */
class CloudKey(private val dir: File, private val cipher: KeyCipher) {
    private val file get() = File(dir, FILE)

    @Synchronized
    fun isSet(): Boolean = read() != null

    /** Checks a pasted key's shape only (no network call): `sk-or-` prefix, printable ASCII, no spaces, a plausible length. */
    fun check(pasted: String): KeyCheck {
        val key = pasted.trim()
        return when {
            key.isEmpty() -> KeyCheck.EMPTY
            key.length < MIN_CHARS -> KeyCheck.TOO_SHORT
            key.length > MAX_CHARS -> KeyCheck.TOO_LONG
            key.any { it.code !in 0x21..0x7E } -> KeyCheck.BAD_CHARACTERS
            !key.startsWith(PREFIX) -> KeyCheck.BAD_PREFIX
            else -> KeyCheck.OK
        }
    }

    /** Stores [pasted] (trimmed) if it passes [check]; the result says why not. Throws [IOException] if the file cannot be written. */
    @Synchronized
    fun save(pasted: String): KeyCheck {
        val verdict = check(pasted)
        if (verdict != KeyCheck.OK) return verdict
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create the key folder")
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeBytes(cipher.encrypt(pasted.trim().toByteArray(Charsets.US_ASCII)))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot replace the stored key")
        }
        return KeyCheck.OK
    }

    @Synchronized
    fun clear() {
        File(dir, "$FILE.tmp").delete()
        file.delete()
        cipher.destroy()
    }

    /**
     * Calls [block] with the complete header value (`Bearer <key>`) and returns what [block] returns, or null when no
     * key is stored. For the request builder only: the caller of this must not return or keep the value.
     */
    internal fun <T> withBearer(block: (String) -> T): T? {
        val key = synchronized(this) { read() } ?: return null // the lock is not held while [block] does its network call
        return block("Bearer $key")
    }

    /** Removes the stored key, any `Bearer ...` text and any key-shaped string from [text]: a defence for text that came from outside. */
    internal fun scrub(text: String): String {
        var out = text
        synchronized(this) { read() }?.let { out = out.replace(it, "[key]") }
        return KEY_SHAPE.replace(BEARER.replace(out, "Bearer [key]"), "[key]")
    }

    override fun toString() = "CloudKey"

    private fun read(): String? = try {
        if (!file.isFile) null else String(cipher.decrypt(file.readBytes()), Charsets.US_ASCII).takeIf { check(it) == KeyCheck.OK }
    } catch (e: Exception) { // damaged, or made with a key that no longer exists: as if none were stored
        null
    }

    companion object {
        const val FILE = "openrouter.key"
        const val PREFIX = "sk-or-"
        const val MIN_CHARS = 16
        const val MAX_CHARS = 256
        private val BEARER = Regex("""Bearer\s+\S+""", RegexOption.IGNORE_CASE)
        private val KEY_SHAPE = Regex("""sk-or-[A-Za-z0-9_-]+""")
    }
}
