package ai.riskmandate.app.features.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
class ChatStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store() = FileChatHistoryStore(File(tmp.root, "feature_chat"))

    private val convs = listOf(
        Conversation("a", listOf(ChatMessage(ChatRole.USER, "hi \"quoted\" <b>x</b>\nline2 é中"), ChatMessage(ChatRole.ASSISTANT, "yo", 2, 1500, 3.5, stopped = true))),
        Conversation("b", listOf(ChatMessage(ChatRole.USER, "q"), ChatMessage(ChatRole.ASSISTANT, "bad", failed = true, failure = "LOAD_FAILED"))),
    )

    @Test fun roundTrip() {
        val s = store()
        assertTrue(s.load().isEmpty())
        s.save(convs)
        assertEquals(convs, s.load())
    }

    @Test fun saveReplacesAndLeavesNoTempFile() {
        val s = store()
        s.save(convs); s.save(convs.take(1))
        assertEquals(1, s.load().size)
        assertEquals(listOf(FileChatHistoryStore.FILE), File(tmp.root, "feature_chat").list()!!.toList())
    }

    @Test fun damagedOrUnknownFilesReadAsEmpty() {
        val dir = File(tmp.root, "feature_chat").apply { mkdirs() }
        File(dir, FileChatHistoryStore.FILE).writeText("{ not json")
        assertTrue(store().load().isEmpty())
        File(dir, FileChatHistoryStore.FILE).writeText("""{"v":99,"c":[]}""")
        assertTrue(store().load().isEmpty())
    }

    @Test fun clearDeletesTheFile() {
        val s = store()
        s.save(convs); s.clear()
        assertFalse(File(tmp.root, "feature_chat/${FileChatHistoryStore.FILE}").exists())
        assertTrue(s.load().isEmpty())
    }

    /** Dinis's decision: history never leaves the tablet. Every backup and transfer domain stays excluded. */
    @Test fun backupAndDeviceTransferExcludeEveryDomain() {
        val domains = listOf("root", "file", "database", "sharedpref", "external")
        val rules = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/data_extraction_rules.xml")).documentElement
        for (section in listOf("cloud-backup", "device-transfer")) {
            val node = rules.getElementsByTagName(section).item(0) as Element
            assertTrue("$section must have no <include>", node.getElementsByTagName("include").length == 0)
            val excluded = (0 until node.getElementsByTagName("exclude").length).map { (node.getElementsByTagName("exclude").item(it) as Element).getAttribute("domain") }
            assertEquals("$section must exclude every required domain", emptySet<String>(), domains.toSet() - excluded.toSet())
        }
        val old = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/backup_rules.xml")).documentElement
        val oldExcluded = (0 until old.getElementsByTagName("exclude").length).map { (old.getElementsByTagName("exclude").item(it) as Element).getAttribute("domain") }
        assertEquals("backup_rules.xml must exclude every required domain", emptySet<String>(), domains.toSet() - oldExcluded.toSet())
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    @Test fun limitsAreEnforced() {
        val big = ChatMessage(ChatRole.USER, "z".repeat(ChatLimits.MAX_MESSAGE_CHARS + 10))
        val convs = (1..40).map { Conversation("c$it", listOf(big)) }
        val back = FileChatHistoryStore.decode(FileChatHistoryStore.encode(convs))
        assertEquals(ChatLimits.MAX_CONVERSATIONS, back.size)
        assertEquals(ChatLimits.MAX_MESSAGE_CHARS, back.first().messages.first().text.length)
    }
}
