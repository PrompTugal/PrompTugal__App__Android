package ai.riskmandate.app.features.chat

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Where chat history lives. The app uses a file in app-private storage; tests use memory. */
interface ChatHistoryStore {
    fun load(): List<Conversation>
    fun save(conversations: List<Conversation>)
    fun clear()
}

/**
 * History as one JSON file in `noBackupFilesDir/feature_chat/`: app-private, never in cloud backup (the platform
 * excludes that folder; the app's backup rules exclude every domain as well), never exported or shared. Written to a
 * temporary file
 * and renamed, so a crash never leaves half a file. A damaged file reads as empty history.
 */
class FileChatHistoryStore(private val dir: File) : ChatHistoryStore {
    private val file get() = File(dir, FILE)

    @Synchronized
    override fun load(): List<Conversation> = try {
        if (!file.isFile) emptyList() else decode(file.readText(Charsets.UTF_8))
    } catch (e: Exception) { // IOException, JSONException, anything odd in an old file
        emptyList()
    }

    @Synchronized
    override fun save(conversations: List<Conversation>) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(encode(conversations), Charsets.UTF_8)
        if (!tmp.renameTo(file)) { // some file systems refuse to rename over an existing file
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot replace $file")
        }
    }

    @Synchronized
    override fun clear() {
        File(dir, "$FILE.tmp").delete()
        file.delete()
    }

    companion object {
        const val FILE = "history.json"
        private const val VERSION = 1

        fun encode(conversations: List<Conversation>): String {
            val arr = JSONArray()
            for (c in conversations.take(ChatLimits.MAX_CONVERSATIONS)) {
                val msgs = JSONArray()
                for (m in c.messages.takeLast(ChatLimits.MAX_MESSAGES)) {
                    msgs.put(JSONObject().put("r", m.role.name).put("t", m.text.take(ChatLimits.MAX_MESSAGE_CHARS))
                        .put("n", m.tokens).put("ms", m.millis).put("tps", m.tokensPerSecond)
                        .put("s", m.stopped).put("f", m.failed).put("fr", m.failure ?: JSONObject.NULL)
                        .put("via", m.via ?: JSONObject.NULL).put("cost", m.costUsd ?: JSONObject.NULL)
                        .put("g", m.grounding?.let(::encodeGrounding) ?: JSONObject.NULL))
                }
                arr.put(JSONObject().put("id", c.id).put("m", msgs))
            }
            return JSONObject().put("v", VERSION).put("c", arr).toString()
        }

        private fun encodeGrounding(g: ChatGrounding): JSONObject = JSONObject()
            .put("s", JSONArray().also { arr ->
                for (x in g.sources.take(8)) arr.put(JSONObject().put("n", x.n).put("t", x.title.take(300)).put("k", x.kind).put("u", x.source.take(300)))
            })
            .put("a", g.attribution.take(1_000))
            .put("b", JSONArray(g.badRefs.take(20)))

        private fun decodeGrounding(o: JSONObject): ChatGrounding {
            val s = o.optJSONArray("s")
            val sources = (0 until minOf(s?.length() ?: 0, 8)).mapNotNull { i ->
                val x = s!!.optJSONObject(i) ?: return@mapNotNull null
                ChatSource(x.optInt("n"), x.optString("t").take(300), x.optString("k"), x.optString("u").take(300))
            }
            val b = o.optJSONArray("b")
            return ChatGrounding(sources, o.optString("a").take(1_000), (0 until minOf(b?.length() ?: 0, 20)).map { b!!.optInt(it) })
        }

        fun decode(text: String): List<Conversation> {
            val root = JSONObject(text)
            if (root.optInt("v") != VERSION) return emptyList()
            val arr = root.getJSONArray("c")
            val out = ArrayList<Conversation>()
            for (i in 0 until minOf(arr.length(), ChatLimits.MAX_CONVERSATIONS)) {
                val o = arr.getJSONObject(i)
                val ms = o.getJSONArray("m")
                val messages = (0 until minOf(ms.length(), ChatLimits.MAX_MESSAGES)).mapNotNull { j ->
                    val m = ms.getJSONObject(j)
                    val role = ChatRole.entries.firstOrNull { it.name == m.optString("r") } ?: return@mapNotNull null
                    ChatMessage(role, m.optString("t").take(ChatLimits.MAX_MESSAGE_CHARS), m.optInt("n"), m.optLong("ms"),
                        m.optDouble("tps", 0.0), m.optBoolean("s"), m.optBoolean("f"), m.optString("fr").takeIf { it.isNotEmpty() && it != "null" },
                        m.optString("via").takeIf { it.isNotEmpty() && it != "null" }, if (m.isNull("cost")) null else m.optDouble("cost"),
                        m.optJSONObject("g")?.let(::decodeGrounding))
                }
                out += Conversation(o.getString("id"), messages)
            }
            return out
        }
    }
}

/** In-memory store for tests. */
class MemoryChatHistoryStore(var saved: List<Conversation> = emptyList()) : ChatHistoryStore {
    var clears = 0
    override fun load() = saved
    override fun save(conversations: List<Conversation>) { saved = conversations }
    override fun clear() { saved = emptyList(); clears++ }
}
