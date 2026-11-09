package ai.riskmandate.app.features.stand

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import ai.riskmandate.app.R
import kotlinx.coroutines.delay

/** Test tags of the stand-mode controls. */
object StandTags {
    const val CORNER = "stand_corner"
    const val SWITCH = "stand_switch"
    const val PIN_FIELD = "stand_pin_field"
    const val PIN_OK = "stand_pin_ok"
    const val STATUS = "stand_status"
    const val MORE = "stand_more"
    const val LESS = "stand_less"
}

/**
 * Stand mode (feature D) wraps the whole app: screen pinning, keep-screen-on, return home when idle.
 *
 * Staff reach the controls with a long-press on the bottom-end corner (nothing is drawn there while
 * stand mode is off; a faint dot shows while it is on). With a staff PIN set, the PIN is asked first.
 *
 * @param atHome true while the home screen shows.
 * @param goHome takes the app back to the home screen (for the idle reset).
 */
@Composable
fun StandHost(activity: ComponentActivity, atHome: Boolean, goHome: () -> Unit, content: @Composable () -> Unit) {
    val settings = remember(activity) { StandSettings.of(activity) }
    val lock = remember(activity) { ActivityScreenLock(activity) }
    StandHostContent(settings, lock, activity.lifecycle, SystemClock::elapsedRealtime, atHome, goHome, content)
}

private enum class Panel { NONE, PIN, SETTINGS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StandHostContent(
    settings: StandSettings,
    lock: ScreenLock,
    lifecycle: Lifecycle,
    clock: () -> Long,
    atHome: Boolean,
    goHome: () -> Unit,
    content: @Composable () -> Unit,
) {
    var enabled by remember { mutableStateOf(settings.enabled) }
    var minutes by remember { mutableIntStateOf(settings.idleMinutes) }
    var hasPin by remember { mutableStateOf(settings.hasPin) }
    var pinned by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(Panel.NONE) }
    // Asked once per task, not on every rotation, so a refused prompt does not come back each time.
    var asked by rememberSaveable { mutableStateOf(false) }
    // Only stop pinning if stand mode was on in this composition: never undo a pin we did not ask for.
    var wasOn by remember { mutableStateOf(false) }

    val timer = remember { IdleTimer(clock, minutes * 60_000L) }
    val gate = remember { PinGate(clock) }
    val currentAtHome by rememberUpdatedState(atHome)
    val currentGoHome by rememberUpdatedState(goHome)

    LaunchedEffect(enabled) {
        lock.keepScreenOn(enabled)
        if (enabled) {
            timer.touch()
            if (!asked && !lock.isLocked()) {
                asked = true
                lock.lock()
            }
            wasOn = true
        } else if (wasOn) {
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
                if (timer.poll() && !currentAtHome) currentGoHome()
                delay(1_000)
            }
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Watches every touch without consuming it.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    timer.touch()
                }
            }
        },
    ) {
        content()
        val label = stringResource(R.string.stand_corner_label)
        Box(
            Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).size(48.dp)
                .testTag(StandTags.CORNER)
                .combinedClickable(onClickLabel = null, onClick = {}, onLongClickLabel = label, onLongClick = {
                    panel = if (hasPin) Panel.PIN else Panel.SETTINGS
                }),
            contentAlignment = Alignment.Center,
        ) {
            if (enabled) {
                Box(
                    Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape)
                        .clearAndSetSemantics {},
                )
            }
        }
    }

    when (panel) {
        Panel.NONE -> Unit
        Panel.PIN -> PinDialog(
            gate = gate,
            check = { settings.checkPin(it) },
            onOk = { panel = Panel.SETTINGS },
            onCancel = { panel = Panel.NONE },
        )
        Panel.SETTINGS -> SettingsDialog(
            enabled = enabled,
            pinned = pinned,
            minutes = minutes,
            hasPin = hasPin,
            onEnabled = { on -> settings.enabled = on; enabled = on },
            onMinutes = { m ->
                settings.idleMinutes = m
                minutes = settings.idleMinutes
                timer.timeoutMs = minutes * 60_000L
            },
            onSetPin = { pin -> settings.setPin(pin).also { hasPin = settings.hasPin } },
            onClearPin = { settings.clearPin(); hasPin = false },
            onClose = { panel = Panel.NONE },
        )
    }
}

@Composable
private fun PinDialog(gate: PinGate, check: (String) -> Boolean, onOk: () -> Unit, onCancel: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.stand_pin_prompt)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c in '0'..'9' }.take(8); error = null },
                    label = { Text(stringResource(R.string.stand_pin_field)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.testTag(StandTags.PIN_FIELD),
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(StandTags.PIN_OK),
                onClick = {
                    if (gate.attempt { check(pin) }) onOk()
                    else error = if (gate.isLocked()) R.string.stand_pin_locked else R.string.stand_pin_wrong
                    pin = ""
                },
            ) { Text(stringResource(R.string.stand_ok)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.stand_cancel)) } },
    )
}

@Composable
private fun SettingsDialog(
    enabled: Boolean,
    pinned: Boolean,
    minutes: Int,
    hasPin: Boolean,
    onEnabled: (Boolean) -> Unit,
    onMinutes: (Int) -> Unit,
    onSetPin: (String) -> Boolean,
    onClearPin: () -> Unit,
    onClose: () -> Unit,
) {
    var newPin by rememberSaveable { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
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
                Text(
                    stringResource(if (hasPin) R.string.stand_pin_is_set else R.string.stand_pin_not_set),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter { c -> c in '0'..'9' }.take(8); pinError = false },
                    label = { Text(stringResource(R.string.stand_pin_field)) },
                    singleLine = true,
                    isError = pinError,
                    supportingText = if (pinError) ({ Text(stringResource(R.string.stand_pin_invalid)) }) else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Row {
                    TextButton(onClick = {
                        if (onSetPin(newPin)) { newPin = ""; pinError = false } else pinError = true
                    }) { Text(stringResource(R.string.stand_pin_set)) }
                    if (hasPin) TextButton(onClick = onClearPin) { Text(stringResource(R.string.stand_pin_remove)) }
                }
                Text(stringResource(R.string.stand_unpin_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.stand_close)) } },
    )
}
