package ai.riskmandate.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

enum class OpenResult { CUSTOM_TAB, BROWSER, NO_BROWSER, REFUSED, FAILED }

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
            } catch (e: RuntimeException) {
                // No activity, or the browser refused (SecurityException): fall through to a plain browser.
            }
        }
        return try {
            val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(view)
            OpenResult.BROWSER
        } catch (e: ActivityNotFoundException) {
            OpenResult.NO_BROWSER
        } catch (e: RuntimeException) {
            OpenResult.FAILED
        }
    }

    /**
     * True when the system reports no validated internet connection. A hint only: the browser opens
     * regardless and shows its own error page. Any failure to ask counts as online (no false alarm).
     */
    /** Pure decision, testable on the JVM. */
    fun offline(hasActiveNetwork: Boolean, hasInternet: Boolean): Boolean = !hasActiveNetwork || !hasInternet

    fun isOffline(context: Context): Boolean = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            false
        } else {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            offline(caps != null, caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        }
    } catch (e: RuntimeException) {
        false
    }

    @StringRes
    fun message(result: OpenResult): Int? = when (result) {
        OpenResult.CUSTOM_TAB, OpenResult.BROWSER -> null
        OpenResult.NO_BROWSER -> R.string.message_no_browser
        OpenResult.REFUSED -> R.string.message_refused
        OpenResult.FAILED -> R.string.message_failed
    }
}
