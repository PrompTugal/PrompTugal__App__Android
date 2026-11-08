package ai.riskmandate.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

enum class OpenResult { CUSTOM_TAB, BROWSER, NO_BROWSER, REFUSED }

/** Opens a riskmandate.ai page: in a Custom Tab when a browser supports them, else in any browser. */
object Browser {
    fun open(context: Context, url: String): OpenResult {
        if (!Links.isAllowed(url)) return OpenResult.REFUSED
        val uri = Uri.parse(url)
        val customTabsPackage = try {
            CustomTabsClient.getPackageName(context, emptyList())
        } catch (e: RuntimeException) {
            null
        }
        if (customTabsPackage != null) {
            try {
                val tab = CustomTabsIntent.Builder().build()
                tab.intent.setPackage(customTabsPackage)
                if (context !is Activity) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                tab.launchUrl(context, uri)
                return OpenResult.CUSTOM_TAB
            } catch (e: ActivityNotFoundException) {
                // Fall through to a plain browser.
            }
        }
        return try {
            val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(view)
            OpenResult.BROWSER
        } catch (e: ActivityNotFoundException) {
            OpenResult.NO_BROWSER
        }
    }

    fun message(result: OpenResult): String? = when (result) {
        OpenResult.CUSTOM_TAB, OpenResult.BROWSER -> null
        OpenResult.NO_BROWSER -> "No web browser is installed on this device, so riskmandate.ai cannot be opened."
        OpenResult.REFUSED -> "That address is not on riskmandate.ai, so the app will not open it."
    }
}
