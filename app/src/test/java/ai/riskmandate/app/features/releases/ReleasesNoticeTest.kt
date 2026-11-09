package ai.riskmandate.app.features.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesNoticeTest {
    private class Mem(override var lastSeen: Int = 0) : SeenStore

    @Test fun newerVersionIsOfferedOnce() {
        val store = Mem(lastSeen = 41)
        assertTrue(ReleasesNotice.shouldOffer(42, store, hasArticles = true))
        ReleasesNotice.markSeen(42, store)
        assertFalse("not offered twice for the same version", ReleasesNotice.shouldOffer(42, store, hasArticles = true))
        assertTrue("the next update offers again", ReleasesNotice.shouldOffer(43, store, hasArticles = true))
    }

    @Test fun firstInstallOffersTheNewestArticle() = assertTrue(ReleasesNotice.shouldOffer(1, Mem(), hasArticles = true))

    @Test fun nothingToShowNothingOffered() = assertFalse(ReleasesNotice.shouldOffer(42, Mem(), hasArticles = false))

    @Test fun downgradeDoesNotResetWhatWasSeen() {
        val store = Mem(lastSeen = 50)
        assertFalse(ReleasesNotice.shouldOffer(40, store, hasArticles = true))
        ReleasesNotice.markSeen(40, store)
        assertEquals(50, store.lastSeen)
    }

    @Test fun isNewerIsStrict() {
        assertTrue(ReleasesNotice.isNewer(2, 1))
        assertFalse(ReleasesNotice.isNewer(2, 2))
        assertFalse(ReleasesNotice.isNewer(1, 2))
    }
}
