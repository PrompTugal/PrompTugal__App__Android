package ai.riskmandate.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun specFor(bytes: ByteArray, hash: String = ModelFiles.sha256(bytes.inputStream())) =
        ModelCatalog.GEMMA4_E2B.copy(sizeBytes = bytes.size.toLong(), sha256 = hash)

    @Test fun sha256MatchesTheStandardVector() =
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ModelFiles.sha256("abc".byteInputStream()))

    @Test fun rightBytesPass() {
        val bytes = ByteArray(3_000_000) { (it % 251).toByte() }
        val file = tmp.newFile("m.litertlm").apply { writeBytes(bytes) }
        val progress = mutableListOf<Int>()
        assertEquals(Verdict.OK, ModelFiles.verify(file, specFor(bytes)) { progress += it })
        assertTrue(file.exists())
        assertEquals(100, progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test fun wrongHashDeletesTheFile() {
        val bytes = ByteArray(1000) { 7 }
        val file = tmp.newFile("m.litertlm").apply { writeBytes(bytes) }
        assertEquals(Verdict.WRONG_HASH, ModelFiles.verify(file, specFor(bytes, "0".repeat(64))))
        assertFalse(file.exists())
    }

    @Test fun wrongSizeKeepsTheFileAndDoesNotHash() {
        val file = tmp.newFile("m.litertlm").apply { writeBytes(ByteArray(10)) }
        var hashed = false
        assertEquals(Verdict.WRONG_SIZE, ModelFiles.verify(file, specFor(ByteArray(11))) { hashed = true })
        assertTrue(file.exists())
        assertFalse(hashed)
    }

    @Test fun findMatchesOnlyCatalogueNames() {
        val a = tmp.newFolder("internal")
        val b = tmp.newFolder("adb")
        File(a, ModelCatalog.GEMMA4_E2B.fileName).writeBytes(ByteArray(1))
        File(b, ModelCatalog.GEMMA4_E4B.fileName).writeBytes(ByteArray(1))
        File(b, "other.litertlm").writeBytes(ByteArray(1))
        File(b, ModelCatalog.GEMMA3_1B.fileName).mkdirs() // a folder with the right name is not a model
        val found = ModelFiles.find(listOf(a, b, File(tmp.root, "missing")))
        assertEquals(listOf(ModelCatalog.GEMMA4_E2B, ModelCatalog.GEMMA4_E4B), found.map { it.spec })
        assertEquals(a, found[0].file.parentFile)
    }

    @Test fun adbCommandNamesTheExactFileAndFolder() = assertEquals(
        "adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/ai.riskmandate.app/files/feature_models/",
        ModelFiles.adbCommand(ModelCatalog.GEMMA4_E2B, "/sdcard/Android/data/ai.riskmandate.app/files/feature_models"),
    )
}
