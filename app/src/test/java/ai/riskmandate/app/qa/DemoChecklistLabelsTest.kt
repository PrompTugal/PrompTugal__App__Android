package ai.riskmandate.app.qa

import ai.riskmandate.app.llm.StrictJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps `docs/qa/demo-run-wednesday.md` true (QA): every label the checklist tells a person to look for is a real string
 * of the app, in English and where the checklist says it, in pt-PT, and the checklist really contains it. If an owner renames
 * a label or a case study, this fails and the checklist is fixed the same day, not on the stage.
 */
class DemoChecklistLabelsTest {
    private val res = "src/main/res"
    private val doc = File("../docs/qa/demo-run-wednesday.md").readText()

    private fun strings(dir: String, file: String): Map<String, String> {
        val text = File("$res/$dir/$file").readText()
        return Regex("""<string name="([a-z0-9_]+)"[^>]*>([^<]*)</string>""").findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&") }
    }

    private fun scenarioTitles(): Map<String, Pair<String, String>> =
        (File("src/main/assets/feature_lab/scenarios").listFiles()!!).filter { it.name.startsWith("case-") }.associate { f ->
            val m = StrictJson.parse(f.readText(), 2_000_000) as Map<*, *>
            val pt = ((m["i18n"] as Map<*, *>)["pt-PT"] as Map<*, *>)["title"] as String
            m["id"] as String to ((m["title"] as String) to pt)
        }

    @Test fun theSixCaseStudyTitlesAreTheOnesTheChecklistNamesInEnglishAndPortuguese() {
        val expected = mapOf(
            "case-outreach" to ("Outreach drafts, a person sends" to "O Outreach escreve rascunhos, uma pessoa envia"),
            "case-security-hold" to ("Security stops work, never restarts it" to "A Segurança põe o trabalho em pausa, nunca o retoma"),
            "case-instructions" to ("A message is data, not an instruction" to "Uma mensagem é dados, não uma instrução"),
            "case-dev-main" to ("Dev pushes to dev, not to main" to "O Dev publica em dev, não em main"),
            "case-researcher-login" to ("Researcher stays out of members-only pages" to "O Researcher fica fora das páginas só para membros"),
            "case-journalist-source" to ("Journalist gives each claim its source" to "O Journalist dá a cada afirmação a sua fonte"),
        )
        assertEquals(expected, scenarioTitles())
        for ((en, _) in expected.values) assertTrue("the checklist must name the case study '$en'", doc.contains(en))
    }

    @Test fun theChatLabelsOfTheChecklistAreTheAppsOwn() {
        val en = strings("values", "feature_chat.xml")
        val pt = strings("values-pt-rPT", "feature_chat.xml")
        val labels = listOf(
            "chat_badge" to "Runs on this device", "chat_badge_cloud" to "Cloud: openrouter.ai", "chat_network_on" to "Network: on", "chat_network_off" to "Network: off",
            "chat_new" to "New chat", "chat_history" to "History", "chat_clear" to "Clear history", "chat_clear_title" to "Delete every saved chat?",
            "chat_clear_confirm" to "Delete", "chat_cancel" to "Cancel", "chat_stop" to "Stop", "chat_stopped" to "Stopped",
            "chat_preset_rm_title" to "What is RiskMandate?", "chat_preset_abp_title" to "Explain an ABP", "chat_open_models" to "Open Models",
            "chat_no_model_title" to "No model installed",
        )
        for ((k, v) in labels) {
            assertEquals("string $k", v, en[k])
            assertTrue("the checklist must contain '$v'", doc.contains(v))
            assertTrue("$k has a pt-PT translation that differs from English", pt[k] != null && pt[k] != v)
        }
        val note = en["chat_history_note"]!!
        assertTrue("the checklist quotes the History note exactly as the app says it: $note", doc.contains(note))
    }

    @Test fun theOurDataAndLabLabelsOfTheChecklistAreTheAppsOwn() {
        val wb = strings("values", "feature_workbench.xml"); val wbPt = strings("values-pt-rPT", "feature_workbench.xml")
        assertEquals("Ask our data", wb["ask_title"]); assertEquals("Our data", wb["workbench_tab_ask"]); assertEquals("No network", wb["ask_offline"])
        assertEquals("Os nossos dados", wbPt["workbench_tab_ask"])
        val lab = strings("values", "feature_lab.xml"); val labPt = strings("values-pt-rPT", "feature_lab.xml")
        assertEquals("Side by side", lab["lab_home_kicker"]); assertEquals("Lado a lado", labPt["lab_home_kicker"])
        for (t in listOf("Ask our data", "Our data", "No network", "Side by side", "Lado a lado", "Os nossos dados"))
            assertTrue("the checklist must contain '$t'", doc.contains(t))
        assertEquals("Demonstration", strings("values", "feature_abpdemo.xml")["abpdemo_kicker"])
        assertTrue(doc.contains("Demonstration"))
    }

    @Test fun theChecklistNeverPromisesWhatTheAppDoesNotSay() {
        // Claims beyond riskmandate.ai are barred by the repo rules; these phrases would be one.
        for (bad in listOf("always right", "as good as", "guaranteed", "100%", "fully secure", "unhackable"))
            assertTrue("the checklist must not contain a promise like '$bad' outside a warning", Regex("(?i)(do not|never|not) [^.\n]*" + Regex.escape(bad)).containsMatchIn(doc) || !doc.contains(bad, ignoreCase = true))
        assertTrue("no em-dashes (house style)", !doc.contains("—"))
    }
}
