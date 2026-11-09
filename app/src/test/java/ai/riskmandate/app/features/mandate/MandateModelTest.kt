package ai.riskmandate.app.features.mandate

import ai.riskmandate.app.Links
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MandateModelTest {
    private val pkg = "ai.riskmandate.app"
    private val mandate = Mandate.current(pkg, listOf("start_url", "device_label"))

    private fun perm(name: String, granted: Boolean = true) = ReachItem(ReachKind.PERMISSION, name, granted)
    private fun activity(name: String, guard: String? = null) =
        ReachItem(ReachKind.COMPONENT, name, componentType = ComponentType.ACTIVITY, guardedBy = guard)

    /** The reach the release build is expected to have when nothing extra is exported. */
    private val cleanReach = listOf(
        perm("android.permission.INTERNET"),
        perm("android.permission.ACCESS_NETWORK_STATE"),
        perm("$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"),
        activity("$pkg.MainActivity"),
    )

    @Test fun gapIsEmptyWhenReachIsWithinMandate() {
        val report = MandateReport(cleanReach, mandate)
        assertTrue(report.gapIsEmpty)
        assertEquals(cleanReach, report.covered)
        assertEquals(emptyList<ReachItem>(), report.gap)
    }

    @Test fun anExtraPermissionIsGap() {
        val camera = perm("android.permission.CAMERA", granted = false)
        val report = MandateReport(cleanReach + camera, mandate)
        assertEquals(listOf(camera), report.gap)
        assertFalse(report.gapIsEmpty)
    }

    @Test fun anExtraExportedComponentIsGap() {
        val receiver = ReachItem(
            ReachKind.COMPONENT, "androidx.profileinstaller.ProfileInstallReceiver",
            componentType = ComponentType.RECEIVER, guardedBy = "android.permission.DUMP",
        )
        assertEquals(listOf(receiver), MandateReport(cleanReach + receiver, mandate).gap)
    }

    @Test fun cleartextAndStorageAreGap() {
        val extra = listOf(
            ReachItem(ReachKind.NETWORK, "cleartext"),
            ReachItem(ReachKind.STORAGE, "backup"),
            ReachItem(ReachKind.STORAGE, "files", count = 3),
        )
        assertEquals(extra, MandateReport(cleanReach + extra, mandate).gap)
    }

    @Test fun sameIdOfAnotherKindIsNotCovered() {
        // An exported component named like a mandated permission is not covered by it.
        val lookalike = activity("android.permission.INTERNET")
        assertEquals(listOf(lookalike), MandateReport(cleanReach + lookalike, mandate).gap)
    }

    @Test fun gapIsRecomputedWhenEitherSideMoves() {
        val camera = perm("android.permission.CAMERA")
        val reach = cleanReach + camera
        assertEquals(1, MandateReport(reach, mandate).gap.size)
        val widened = mandate.copy(entries = mandate.entries + MandateEntry(ReachKind.PERMISSION, "android.permission.CAMERA", Reason.LAUNCHER))
        assertEquals(0, MandateReport(reach, widened).gap.size)
        assertEquals(1, MandateReport(cleanReach, mandate).let { MandateReport(reach, it.mandate) }.gap.size)
    }

    @Test fun mandateEntriesNothingUsesAreReportedAsUnused() {
        val report = MandateReport(cleanReach.take(2), mandate)
        assertEquals(2, report.unused.size)
        assertTrue(report.unused.any { it.id.endsWith("MainActivity") })
    }

    @Test fun mandateSaysNoDataAndHttpsPort443OnTheTwoHosts() {
        assertEquals(emptyList<String>(), mandate.data)
        assertEquals(443, mandate.port)
        assertEquals(listOf("riskmandate.ai", "www.riskmandate.ai"), mandate.hosts)
    }

    @Test fun mandateHostsAgreeWithTheRealAllowList() {
        mandate.hosts.forEach { assertTrue(Links.isAllowed("https://$it/")) }
        assertFalse(Links.isAllowed("https://example.com/"))
    }

    @Test fun hostProbesUseTheRealCheck() {
        val probes = HostProbe.run(Links::isAllowed).associate { it.url to it.allowed }
        assertEquals(true, probes["https://riskmandate.ai/"])
        assertEquals(true, probes["https://www.riskmandate.ai/"])
        assertEquals(false, probes["http://riskmandate.ai/"])
        assertEquals(false, probes["https://riskmandate.ai:8443/"])
        assertEquals(false, probes["https://example.com/"])
        assertEquals(false, probes["https://riskmandate.ai@example.com/"])
        assertEquals(HostProbe.SAMPLES.size, probes.size)
    }

    @Test fun keysAreUniquePerKindAndId() {
        assertEquals(cleanReach.size, cleanReach.map { it.key }.toSet().size)
    }
}
