package ai.riskmandate.app.llm

import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {
    @Test fun hostsAreHuggingFaceAndItsCdnOnly() {
        listOf("huggingface.co", "hf.co", "us.aws.cdn.hf.co", "cas-bridge.xethub.hf.co", "HuggingFace.co").forEach { assertTrue(it, DownloadPolicy.hostAllowed(it)) }
        listOf("evilhf.co", "hf.co.evil.com", "huggingface.co.evil.com", "example.org", "xhuggingface.co", "-x.hf.co", ".hf.co", "a..hf.co").forEach {
            assertFalse(it, DownloadPolicy.hostAllowed(it))
        }
    }

    @Test fun everyPinnedUrlPasses() = ModelCatalog.ALL.forEach { assertNull(it.id, DownloadPolicy.refusal(URL(it.url))) }

    @Test fun refusesCleartextPortsAndUserInfo() {
        assertEquals("not https", DownloadPolicy.refusal(URL("http://huggingface.co/x")))
        assertEquals("port 8443", DownloadPolicy.refusal(URL("https://huggingface.co:8443/x")))
        assertNull(DownloadPolicy.refusal(URL("https://huggingface.co:443/x")))
        assertEquals("user info in the address", DownloadPolicy.refusal(URL("https://a:b@huggingface.co/x")))
        assertEquals("host not allowed", DownloadPolicy.refusal(URL("https://example.org/x")))
    }

    @Test fun redirectsResolveAndAreChecked() {
        val from = URL(ModelCatalog.GEMMA4_E2B.url)
        assertEquals("us.aws.cdn.hf.co", DownloadPolicy.redirect(from, "https://us.aws.cdn.hf.co/xet/abc?sig=1").getOrThrow().host)
        assertEquals("/api/resolve-cache/x", DownloadPolicy.redirect(from, "/api/resolve-cache/x").getOrThrow().path)
        assertTrue(DownloadPolicy.redirect(from, "http://us.aws.cdn.hf.co/x").isFailure)
        assertTrue(DownloadPolicy.redirect(from, "https://example.org/x").isFailure)
        assertTrue(DownloadPolicy.redirect(from, null).isFailure)
        assertTrue(DownloadPolicy.redirect(from, "javascript:alert(1)").isFailure)
    }

    @Test fun roomIsTheRestPlusAMargin() {
        val s = ModelCatalog.GEMMA4_E2B
        assertTrue(DownloadPolicy.hasRoom(s.sizeBytes + DownloadPolicy.MARGIN_BYTES, s, 0))
        assertFalse(DownloadPolicy.hasRoom(s.sizeBytes + DownloadPolicy.MARGIN_BYTES - 1, s, 0))
        assertTrue(DownloadPolicy.hasRoom(DownloadPolicy.MARGIN_BYTES + 10, s, s.sizeBytes - 10))
    }
}
