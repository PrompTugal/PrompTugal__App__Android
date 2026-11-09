package ai.riskmandate.app.features.releases

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import java.io.InputStream

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

/** Where article images come from: the app's assets, or a fixture in tests. */
fun interface ArticleImages {
    /** A fresh stream of `releases/<dir>/<path>`, or null if it is not there. The caller closes it. */
    fun open(dir: String, path: String): InputStream?
}

class AssetArticleImages(private val assets: AssetManager) : ArticleImages {
    override fun open(dir: String, path: String): InputStream? = try {
        assets.open("${AssetArticleSource.ROOT}/$dir/$path")
    } catch (e: IOException) {
        null
    }
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
    fun image(images: ArticleImages, dir: String, path: String, maxWidthPx: Int): Bitmap? {
        if (Markdown.safeImagePath(path) == null || dir.contains('/') || dir.contains("..")) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream returns null when only the bounds are read: check the stream exists, not the result.
            (images.open(dir, path) ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return null
            val target = maxWidthPx.coerceIn(1, 2048)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            images.open(dir, path)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }
}
