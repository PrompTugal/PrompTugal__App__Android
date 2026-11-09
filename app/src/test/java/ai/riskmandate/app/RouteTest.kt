package ai.riskmandate.app

import org.junit.Assert.assertEquals
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

    @Test fun previewRoutesOpenOnlyForSwitchedOffFeaturesAndNeverInStandMode() {
        val off = setOf("models", "chat", "lab")
        assertEquals(Routes.preview("models"), resolveRoute(Routes.preview("models"), standOn = false, featureEnabled = false, previewIds = off))
        assertEquals("a feature that is now on has no preview", Routes.HOME, resolveRoute(Routes.preview("site"), standOn = false, featureEnabled = false, previewIds = off))
        assertEquals("a restored preview route without any switched-off feature", Routes.HOME, resolveRoute(Routes.preview("models"), standOn = false, featureEnabled = false))
        assertEquals(Routes.HOME, resolveRoute(Routes.preview("models"), standOn = true, featureEnabled = false, previewIds = off))
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
}
