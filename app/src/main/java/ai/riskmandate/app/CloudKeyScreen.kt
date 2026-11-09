package ai.riskmandate.app

import ai.riskmandate.app.cloud.CloudLimits
import ai.riskmandate.app.cloud.CloudModels
import ai.riskmandate.app.cloud.CloudSpend
import ai.riskmandate.app.cloud.KeyCheck
import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

object CloudKeyTags {
    const val STATUS = "cloud_status"
    const val FIELD = "cloud_key_field"
    const val SAVE = "cloud_key_save"
    const val REMOVE = "cloud_key_remove"
    const val SWITCH = "cloud_switch"
    const val MESSAGE = "cloud_message"
    const val SPEND = "cloud_spend"
    const val RESET = "cloud_reset"
    const val RESET_CONFIRM = "cloud_reset_confirm"
    const val RESET_CANCEL = "cloud_reset_cancel"
    fun model(id: String) = "cloud_model_$id"
}

/**
 * Staff only (Diagnostics, hidden in stand mode; docs/security/summit-checklist.md M.1, M.4, M.5): the OpenRouter
 * key, the switch and the spending. The key is typed or pasted with the system's own paste into a password field
 * (the app never reads the clipboard), checked for shape, then kept encrypted; the field is cleared the moment it
 * is saved or this screen is left, and it is never in saved state. The screen shows only "Key set" or "No key".
 * While this screen shows, the window is `FLAG_SECURE` (no screenshot, no recents thumbnail) and autofill is off.
 */
@Composable
fun CloudKeyScreen(host: CloudHost, standOn: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    // The pasted text lives only in this composition: not rememberSaveable, so it is not in any saved state.
    var pasted by remember { mutableStateOf("") }
    var keySet by remember { mutableStateOf(host.key.isSet()) }
    var switchOn by remember { mutableStateOf(host.switch.isOn) }
    var model by remember { mutableStateOf(host.model.id) }
    var message by remember { mutableStateOf<Int?>(null) }
    var snapshot by remember { mutableStateOf(host.spend.snapshot()) }
    var confirmReset by remember { mutableStateOf(false) }

    // FLAG_SECURE and no autofill while the key screen is shown; both undone when it is left, and the field cleared.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val had = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val oldAutofill = view.importantForAutofill
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose {
            pasted = ""
            if (!had) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            view.importantForAutofill = oldAutofill
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.cloud_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = Pt.colors.muted,
        )
        Text(
            stringResource(if (keySet) R.string.cloud_key_set else R.string.cloud_key_none),
            style = MaterialTheme.typography.titleMedium,
            color = Pt.colors.ink,
            modifier = Modifier.testTag(CloudKeyTags.STATUS).semantics { heading() },
        )
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            label = { Text(stringResource(R.string.cloud_field_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth().testTag(CloudKeyTags.FIELD),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val result = try { host.saveKey(pasted) } catch (e: java.io.IOException) { null }
                    pasted = "" // cleared the moment it is saved or refused: it never stays in the field
                    message = when (result) {
                        KeyCheck.OK -> R.string.cloud_saved
                        KeyCheck.EMPTY -> R.string.cloud_check_empty
                        KeyCheck.TOO_SHORT -> R.string.cloud_check_short
                        KeyCheck.TOO_LONG -> R.string.cloud_check_long
                        KeyCheck.BAD_CHARACTERS -> R.string.cloud_check_chars
                        KeyCheck.BAD_PREFIX -> R.string.cloud_check_prefix
                        null -> R.string.cloud_save_failed
                    }
                    if (result == KeyCheck.OK) {
                        clearClipboard(context) // clears, never reads
                        keySet = true
                    }
                    snapshot = host.spend.snapshot()
                    onChanged()
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag(CloudKeyTags.SAVE),
            ) { Text(stringResource(R.string.cloud_save)) }
            OutlinedButton(
                onClick = {
                    host.removeKey()
                    pasted = ""
                    keySet = false
                    switchOn = false
                    message = R.string.cloud_removed
                    onChanged()
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag(CloudKeyTags.REMOVE),
            ) { Text(stringResource(R.string.cloud_remove)) }
        }
        message?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it == R.string.cloud_saved || it == R.string.cloud_removed) Pt.colors.muted else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(CloudKeyTags.MESSAGE).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        // The switch: off by default, off after a restart, off whenever stand mode is on.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cloud_switch_label), style = MaterialTheme.typography.titleSmall, color = Pt.colors.ink)
                Text(stringResource(R.string.cloud_switch_note), style = MaterialTheme.typography.bodySmall, color = Pt.colors.muted)
            }
            Switch(
                checked = switchOn && keySet && !standOn,
                enabled = keySet && !standOn,
                onCheckedChange = { want ->
                    if (want) switchOn = host.switch.turnOn() else { host.switch.turnOff(); switchOn = false }
                    onChanged()
                },
                modifier = Modifier.testTag(CloudKeyTags.SWITCH),
            )
        }

        // The model: a choice from the pinned list, never free text.
        Text(stringResource(R.string.cloud_model_heading), style = MaterialTheme.typography.titleSmall, color = Pt.colors.ink, modifier = Modifier.semantics { heading() })
        for (m in CloudModels.ALL) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = model == m.id, role = Role.RadioButton, onClick = { host.choose(m.id); model = m.id; onChanged() })
                    .testTag(CloudKeyTags.model(m.id)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = model == m.id, onClick = null)
                Text(m.label, style = MaterialTheme.typography.bodyMedium, color = Pt.colors.ink, modifier = Modifier.padding(start = 12.dp))
            }
        }

        // Spend: the caps are code constants; the screen shows them and can only reset the total, on purpose.
        Text(stringResource(R.string.cloud_spend_heading), style = MaterialTheme.typography.titleSmall, color = Pt.colors.ink, modifier = Modifier.semantics { heading() })
        Text(
            if (snapshot.ledgerOk) {
                stringResource(
                    R.string.cloud_spend_line,
                    CloudSpend.usd(snapshot.sessionSpentMicros), CloudSpend.usd(snapshot.sessionCapMicros),
                    CloudSpend.usd(snapshot.totalSpentMicros), CloudSpend.usd(snapshot.totalCapMicros),
                )
            } else {
                stringResource(R.string.cloud_spend_missing)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Pt.colors.muted,
            modifier = Modifier.testTag(CloudKeyTags.SPEND),
        )
        OutlinedButton(
            onClick = { snapshot = host.spend.snapshot(); confirmReset = true },
            modifier = Modifier.heightIn(min = 48.dp).testTag(CloudKeyTags.RESET),
        ) { Text(stringResource(R.string.cloud_reset)) }

        Text(stringResource(R.string.cloud_keystore_note), style = MaterialTheme.typography.bodySmall, color = Pt.colors.muted)
        Text(stringResource(R.string.cloud_credit_note), style = MaterialTheme.typography.bodySmall, color = Pt.colors.muted)
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.cloud_reset_title)) },
            text = {
                // The amount is shown before anything is zeroed (security R-cloud-9).
                Text(
                    stringResource(
                        R.string.cloud_reset_body,
                        CloudSpend.usd(snapshot.totalSpentMicros), CloudSpend.usd(snapshot.totalCapMicros),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { host.spend.reset(); snapshot = host.spend.snapshot(); confirmReset = false; onChanged() },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CloudKeyTags.RESET_CONFIRM),
                ) { Text(stringResource(R.string.cloud_reset_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmReset = false },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CloudKeyTags.RESET_CANCEL),
                ) { Text(stringResource(R.string.cloud_reset_cancel)) }
            },
        )
    }
}

/** Clears the primary clip after a key was saved (API 28 and later); the app never reads the clipboard. */
internal fun clearClipboard(context: android.content.Context) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            context.getSystemService(android.content.ClipboardManager::class.java)?.clearPrimaryClip()
        }
    } catch (e: RuntimeException) {
        // The platform refused; the screen already tells the staff member to clear it themselves.
    }
}
