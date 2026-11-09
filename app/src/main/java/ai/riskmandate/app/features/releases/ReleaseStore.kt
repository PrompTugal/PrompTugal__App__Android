package ai.riskmandate.app.features.releases

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException

/** Where articles come from. The app reads its assets; tests use a fake. */
interface ArticleSource {
    /** Folder names under `releases/`. */
    fun dirs(): List<String>

    /** The text of `releases/<dir>/<file>`, or null if it is not there. */
    fun read(dir: String, file: String): String?
}

class AssetArticleSource(private val assets: AssetManager) : ArticleSource {
    override fun dirs(): List<String> = try {
        // Immediate children on a device; Robolectric lists nested paths: keep the first segment either way.
        assets.list(ROOT)?.map { it.substringBefore('/') }?.distinct().orEmpty()
    } catch (e: IOException) {
        emptyList()
    }

    override fun read(dir: String, file: String): String? = try {
        assets.open("$ROOT/$dir/$file").use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: IOException) {
        null
    }

    companion object { const val ROOT = "releases" }
}

/**
 * Scans the article folders (no index file to forget), keeps those that parse, newest first.
 * [language] is the device language (`pt`): `article.pt-PT.md` is used when it exists and parses, else `article.md`.
 */
object ReleaseStore {
    const val ARTICLE = "article.md"
    const val ARTICLE_PT = "article.pt-PT.md"

    fun load(source: ArticleSource, language: String): List<Article> =
        source.dirs().mapNotNull { dir ->
            val localised = if (language.equals("pt", ignoreCase = true)) source.read(dir, ARTICLE_PT)?.let { ArticleParser.parse(dir, it) } else null
            localised ?: source.read(dir, ARTICLE)?.let { ArticleParser.parse(dir, it) }
        }.sortedWith(ArticleParser.newestFirst)

    fun load(context: Context, language: String): List<Article> = load(AssetArticleSource(context.applicationContext.assets), language)

    /**
     * Decodes `releases/<dir>/<path>` scaled down so its width is at most [maxWidthPx] (at most 2048): a 10"
     * screenshot is 1600 px wide and the list thumbnail needs far less. Null if missing, unreadable or the path is not allowed.
     */
    fun image(assets: AssetManager, dir: String, path: String, maxWidthPx: Int): Bitmap? {
        if (Markdown.safeImagePath(path) == null || dir.contains('/') || dir.contains("..")) return null
        val file = "${AssetArticleSource.ROOT}/$dir/$path"
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(file).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return null
            val target = maxWidthPx.coerceIn(1, 2048)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            assets.open(file).use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }
}
