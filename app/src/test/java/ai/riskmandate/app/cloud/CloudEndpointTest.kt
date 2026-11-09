package ai.riskmandate.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudEndpointTest {
    @Test fun onlyHttpsOpenRouterUnderApiV1IsAllowed() {
        assertTrue(CloudEndpoint.isAllowed(CloudEndpoint.CHAT_URL))
        assertTrue(CloudEndpoint.isAllowed("https://openrouter.ai/api/v1/models"))
        assertTrue(CloudEndpoint.isAllowed("https://openrouter.ai:443/api/v1/chat/completions"))
    }

    @Test fun everythingElseIsRefused() {
        val bad = listOf(
            "http://openrouter.ai/api/v1/chat/completions",
            "https://openrouter.ai.evil.example/api/v1/chat/completions",
            "https://evilopenrouter.ai/api/v1/chat/completions",
            "https://openrouter.ai./api/v1/chat/completions",
            "https://sub.openrouter.ai/api/v1/chat/completions",
            "https://evil.example/api/v1/chat/completions",
            "https://evil.example/https://openrouter.ai/api/v1/x",
            "https://openrouter.ai@evil.example/api/v1/x",
            "https://user:pw@openrouter.ai/api/v1/x",
            "https://openrouter.ai:8443/api/v1/x",
            "https://openrouter.ai/other/path",
            "https://openrouter.ai",
            "https://openrouter.ai/api/v10/x",
            "https://OPENROUTER.AI/api/v1/x",
            "HTTPS://openrouter.ai/api/v1/x",
            "https://openrouter.ai/api/v1/x y",
            "https://openrouter.ai\\@evil.example/api/v1/x",
            "https://openrouter.ai/api/v1/x#frag",
            "https://openrouter.ai%2eevil.example/api/v1/x",
            "ftp://openrouter.ai/api/v1/x",
            "",
        )
        for (u in bad) assertFalse("must be refused: $u", CloudEndpoint.isAllowed(u))
    }

    @Test fun theModelListIsPinnedAndUnique() {
        val ids = CloudModels.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("[a-z0-9.-]+/[a-z0-9.:-]+")) })
        for (m in CloudModels.ALL) assertEquals(m, CloudModels.byId(m.id))
        assertEquals(null, CloudModels.byId("not/pinned"))
    }
}
