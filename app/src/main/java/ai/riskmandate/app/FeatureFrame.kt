package ai.riskmandate.app

import ai.riskmandate.app.ui.ScreenHeader
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

object FeatureFrameTags {
    const val TITLE = "feature_title"
}

/** True while the shell shows its permanent sidebar (then there is no top bar, so the frame draws the header). */
val LocalSidebarVisible = staticCompositionLocalOf { false }

/**
 * The host's frame around every summit feature: system-bar insets and one header. With the sidebar
 * (tablet) the header is a [ScreenHeader], kicker and serif title, and there is no Back button: the
 * selected sidebar item is the way home. On a phone the shell's top bar shows the title (menu button and
 * title), so no second header is drawn and the system Back goes to Now. A feature draws only its content:
 * no title, no inset padding, no Back (architecture rule, one header per feature).
 */
@Composable
fun FeatureFrame(@StringRes title: Int, @StringRes kicker: Int?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (LocalSidebarVisible.current) {
            ScreenHeader(
                kicker = stringResource(kicker ?: R.string.app_name),
                title = stringResource(title),
                modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 24.dp).testTag(FeatureFrameTags.TITLE),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

/**
 * The previous shape, kept so feature sessions' screenshot tests keep compiling: [onBack] is ignored (the
 * sidebar or the system Back is the way home now) and the kicker is the app name. Use the one above.
 */
@Deprecated("Back is no longer drawn: use FeatureFrame(title, kicker) { }")
@Composable
fun FeatureFrame(@StringRes title: Int, onBack: () -> Unit, content: @Composable () -> Unit) =
    FeatureFrame(title, null, content)
