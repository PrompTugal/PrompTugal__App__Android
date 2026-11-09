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

    @Test fun staffScreensAreUnreachableInStandModeButLicencesStay() {
        assertEquals(Routes.HOME, resolveRoute(Routes.DIAGNOSTICS, standOn = true, featureEnabled = false))
        assertEquals(Routes.HOME, resolveRoute(Routes.MODEL_BENCH, standOn = true, featureEnabled = false))
        assertEquals(Routes.LICENCES, resolveRoute(Routes.LICENCES, standOn = true, featureEnabled = false))
        assertEquals(Routes.HOME, resolveRoute(Routes.HOME, standOn = true, featureEnabled = false))
    }
}
