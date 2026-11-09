package ai.riskmandate.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Nothing the app stores (chat history, caches, settings) may reach a backup or a device transfer. */
class BackupRulesTest {
    private val domains = setOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref",
    )

    private fun excluded(path: String, section: String? = null): Set<String> {
        var xml = java.io.File(path).readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        if (section != null) xml = Regex("<$section>(.*?)</$section>", RegexOption.DOT_MATCHES_ALL).find(xml)!!.groupValues[1]
        return Regex("""<exclude\s+domain="(\w+)"\s*/>""").findAll(xml).map { it.groupValues[1] }.toSet()
    }

    @Test fun cloudBackupExcludesEveryDomain() =
        assertEquals(domains, excluded("src/main/res/xml/data_extraction_rules.xml", "cloud-backup"))

    @Test fun deviceTransferExcludesEveryDomain() =
        assertEquals(domains, excluded("src/main/res/xml/data_extraction_rules.xml", "device-transfer"))

    @Test fun fullBackupContentExcludesEveryDomain() =
        assertEquals(domains, excluded("src/main/res/xml/backup_rules.xml"))

    @Test fun manifestStillTurnsBackupOffAndPointsAtTheRules() {
        val m = java.io.File("src/main/AndroidManifest.xml").readText()
        assert(m.contains("""android:allowBackup="false""""))
        assert(m.contains("@xml/data_extraction_rules") && m.contains("@xml/backup_rules"))
    }
}
