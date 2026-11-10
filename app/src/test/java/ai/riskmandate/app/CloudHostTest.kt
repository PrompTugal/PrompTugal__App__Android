package ai.riskmandate.app

import ai.riskmandate.app.cloud.CloudLimits
import ai.riskmandate.app.cloud.SoftwareCipher
import ai.riskmandate.app.cloud.TEST_KEY
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.view.WindowManager

/** The cloud key screen and the host wiring, against security's section M (summit-checklist.md). */
@RunWith(AndroidJUnit4::class)
class CloudHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var dir: File
    private lateinit var cipher: SoftwareCipher
    private lateinit var host: CloudHost
    private var stand = false

    @Before fun setUp() {
        dir = java.nio.file.Files.createTempDirectory("cloud").toFile()
        cipher = SoftwareCipher()
        host = CloudHost(dir, cipher) { stand }
    }

    @After fun tearDown() { dir.deleteRecursively() }

    private fun show(standOn: Boolean = false, onChanged: () -> Unit = {}) =
        compose.setContent { RiskMandateTheme { CloudKeyScreen(host, standOn, onChanged) } }

    /** Every piece of text the screen exposes, including the password field's, for "the key is nowhere". */
    private fun allText(): String {
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { it.text } +
                listOfNotNull(n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text) +
                n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty() +
                n.children.flatMap { walk(it) }
        // every window: the confirmation dialog is a window of its own
        return compose.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { walk(it) }.joinToString("\n")
    }

    @Test fun savingAValidKeyStoresItClearsTheFieldAndShowsOnlyKeySet() {
        show()
        compose.onNodeWithTag(CloudKeyTags.STATUS).assertTextContains("No key")
        compose.onNodeWithTag(CloudKeyTags.FIELD).performTextInput(TEST_KEY)
        compose.onNodeWithTag(CloudKeyTags.SAVE).performClick()
        compose.waitForIdle()
        assertTrue(host.key.isSet())
        compose.onNodeWithTag(CloudKeyTags.STATUS).assertTextContains("Key set")
        assertFalse("the key must be nowhere on the screen", allText().contains(TEST_KEY))
        assertFalse(allText().contains(TEST_KEY.takeLast(6)))
        // the ledger is created when the key is saved
        assertTrue(host.spend.snapshot().ledgerOk)
    }

    @Test fun aBadKeyIsRefusedWithAReasonAndTheFieldIsClearedAnyway() {
        show()
        for ((text, wanted) in listOf(
            "" to R.string.cloud_check_empty,
            "sk-or-short" to R.string.cloud_check_short,
            "sk-or-" + "a".repeat(300) to R.string.cloud_check_long,
            "sk-or-has space in it here" to R.string.cloud_check_chars,
            "not-an-openrouter-key-at-all" to R.string.cloud_check_prefix,
        )) {
            if (text.isNotEmpty()) compose.onNodeWithTag(CloudKeyTags.FIELD).performTextInput(text)
            compose.onNodeWithTag(CloudKeyTags.SAVE).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(CloudKeyTags.MESSAGE).assertTextContains(ApplicationProvider.getApplicationContext<android.content.Context>().getString(wanted))
            assertFalse("nothing was stored", host.key.isSet())
            assertFalse(allText().contains(text.ifEmpty { "\u0000" }))
        }
    }

    private fun savingFailsWith(failure: Throwable) {
        val broken = object : ai.riskmandate.app.cloud.KeyCipher {
            override fun encrypt(plain: ByteArray): ByteArray = throw failure
            override fun decrypt(blob: ByteArray): ByteArray = throw failure
        }
        host = CloudHost(dir, broken) { false }
        show()
        compose.onNodeWithTag(CloudKeyTags.FIELD).performTextInput(TEST_KEY)
        compose.onNodeWithTag(CloudKeyTags.SAVE).performClick() // must not throw
        compose.waitForIdle()
        compose.onNodeWithTag(CloudKeyTags.MESSAGE).assertTextContains("Could not store the key on this tablet.")
        assertFalse(host.key.isSet())
        assertFalse("nothing from the exception is shown", allText().contains("secret detail"))
        assertFalse(allText().contains(TEST_KEY))
    }

    // A Keystore or file failure must never crash the tap: a fixed message that carries nothing from the exception.
    @Test fun aGeneralSecurityFailureOnSaveIsAFixedMessage() = savingFailsWith(java.security.GeneralSecurityException("secret detail"))

    @Test fun aRuntimeFailureOnSaveIsAFixedMessage() = savingFailsWith(IllegalStateException("secret detail"))

    @Test fun anIoFailureOnSaveIsAFixedMessage() = savingFailsWith(java.io.IOException("secret detail"))

    @Test fun theIntroDoesNotPromiseVisitorsNeverSeeIt() {
        val text = File("src/main/res/values/strings.xml").readText()
        assertFalse(text.contains("Visitors never see this screen"))
        assertTrue(text.contains("hidden while stand mode is on"))
    }

    @Test fun removeKeyDeletesTheKeyTheEncryptionKeyAndSwitchesTheCloudOff() {
        host.saveKey(TEST_KEY)
        host.switch.turnOn()
        show()
        compose.onNodeWithTag(CloudKeyTags.REMOVE).performClick()
        compose.waitForIdle()
        assertFalse(host.key.isSet())
        assertEquals(1, cipher.destroyed)
        assertFalse(host.switch.isOn)
        compose.onNodeWithTag(CloudKeyTags.STATUS).assertTextContains("No key")
    }

    @Test fun theSwitchNeedsAKeyIsOffByDefaultAndCannotBeTurnedOnInStandMode() {
        show()
        compose.onNodeWithTag(CloudKeyTags.SWITCH).assertIsNotEnabled() // no key yet
        assertFalse(host.switch.isOn)
        host.saveKey(TEST_KEY)
        stand = true
        assertFalse("the switch refuses in stand mode", host.switch.turnOn())
    }

    @Test fun aKeyAndNoStandModeLetStaffTurnTheSwitchOn() {
        host.saveKey(TEST_KEY)
        show()
        compose.onNodeWithTag(CloudKeyTags.SWITCH).assertIsEnabled().performClick()
        compose.waitForIdle()
        assertTrue(host.switch.isOn)
    }

    @Test fun inStandModeTheSwitchIsDisabledOnTheScreenToo() {
        host.saveKey(TEST_KEY)
        show(standOn = true)
        compose.onNodeWithTag(CloudKeyTags.SWITCH).assertIsNotEnabled()
    }

    @Test fun resetAsksFirstShowingTheAmountAndOnlyConfirmingZeroesIt() {
        host.saveKey(TEST_KEY)
        host.spend.record(1_230_000) // US$1.23
        show()
        compose.onNodeWithTag(CloudKeyTags.RESET).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CloudKeyTags.RESET_CONFIRM).assertIsDisplayed()
        assertEquals("nothing is reset before the confirmation", 1_230_000L, host.spend.snapshot().totalSpentMicros)
        assertTrue(allText().contains("1.23"))
        compose.onNodeWithTag(CloudKeyTags.RESET_CANCEL).performClick()
        compose.waitForIdle()
        assertEquals(1_230_000L, host.spend.snapshot().totalSpentMicros)
        compose.onNodeWithTag(CloudKeyTags.RESET).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CloudKeyTags.RESET_CONFIRM).performClick()
        compose.waitForIdle()
        assertEquals(0L, host.spend.snapshot().totalSpentMicros)
    }

    @Test fun theCapsShownAreTheCodeConstantsNotAnythingTheScreenSets() {
        host.saveKey(TEST_KEY)
        show()
        compose.onNodeWithTag(CloudKeyTags.SPEND).assertTextContains(
            "US$" + ai.riskmandate.app.cloud.CloudSpend.usd(CloudLimits.TOTAL_CAP_MICROS), substring = true,
        )
    }

    @Test fun theWindowIsFlagSecureOnlyWhileTheKeyScreenShows() {
        var showing by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { RiskMandateTheme { if (showing) CloudKeyScreen(host, false) {} } }
        compose.waitForIdle()
        val flags = { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE }
        assertTrue("FLAG_SECURE while the key screen is shown", flags() != 0)
        showing = false
        compose.waitForIdle()
        assertEquals("and cleared when it is left", 0, flags())
    }

    @Test fun theModelIsAChoiceFromThePinnedListOnly() {
        host.choose("not/a-real-model")
        assertEquals(ai.riskmandate.app.cloud.CloudModels.GEMMA4, host.model)
        host.choose(ai.riskmandate.app.cloud.CloudModels.SONNET.id)
        assertEquals(ai.riskmandate.app.cloud.CloudModels.SONNET, host.model)
    }

    @Test fun theServiceIsReusedPerModelAndRebuiltWhenTheModelChanges() {
        val a = host.service()
        assertTrue(a === host.service())
        host.choose(ai.riskmandate.app.cloud.CloudModels.SONNET.id)
        assertFalse(a === host.service())
    }
}

class CloudWiringTest {
    private val service: () -> ai.riskmandate.app.llm.LlmService = { ai.riskmandate.app.llm.FakeLlmService(reply = { ai.riskmandate.app.llm.FakeReply("x") }) }

    @Test fun cloudLlmReachesOnlyAFeatureThatDeclaresItWithAKeyTheSwitchOnAndNoStandMode() {
        assertTrue(cloudLlmFor(usesCloud = true, keySet = true, switchOn = true, standOn = false, service) != null)
        assertNull("feature does not declare usesCloud", cloudLlmFor(false, true, true, false, service))
        assertNull("no key", cloudLlmFor(true, false, true, false, service))
        assertNull("switch off", cloudLlmFor(true, true, false, false, service))
        assertNull("stand mode", cloudLlmFor(true, true, true, true, service))
    }

    @Test fun cloudIsOnOnlyWithAKeyTheSwitchAndNoStandMode() {
        assertTrue(cloudIsOn(keySet = true, switchOn = true, standOn = false))
        assertFalse(cloudIsOn(false, true, false))
        assertFalse(cloudIsOn(true, false, false))
        assertFalse(cloudIsOn(true, true, true))
    }

    @Test fun theServiceIsNotEvenBuiltWhenTheAnswerIsNull() {
        var built = 0
        cloudLlmFor(true, true, false, false) { built++; service() }
        cloudLlmFor(false, true, true, false) { built++; service() }
        assertEquals(0, built)
    }

    @Test fun noFeatureOnThisBranchDeclaresUsesCloudYetOrItIsKnownAndListed() {
        // A feature that declares usesCloud is reviewed like a new permission: this list is the review's record.
        val declared = ai.riskmandate.app.features.FEATURES.filter { it.usesCloud }.map { it.id }.toSet()
        val reviewed = setOf("workbench", "chat")
        assertTrue("usesCloud features not reviewed: ${declared - reviewed}", reviewed.containsAll(declared))
    }

    @Test fun theModelsPrivacyLineIsTrueNowThatTheCloudExists() {
        // R-models-1: "Nothing you type leaves the device" is wrong once Chat and the Workbench have a cloud route.
        for (file in listOf("src/main/res/values/feature_models.xml", "src/main/res/values-pt-rPT/feature_models.xml")) {
            val line = Regex("""<string name="models_privacy">(.*?)</string>""").find(File(file).readText())!!.groupValues[1].lowercase()
            assertFalse("$file: unconditional claim", line.contains("nothing you type leaves") || line.contains("nada do que escreve sai"))
            assertTrue("$file: the claim is conditional on the cloud being off", line.contains("cloud model off") || line.contains("nuvem desligado"))
        }
    }

    @Test fun hostStringsMakeNoOnDeviceOnlyPromise() {
        // The cloud makes "stays on this device" claims conditional: the host's own strings must not make them.
        val text = listOf("src/main/res/values/strings.xml", "src/main/res/values-pt-rPT/strings.xml").joinToString("\n") { File(it).readText() }.lowercase()
        for (claim in listOf("stays on this device", "nothing you type leaves", "runs on this device", "never leaves the device", "fica neste dispositivo", "nada do que escreve sai")) {
            assertFalse("host string makes the claim '$claim'", text.contains(claim))
        }
    }
}

@RunWith(AndroidJUnit4::class)
class CloudHostActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun everyFeatureGetsTheCloudOnFactButOnlyUsesCloudFeaturesGetTheService() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val svc: () -> ai.riskmandate.app.llm.LlmService = { ai.riskmandate.app.llm.FakeLlmService(reply = { ai.riskmandate.app.llm.FakeReply("x") }) }
        fun ctx(on: Boolean, usesCloud: Boolean) = hostFeatureContext(
            context, {}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null), { {} }, {},
            cloudLlm = cloudLlmFor(usesCloud, on, on, false, svc), cloudOn = cloudIsOn(on, on, false),
        )
        // an on-device feature (no usesCloud) still learns that the cloud is on, and gets no service
        assertTrue(ctx(on = true, usesCloud = false).cloudOn)
        assertNull(ctx(on = true, usesCloud = false).cloudLlm)
        // a cloud feature gets both
        assertTrue(ctx(on = true, usesCloud = true).cloudOn)
        assertTrue(ctx(on = true, usesCloud = true).cloudLlm != null)
        // off: neither
        assertFalse(ctx(on = false, usesCloud = true).cloudOn)
        assertNull(ctx(on = false, usesCloud = true).cloudLlm)
    }

    @Test fun theKeyIsNotInSavedStateAndTheDiagnosticsReportNeverHasIt() {
        val dir = java.nio.file.Files.createTempDirectory("cloud").toFile()
        try {
            val host = CloudHost(dir, SoftwareCipher()) { false }
            host.saveKey(TEST_KEY)
            CloudProcess.setForTest(host)
            // the Diagnostics report (what Copy puts on the clipboard) has no cloud or key field
            val report = DiagnosticsInfo.collect(compose.activity, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null)).toText()
            assertFalse(report.contains(TEST_KEY))
            assertFalse(report.contains("sk-or-"))
            compose.onNodeWithTag(HomeTags.DIAGNOSTICS).performScrollTo().performClick()
            compose.onNodeWithTag(DiagnosticsTags.CLOUD).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(CloudKeyTags.FIELD).performTextInput("sk-or-typed-but-never-saved-1234")
            compose.activityRule.scenario.recreate() // a rotation: nothing typed may come back
            compose.waitForIdle()
            compose.onNodeWithTag(CloudKeyTags.FIELD).assertIsDisplayed()
            assertFalse("typed text must not survive recreation", compose.onAllNodes(androidx.compose.ui.test.hasText("sk-or-typed-but-never-saved-1234")).fetchSemanticsNodes().isNotEmpty())
        } finally {
            CloudProcess.setForTest(null)
            dir.deleteRecursively()
        }
    }
}
