package ai.riskmandate.app.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On a real or emulated device: the Android Keystore path of [CloudKey] that the JVM tests cannot reach.
 * Save, read back, the file holds no plain text, a fresh IV each write, clear removes the Keystore entry.
 * Uses its own Keystore alias and a folder in the app's cache, and a fake key that fits no secret scanner.
 * Run with `./gradlew connectedDebugAndroidTest --tests 'ai.riskmandate.app.cloud.KeystoreCipherTest'`.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreCipherTest {
    private val alias = "riskmandate_cloud_key_test_${System.nanoTime()}"
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "cloud_keystore_test_${System.nanoTime()}")
    private val fakeKey = "sk-or-FAKE-key-for-tests"

    @After fun cleanUp() {
        KeystoreCipher(alias).destroy()
        dir.deleteRecursively()
    }

    @Test fun saveReadBackAndClearThroughTheRealKeystore() {
        val cipher = KeystoreCipher(alias)
        val key = CloudKey(dir, cipher)
        assertFalse(key.isSet())
        assertEquals(KeyCheck.OK, key.save(fakeKey))
        assertTrue(key.isSet())
        assertEquals("Bearer $fakeKey", key.withBearer { it })
        val bytes = File(dir, CloudKey.FILE).readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(fakeKey))
        assertTrue(CloudKey(dir, KeystoreCipher(alias)).isSet()) // a new instance finds the same Keystore entry

        key.clear()
        assertFalse(key.isSet())
        assertFalse(File(dir, CloudKey.FILE).exists())
    }

    @Test fun aDamagedFileReadsAsNoKey() {
        val key = CloudKey(dir, KeystoreCipher(alias))
        assertEquals(KeyCheck.OK, key.save(fakeKey))
        File(dir, CloudKey.FILE).writeBytes(ByteArray(64) { 9 })
        assertFalse(key.isSet())
        assertEquals(null, key.withBearer { it })
        key.clear()
    }

    @Test fun everyWriteUsesAFreshIvAndAClearedKeyCannotReadOldCiphertext() {
        val cipher = KeystoreCipher(alias)
        val a = cipher.encrypt(fakeKey.toByteArray())
        val b = cipher.encrypt(fakeKey.toByteArray())
        assertNotEquals(a.take(KeystoreCipher.IV_BYTES), b.take(KeystoreCipher.IV_BYTES))
        assertEquals(fakeKey, String(cipher.decrypt(a)))
        cipher.destroy() // removes the Keystore entry
        assertThrows(Exception::class.java) { KeystoreCipher(alias).decrypt(a) } // a new key was generated: the old blob is unreadable
    }
}
