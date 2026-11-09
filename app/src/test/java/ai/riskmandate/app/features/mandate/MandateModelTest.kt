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
            ReachItem(ReachKind.STORAGE, "files/notes.txt", count = 1),
        )
        assertEquals(extra, MandateReport(cleanReach + extra, mandate).gap)
    }

    @Test fun stand_and_site_storage_is_mandated_but_a_stray_file_is_gap() {
        val mandated = listOf(
            ReachItem(ReachKind.STORAGE, "shared_prefs/stand_mode.xml", count = 1),
            ReachItem(ReachKind.STORAGE, "app_webview", count = 12),
            ReachItem(ReachKind.STORAGE, "cache", count = 40),
            ReachItem(ReachKind.STORAGE, "shared_prefs/shell.xml", count = 1),
            ReachItem(ReachKind.STORAGE, "shared_prefs/feature_models.xml", count = 1),
            ReachItem(ReachKind.STORAGE, "shared_prefs/feature_releases.xml", count = 1),
            ReachItem(ReachKind.STORAGE, "no_backup/feature_chat", count = 1),
            ReachItem(ReachKind.STORAGE, "no_backup/feature_workbench", count = 1),
        )
        assertTrue(MandateReport(cleanReach + mandated, mandate).gapIsEmpty)
        val stray = ReachItem(ReachKind.STORAGE, "shared_prefs/other.xml", count = 1)
        assertEquals(listOf(stray), MandateReport(cleanReach + mandated + stray, mandate).gap)
    }

    @Test fun siteViewConnectionsAreReachOnlyWhenSiteIsOn_andMandated() {
        // Without the site view only the model download hosts are reach (the code is in the build either way).
        assertEquals(modelDownloadReach(), networkReach(setOf("abpdemo", "mandate")))
        val site = networkReach(setOf("site", "mandate"))
        assertEquals(listOf("connects:riskmandate.ai", "connects:www.riskmandate.ai"), site.map { it.id }.filter { it.contains("riskmandate") })
        assertTrue(MandateReport(cleanReach + site, mandate).gapIsEmpty)
        // A connection to any other host would be gap.
        val other = ReachItem(ReachKind.NETWORK, "connects:example.com")
        assertEquals(listOf(other), MandateReport(cleanReach + site + other, mandate).gap)
    }

    @Test fun modelDownloadHostsAreAskedOfTheRealPolicy_alwaysCounted_andMandated() {
        // Counted whenever the code is in the build: no feature switch needed.
        val models = networkReach(emptySet())
        assertEquals(listOf("connects:huggingface.co", "connects:hf.co", "connects:*.hf.co"), models.map { it.id })
        assertTrue(MandateReport(cleanReach + models, mandate).gapIsEmpty)
        // The look-alike candidates are refused by the policy itself, so they are not reach.
        assertEquals(models.map { it.id }, modelDownloadReach().map { it.id })
        val other = ReachItem(ReachKind.NETWORK, "connects:example.org")
        assertEquals(listOf(other), MandateReport(cleanReach + models + other, mandate).gap)
    }

    @Test fun modelFilesAreMandatedByExactNameFromTheCatalogue() {
        val spec = ai.riskmandate.app.llm.ModelCatalog.ALL.first()
        val ok = listOf(
            "no_backup/models/${spec.fileName}", "no_backup/models/${spec.fileName}.part",
            "no_backup/models/.${spec.fileName}.verified", "external/models/${spec.fileName}",
            "external/models/.${spec.fileName}.verified",
        ).map { ReachItem(ReachKind.STORAGE, it, count = 1) }
        assertTrue(MandateReport(cleanReach + ok, mandate).gapIsEmpty)
        val stray = ReachItem(ReachKind.STORAGE, "no_backup/models/other.bin", count = 1)
        assertEquals(listOf(stray), MandateReport(cleanReach + ok + stray, mandate).gap)
        // An adb folder never holds a ".part".
        val part = ReachItem(ReachKind.STORAGE, "external/models/${spec.fileName}.part", count = 1)
        assertEquals(listOf(part), MandateReport(cleanReach + part, mandate).gap)
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
        assertEquals(mandate.entries.size - 2, report.unused.size)
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
