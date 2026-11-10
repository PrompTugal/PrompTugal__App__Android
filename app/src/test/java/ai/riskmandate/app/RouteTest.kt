package ai.riskmandate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    @Test fun builtInsAndEnabledFeaturesStay() {
        for (r in listOf(Routes.HOME, Routes.LICENCES, Routes.DIAGNOSTICS, Routes.MODEL_BENCH))
            assertEquals(r, resolveRoute(r, standOn = false, featureEnabled = false))
        assertEquals("site", resolveRoute("site", standOn = false, featureEnabled = true))
        assertEquals("site", resolveRoute("site", standOn = true, featureEnabled = true))
    }

    @Test fun unknownOrDisabledGoesHome() {
        assertEquals(Routes.HOME, resolveRoute("nope", standOn = false, featureEnabled = false))
        assertEquals(Routes.HOME, resolveRoute("site", standOn = false, featureEnabled = false))
    }

    @Test fun previewRoutesAreAcceptedForKnownFeaturesAndNeverInStandMode() {
        val known = setOf("site", "models", "chat", "lab")
        assertEquals("preview/models", resolveRoute(Routes.preview("models"), standOn = false, featureEnabled = false, knownFeatureIds = known))
        assertEquals("enabled or not", "preview/site", resolveRoute(Routes.preview("site"), standOn = false, featureEnabled = false, knownFeatureIds = known))
        assertEquals("unknown id", Routes.HOME, resolveRoute(Routes.preview("nope"), standOn = false, featureEnabled = false, knownFeatureIds = known))
        assertEquals("no features known", Routes.HOME, resolveRoute(Routes.preview("models"), standOn = false, featureEnabled = false))
        assertEquals("stand mode", Routes.HOME, resolveRoute(Routes.preview("models"), standOn = true, featureEnabled = false, knownFeatureIds = known))
    }

    @Test fun aPlainSwitchedOffIdStillGoesHome() {
        assertEquals(Routes.HOME, resolveRoute("models", standOn = false, featureEnabled = false, knownFeatureIds = setOf("models")))
    }

    @Test fun routeAndIdRoundTrip() {
        assertEquals("models", Routes.previewId(Routes.preview("models")))
        assertEquals(null, Routes.previewId("models"))
        assertEquals(null, Routes.previewId(Routes.DIAGNOSTICS))
    }

    @Test fun aSwitchedOffFeatureOpensFromAPreviewScreenOnly() {
        val enabled = setOf("site")
        val off = setOf("models")
        assertEquals(Routes.preview("models"), resolveOpenFeature("models", standOn = false, enabledIds = enabled, previewIds = off))
        assertEquals(null, resolveOpenFeature("models", standOn = false, enabledIds = enabled)) // not from an enabled screen
        assertEquals(null, resolveOpenFeature("models", standOn = true, enabledIds = enabled, previewIds = off))
        assertEquals("site", resolveOpenFeature("site", standOn = false, enabledIds = enabled, previewIds = off))
    }

    @Test fun staffScreensAreUnreachableInStandModeButLicencesStay() {
        assertEquals(Routes.HOME, resolveRoute(Routes.DIAGNOSTICS, standOn = true, featureEnabled = false))
        assertEquals(Routes.HOME, resolveRoute(Routes.MODEL_BENCH, standOn = true, featureEnabled = false))
        assertEquals(Routes.LICENCES, resolveRoute(Routes.LICENCES, standOn = true, featureEnabled = false))
        assertEquals(Routes.HOME, resolveRoute(Routes.HOME, standOn = true, featureEnabled = false))
    }

    @Test fun ownCustomPermissionsDoNotCount() {
        val pkg = "ai.riskmandate.app"
        assertEquals(2, countRequestedPermissions(arrayOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"), pkg))
        assertEquals(0, countRequestedPermissions(null, pkg))
    }

    @Test fun openFeatureOpensAnEnabledFeature() {
        assertEquals("models", resolveOpenFeature("models", standOn = false, enabledIds = setOf("models", "chat")))
    }

    @Test fun openFeatureIsRefusedInStandModeAndForUnknownOrDisabledIds() {
        val enabled = setOf("models", "chat")
        assertEquals(null, resolveOpenFeature("models", standOn = true, enabledIds = enabled))
        assertEquals(null, resolveOpenFeature("lab", standOn = false, enabledIds = enabled))
        assertEquals(null, resolveOpenFeature(Routes.DIAGNOSTICS, standOn = false, enabledIds = enabled))
        assertEquals(null, resolveOpenFeature("", standOn = false, enabledIds = enabled))
    }

    @Test fun theCloudKeyScreenIsStaffOnlyLikeDiagnostics() {
        assertEquals(Routes.CLOUD, resolveRoute(Routes.CLOUD, standOn = false, featureEnabled = false))
        assertEquals("stand mode or a restored route", Routes.HOME, resolveRoute(Routes.CLOUD, standOn = true, featureEnabled = false))
    }

    @Test fun noFeatureCanOpenTheCloudKeyScreenThroughOpenFeature() {
        assertEquals(null, resolveOpenFeature(Routes.CLOUD, standOn = false, enabledIds = setOf("site"), previewIds = setOf("models")))
    }

    @Test fun demosComeFirstAndEveryDemoIsInTheDemosSection() {
        val demos = listOf("abpdemo", "lab", "chat", "workbench")
        for (id in demos) assertEquals(R.string.nav_section_demos, sectionOf(id))
        val others = listOf("site", "releases", "mandate", "models")
        for (d in demos) for (o in others) assertTrue("$d before $o", navRank(d) < navRank(o))
        assertTrue("an unknown id goes last", navRank("something-new") > navRank("models"))
        assertEquals("the RiskMandate pages stay under Today", R.string.nav_section_today, sectionOf("site"))
    }

    @Test fun everyFeatureInTheListHasASectionAndARank() {
        for (f in ai.riskmandate.app.features.FEATURES) assertTrue("${f.id} is not in the nav order", navRank(f.id) < navRank("something-new"))
    }
}
