package ai.riskmandate.app.features.stand

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import ai.riskmandate.app.R
import kotlinx.coroutines.delay

/** Test tags of the stand-mode controls. */
object StandTags {
    const val CORNER = "stand_corner"
    const val SWITCH = "stand_switch"
    const val STATUS = "stand_status"
    const val MORE = "stand_more"
    const val LESS = "stand_less"
}

/**
 * Stand mode (feature D) wraps the whole app: screen pinning, keep-screen-on (view flag), return home when idle.
 *
 * Staff reach the controls with a long-press on the bottom-end corner of the home screen (nothing is drawn
 * there while stand mode is off, a faint dot while it is on; taps and the other screens are never touched).
 *
 * @param atHome true while the home screen shows.
 * @param goHome takes the app back to the home screen (for the idle reset).
 * @param idleHeld true while a feature needs the screen left alone (e.g. an auto-play loop): no idle reset.
 *   When it turns false the countdown restarts from that moment.
 */
@Composable
fun StandHost(
    activity: ComponentActivity,
    atHome: Boolean,
    goHome: () -> Unit,
    idleHeld: Boolean = false,
    content: @Composable () -> Unit,
) {
    val settings = remember(activity) { StandSettings.of(activity) }
    val lock = remember(activity) { ActivityScreenLock(activity) }
    StandHostContent(settings, lock, activity.lifecycle, SystemClock::elapsedRealtime, atHome, goHome, idleHeld, content)
}

private const val CORNER_DP = 48

private enum class Panel { NONE, SETTINGS }

@Composable
internal fun StandHostContent(
    settings: StandSettings,
    lock: ScreenLock,
    lifecycle: Lifecycle,
    clock: () -> Long,
    atHome: Boolean,
    goHome: () -> Unit,
    idleHeld: Boolean,
    content: @Composable () -> Unit,
) {
    var enabled by remember { mutableStateOf(settings.enabled) }
    var minutes by remember { mutableIntStateOf(settings.idleMinutes) }
    var pinned by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(Panel.NONE) }
    // Asked once per task, not on every rotation, so a refused prompt does not come back each time.
    var asked by rememberSaveable { mutableStateOf(false) }
    // Only stop pinning if stand mode was on in this composition: never undo a pin we did not ask for.
    var wasOn by remember { mutableStateOf(false) }

    val timer = remember { IdleTimer(clock, minutes * 60_000L) }
    val currentAtHome by rememberUpdatedState(atHome)
    val currentGoHome by rememberUpdatedState(goHome)
    val currentHeld by rememberUpdatedState(idleHeld)
    // What the idle countdown last saw. The timer starts counting when it is created, so the first run of
    // an effect must not restart it (effects run a little later, and would swallow time already idle).
    val seen = remember { object { var enabled = enabled; var held = idleHeld } }
    // Released: count from now, so the reset does not fire at once for time spent held.
    LaunchedEffect(idleHeld) {
        if (seen.held != idleHeld) {
            seen.held = idleHeld
            if (!idleHeld) timer.touch()
        }
    }

    // Keep the screen on with the view flag: no WAKE_LOCK permission needed.
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(enabled) {
        if (enabled) {
            if (!seen.enabled) timer.touch()
            seen.enabled = true
            if (!asked && !lock.isLocked()) {
                asked = true
                lock.lock()
            }
            wasOn = true
        } else {
            seen.enabled = false
        }
        if (!enabled && wasOn) {
            lock.unlock()
            asked = false
            wasOn = false
        }
    }
    LaunchedEffect(enabled, panel) {
        // Android's prompt is asynchronous: look again shortly, to tell staff whether it took.
        pinned = lock.isLocked()
        delay(1_500)
        pinned = lock.isLocked()
    }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        // Only counts while the app is in front; coming back after a long time away resets at once.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (currentHeld) timer.touch()
                else if (timer.poll() && !currentAtHome) currentGoHome()
                delay(1_000)
            }
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val homeNow by rememberUpdatedState(atHome)
    Box(
        Modifier.fillMaxSize().pointerInput(layoutDirection) {
            // Watches every touch on the way down without consuming any, so nothing underneath loses a tap.
            // Also spots a long-press on the corner (home screen only): finger down there, still, for the timeout.
            val cornerPx = CORNER_DP.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                timer.touch()
                val endEdge = size.width - insets.getRight(this@pointerInput, layoutDirection)
                val startEdge = insets.getLeft(this@pointerInput, layoutDirection)
                val bottomEdge = size.height - insets.getBottom(this@pointerInput)
                val inCorner = if (layoutDirection == LayoutDirection.Ltr) {
                    down.position.x >= endEdge - cornerPx && down.position.x <= endEdge
                } else {
                    down.position.x <= startEdge + cornerPx && down.position.x >= startEdge
                } && down.position.y >= bottomEdge - cornerPx && down.position.y <= bottomEdge
                if (homeNow && inCorner) {
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            timer.touch()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                            if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                return@withTimeoutOrNull false
                            }
                        }
                        @Suppress("UNREACHABLE_CODE") true
                    }
                    if (held == null) panel = Panel.SETTINGS
                }
                // Rest of the gesture: keep counting it as activity.
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    timer.touch()
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
        content()
        if (atHome) {
            // Draws and handles nothing: a faint dot while stand mode is on, and a tag for tests.
            Box(
                Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).size(CORNER_DP.dp)
                    .testTag(StandTags.CORNER),
                contentAlignment = Alignment.Center,
            ) {
                if (enabled) {
                    Box(
                        Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape),
                    )
                }
            }
        }
    }

    when (panel) {
        Panel.NONE -> Unit
        Panel.SETTINGS -> SettingsDialog(
            enabled = enabled,
            pinned = pinned,
            minutes = minutes,
            onEnabled = { on -> settings.enabled = on; enabled = on },
            onMinutes = { m ->
                settings.idleMinutes = m
                minutes = settings.idleMinutes
                timer.timeoutMs = minutes * 60_000L
            },
            onClose = { panel = Panel.NONE },
        )
    }
}

@Composable
private fun SettingsDialog(
    enabled: Boolean,
    pinned: Boolean,
    minutes: Int,
    onEnabled: (Boolean) -> Unit,
    onMinutes: (Int) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.stand_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.stand_switch), Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = onEnabled, modifier = Modifier.testTag(StandTags.SWITCH))
                }
                if (enabled) {
                    Text(
                        stringResource(if (pinned) R.string.stand_status_pinned else R.string.stand_status_not_pinned),
                        Modifier.testTag(StandTags.STATUS),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMinutes(minutes - 1) }, modifier = Modifier.testTag(StandTags.LESS)) {
                        Text(stringResource(R.string.stand_less))
                    }
                    Text(stringResource(R.string.stand_idle, minutes), Modifier.weight(1f))
                    TextButton(onClick = { onMinutes(minutes + 1) }, modifier = Modifier.testTag(StandTags.MORE)) {
                        Text(stringResource(R.string.stand_more))
                    }
                }
                Text(stringResource(R.string.stand_unpin_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.stand_close)) } },
    )
}
