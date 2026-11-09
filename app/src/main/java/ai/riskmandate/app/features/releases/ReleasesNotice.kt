package ai.riskmandate.app.features.releases

import android.content.Context

/** The last versionCode whose article the user was shown. Zero: never. */
interface SeenStore {
    var lastSeen: Int
}

/** SharedPreferences file `feature_releases` (architecture rules: one file per feature, this package only). */
class PrefsSeenStore(context: Context) : SeenStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    override var lastSeen: Int
        get() = prefs.getInt(KEY, 0)
        set(value) = prefs.edit().putInt(KEY, value).apply()

    companion object {
        const val FILE = "feature_releases"
        const val KEY = "last_seen_version_code"
    }
}

/**
 * "Show once after an update". The host (HomeScreen/MainActivity, app.android) asks [shouldOffer] with
 * `BuildConfig.VERSION_CODE` and, when true, offers or opens the `releases` route. Opening the screen calls
 * [markSeen], so the offer appears once per version. Nothing is offered when there is no article to show.
 * First install counts as "newer" (nothing seen yet): the newest article is the welcome.
 */
object ReleasesNotice {
    /** Pure decision. */
    fun isNewer(versionCode: Int, lastSeen: Int): Boolean = versionCode > lastSeen

    fun shouldOffer(versionCode: Int, store: SeenStore, hasArticles: Boolean): Boolean =
        hasArticles && isNewer(versionCode, store.lastSeen)

    /** Never moves backwards: a downgrade does not make an old version "unseen" again. */
    fun markSeen(versionCode: Int, store: SeenStore) {
        if (versionCode > store.lastSeen) store.lastSeen = versionCode
    }

    // Host-facing helpers over the real prefs and assets (cheap: one folder listing and one int).
    fun shouldOffer(context: Context, versionCode: Int): Boolean =
        shouldOffer(versionCode, PrefsSeenStore(context), AssetArticleSource(context.applicationContext.assets).dirs().isNotEmpty())

    fun markSeen(context: Context, versionCode: Int) = markSeen(versionCode, PrefsSeenStore(context))
}
