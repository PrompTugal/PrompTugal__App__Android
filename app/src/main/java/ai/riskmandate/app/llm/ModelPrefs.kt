package ai.riskmandate.app.llm

import android.content.Context

/**
 * Which installed model to use when there is more than one (feature G's choice). One value in the
 * `feature_models` preferences file; app-private and excluded from backup like every other file.
 */
object ModelPrefs {
    private const val FILE = "feature_models"
    private const val KEY = "preferred_model"

    fun preferred(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { id -> ModelCatalog.ALL.any { it.id == id } }

    fun setPreferred(context: Context, id: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply { if (id == null) remove(KEY) else putString(KEY, id) }.apply()
    }
}
