package ai.riskmandate.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ModelCatalogTest {
    private val hex64 = Regex("^[0-9a-f]{64}$")
    private val commit = Regex("^[0-9a-f]{40}$")

    @Test fun everyFileIsPinnedByCommitSizeAndHash() {
        ModelCatalog.ALL.forEach { m ->
            assertTrue("${m.id} sha256", hex64.matches(m.sha256))
            assertTrue("${m.id} revision must be a full commit, not a branch", commit.matches(m.revision))
            assertTrue("${m.id} size", m.sizeBytes > 100_000_000)
            assertTrue("${m.id} file", m.fileName.endsWith(".litertlm") && '/' !in m.fileName)
        }
    }

    @Test fun urlsAreHttpsOnHuggingFaceOnly() {
        ModelCatalog.ALL.forEach { m ->
            val uri = URI(m.url)
            assertEquals("https", uri.scheme)
            assertEquals("huggingface.co", uri.host)
            assertEquals(-1, uri.port)
            assertEquals(null, uri.userInfo)
            assertEquals("/${m.repo}/resolve/${m.revision}/${m.fileName}", uri.path)
            assertTrue(m.repo.startsWith("litert-community/"))
        }
    }

    @Test fun idsAndFilesAreUnique() {
        assertEquals(ModelCatalog.ALL.size, ModelCatalog.ALL.map { it.id }.toSet().size)
        assertEquals(ModelCatalog.ALL.size, ModelCatalog.ALL.map { it.fileName }.toSet().size)
        assertTrue(ModelCatalog.ALL.all { Regex("^[a-z0-9]+$").matches(it.id) })
    }

    @Test fun licencesAreRecordedAndOnlyUngatedFilesAreDownloadable() {
        assertEquals("apache-2.0", ModelCatalog.GEMMA4_E2B.licence)
        assertEquals("apache-2.0", ModelCatalog.GEMMA4_E4B.licence)
        assertTrue(ModelCatalog.GEMMA4_E2B.downloadable && ModelCatalog.GEMMA4_E4B.downloadable)
        assertFalse(ModelCatalog.GEMMA3_1B.downloadable)
        assertFalse(ModelCatalog.GEMMA3_270M.downloadable)
        assertTrue(ModelCatalog.ALL.all { it.licence.isNotBlank() })
    }

    @Test fun lookupByFileName() {
        assertEquals(ModelCatalog.GEMMA4_E2B, ModelCatalog.byFileName("gemma-4-E2B-it.litertlm"))
        assertEquals(null, ModelCatalog.byFileName("gemma-4-E2B-it-gpu.litertlm"))
    }
}
