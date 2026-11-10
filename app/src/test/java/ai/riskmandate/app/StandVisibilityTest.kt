package ai.riskmandate.app

import ai.riskmandate.app.features.FEATURES
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stand mode keeps every feature that uses an LLM away from a visitor, even when its owner has switched it on. */
class StandVisibilityTest {
    /** A feature as its owner would ship it once demo-ready: switched on. */
    private fun on(featureId: String, cloud: Boolean = false) = object : Feature {
        override val id = featureId
        override val title = R.string.app_name
        override val enabled = true
        override val usesCloud = cloud
        @Composable override fun Content(context: FeatureContext) {}
    }

    private val llmFeatures = listOf("chat", "lab", "workbench", "models")

    @Test fun everyLlmFeatureIsHiddenInStandModeEvenWhenEnabled() {
        val all = llmFeatures.map { on(it, cloud = it == "workbench") }
        assertEquals("outside stand mode they all show", llmFeatures, visibleFeatures(all, standOn = false).map { it.id })
        assertEquals("in stand mode none shows", emptyList<String>(), visibleFeatures(all, standOn = true).map { it.id })
        for (id in llmFeatures) assertFalse("$id in stand mode", featureVisible(id, standOn = true))
    }

    @Test fun aFeatureThatUsesTheCloudIsHiddenInStandModeWhateverItsId() {
        assertFalse(featureVisible("anything-with-the-cloud", standOn = true))
        assertEquals(emptyList<String>(), visibleFeatures(listOf(on("something-new", cloud = true)), standOn = true).map { it.id })
    }

    @Test fun aNewFeatureIsHiddenInStandModeUntilItIsVettedOnPurpose() {
        assertFalse(featureVisible("brand-new-feature", standOn = true))
        assertTrue(featureVisible("brand-new-feature", standOn = false))
    }

    @Test fun theVettedVisitorFeaturesStillShowInStandMode() {
        val all = listOf("site", "abpdemo", "mandate", "releases").map { on(it) }
        assertEquals(all.map { it.id }, visibleFeatures(all, standOn = true).map { it.id })
    }

    @Test fun noLlmFeatureOfThisBranchIsOnTheVisitorList() {
        // The real list: whatever is enabled today, in stand mode only the non-LLM features remain.
        val inStand = visibleFeatures(FEATURES, standOn = true).map { it.id }.toSet()
        assertTrue("stand mode shows an LLM feature: ${inStand intersect llmFeatures.toSet()}", (inStand intersect llmFeatures.toSet()).isEmpty())
        for (f in FEATURES.filter { it.usesCloud }) assertFalse("${f.id} uses the cloud", f.id in inStand)
    }

    @Test fun aRestoredRouteToAnLlmFeatureGoesHomeInStandMode() {
        // featureVisible gates the route's feature, so resolveRoute sees no enabled feature for it.
        assertEquals(Routes.HOME, resolveRoute("chat", standOn = true, featureEnabled = featureVisible("chat", true)))
        assertEquals("chat", resolveRoute("chat", standOn = false, featureEnabled = featureVisible("chat", false)))
    }

    // One test per LLM feature, on the real feature object (so a renamed id fails here), asking the rule the host uses.
    @Test fun labIsHiddenInStandModeEvenWhenEnabled() = hiddenInStandMode(ai.riskmandate.app.features.lab.LabFeature)

    @Test fun chatIsHiddenInStandModeEvenWhenEnabled() = hiddenInStandMode(ai.riskmandate.app.features.chat.ChatFeature)

    @Test fun workbenchIsHiddenInStandModeEvenWhenEnabled() = hiddenInStandMode(ai.riskmandate.app.features.workbench.WorkbenchFeature)

    @Test fun modelsIsHiddenInStandModeEvenWhenEnabled() = hiddenInStandMode(ai.riskmandate.app.features.models.ModelsFeature)

    private fun hiddenInStandMode(real: Feature) {
        assertTrue("${real.id} is one of the LLM features this test names", real.id in llmFeatures)
        assertFalse("${real.id} must not be visible in stand mode", featureVisible(real.id, standOn = true))
        // the same id, as an owner would ship it switched on: still hidden in stand mode, shown for staff
        val switchedOn = on(real.id, cloud = real.usesCloud)
        assertEquals(emptyList<String>(), visibleFeatures(listOf(switchedOn), standOn = true).map { it.id })
        assertEquals(listOf(real.id), visibleFeatures(listOf(switchedOn), standOn = false).map { it.id })
        // and a restored route to it goes Home in stand mode
        assertEquals(Routes.HOME, resolveRoute(real.id, standOn = true, featureEnabled = featureVisible(real.id, true)))
    }
}
