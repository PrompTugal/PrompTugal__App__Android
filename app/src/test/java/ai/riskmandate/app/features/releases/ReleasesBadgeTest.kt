package ai.riskmandate.app.features.releases

import ai.riskmandate.app.BuildConfig
import ai.riskmandate.app.R
import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleasesBadgeTest {
    @get:Rule val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun show() = compose.setContent {
        val badge = ReleasesFeature.homeBadge()
        Text(if (badge != null) stringResource(badge) else "none", Modifier_testTag())
    }

    @Suppress("FunctionName")
    private fun Modifier_testTag() = androidx.compose.ui.Modifier.testTag("badge")

    @Test fun badgeShowsUntilTheScreenIsOpened() {
        // A bundled article exists, nothing seen yet.
        assertEquals(true, AssetArticleSource(app.assets).dirs().isNotEmpty())
        show()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("badge").assertTextEquals("New"); true }.getOrDefault(false) }
        ReleasesNotice.markSeen(app, BuildConfig.VERSION_CODE)
        assertEquals(false, ReleasesNotice.shouldOffer(app, BuildConfig.VERSION_CODE))
    }

    @Test fun badgeStringIsTranslated() = assertEquals("New", app.getString(R.string.feature_releases_badge))
}
