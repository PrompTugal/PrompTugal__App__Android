package ai.riskmandate.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CloudKeyTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir get() = File(tmp.root, "cloud")
    private val cipher = SoftwareCipher()
    private fun key() = CloudKey(dir, cipher)

    @Test fun savesEncryptedAndReadsBack() {
        val k = key()
        assertFalse(k.isSet())
        assertEquals(KeyCheck.OK, k.save("  $TEST_KEY \n"))
        assertTrue(k.isSet())
        assertEquals("Bearer $TEST_KEY", k.withBearer { it })
        assertTrue(CloudKey(dir, cipher).isSet()) // a new instance, same tablet
    }

    @Test fun theFileNeverHoldsThePlainKey() {
        key().save(TEST_KEY)
        val bytes = File(dir, CloudKey.FILE).readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(TEST_KEY))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(TEST_KEY.takeLast(8)))
        assertEquals(listOf(CloudKey.FILE), dir.list()!!.toList()) // no temp file left
    }

    @Test fun clearRemovesTheFileAndTheEncryptionKey() {
        val k = key()
        k.save(TEST_KEY); k.clear()
        assertFalse(k.isSet())
        assertFalse(File(dir, CloudKey.FILE).exists())
        assertEquals(1, cipher.destroyed)
        assertNull(k.withBearer { it })
    }

    @Test fun aKeyMadeWithAnotherEncryptionKeyOrDamagedReadsAsNotSet() {
        key().save(TEST_KEY)
        assertFalse(CloudKey(dir, SoftwareCipher(ByteArray(32) { (it + 1).toByte() })).isSet())
        File(dir, CloudKey.FILE).writeBytes(ByteArray(40) { 7 })
        assertFalse(key().isSet())
    }

    @Test fun shapeCheckRejectsEmptyShortLongAndNonAscii() {
        val k = key()
        assertEquals(KeyCheck.EMPTY, k.check("   "))
        assertEquals(KeyCheck.TOO_SHORT, k.check("short"))
        assertEquals(KeyCheck.TOO_LONG, k.check("a".repeat(300)))
        assertEquals(KeyCheck.BAD_CHARACTERS, k.check("TESTKEY with a space inside it 123"))
        assertEquals(KeyCheck.BAD_CHARACTERS, k.check("TESTKEY-not-a-real-key-éééé"))
        assertEquals(KeyCheck.OK, k.check(TEST_KEY))
        assertFalse(k.save("short") == KeyCheck.OK)
        assertFalse(k.isSet())
    }

    @Test fun theKeyIsNeverInToStringOrScrubbedText() {
        val k = key()
        k.save(TEST_KEY)
        assertFalse(k.toString().contains(TEST_KEY))
        assertEquals("he said [key] and Bearer [key]", k.scrub("he said $TEST_KEY and Bearer abcdef"))
        assertEquals("other [key] here", k.scrub("other sk-or-someone-elses_KEY-99 here")) // any key-shaped text, not only ours
    }
}
