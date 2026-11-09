package ai.riskmandate.app.features.mandate

import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.R
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.FeatureContent
import ai.riskmandate.app.ui.SectionCard
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.UiTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object MandateTags {
    const val LOADING = "mandate_loading"
    const val CHIP = "mandate_chip"
    const val SUMMARY = "mandate_summary"
    const val GAP_STATUS = "mandate_gap_status"
    const val REACH = "mandate_reach"
    const val MANDATE = "mandate_mandate"
    const val GAP = "mandate_gap"
    fun gapItem(key: String) = "mandate_gap_item_$key"
    fun reachItem(key: String) = "mandate_reach_item_$key"
}

@Composable
fun MandateScreen(state: MandateState) {
    val report = state.report
    FeatureContent {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
        Text(stringResource(R.string.mandate_intro), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.mandate_summary, report.reach.size, report.mandate.entries.size, report.gap.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(MandateTags.SUMMARY),
        )
        StatusChip(
            if (report.gapIsEmpty) stringResource(R.string.mandate_chip_none) else stringResource(R.string.mandate_chip_some, report.gap.size),
            if (report.gapIsEmpty) ChipKind.Allowed else ChipKind.Blocked,
            modifier = Modifier.testTag(MandateTags.CHIP),
        )
        // Text, not colour alone, says whether there is a gap.
        Text(
            if (report.gapIsEmpty) stringResource(R.string.mandate_gap_none) else stringResource(R.string.mandate_gap_some, report.gap.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (report.gapIsEmpty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(MandateTags.GAP_STATUS),
        )

        SectionCard(stringResource(R.string.mandate_defs_heading)) {
            Text(stringResource(R.string.mandate_def_reach), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.mandate_def_mandate), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.mandate_def_gap), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.mandate_def_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ReachSection(state)
        MandateSection(state)
        GapSection(report)
        }
    }
}

@Composable
private fun ReachSection(state: MandateState) {
    val reach = state.report.reach
    SectionCard(stringResource(R.string.mandate_reach_heading), Modifier.testTag(MandateTags.REACH)) {
        SubHeading(stringResource(R.string.mandate_reach_permissions))
        val perms = reach.filter { it.kind == ReachKind.PERMISSION }
        if (perms.isEmpty()) Line(stringResource(R.string.mandate_reach_none))
        perms.forEach { Line(permissionText(it), mono = true, tag = MandateTags.reachItem(it.key)) }

        SubHeading(stringResource(R.string.mandate_reach_components))
        val comps = reach.filter { it.kind == ReachKind.COMPONENT }
        if (comps.isEmpty()) Line(stringResource(R.string.mandate_reach_none))
        comps.forEach { Line(componentText(it), mono = true, tag = MandateTags.reachItem(it.key)) }

        SubHeading(stringResource(R.string.mandate_reach_network))
        val connects = reach.filter { it.kind == ReachKind.NETWORK && it.id.startsWith("connects:") }
        if (connects.isEmpty()) Line(stringResource(R.string.mandate_connects_none))
        connects.forEach { Line(stringResource(R.string.mandate_connects, it.id.removePrefix("connects:")), mono = true, tag = MandateTags.reachItem(it.key)) }
        Line(
            if (reach.any { it.key == "network:cleartext" }) stringResource(R.string.mandate_cleartext_on)
            else stringResource(R.string.mandate_cleartext_off),
        )

        SubHeading(stringResource(R.string.mandate_reach_storage))
        val storage = reach.filter { it.kind == ReachKind.STORAGE }
        if (storage.isEmpty()) Line(stringResource(R.string.mandate_storage_none))
        storage.forEach { Line(storageText(it), tag = MandateTags.reachItem(it.key)) }

        SubHeading(stringResource(R.string.mandate_reach_device))
        Line(stringResource(state.device.managementTextRes))
    }
}

@Composable
private fun MandateSection(state: MandateState) {
    val mandate = state.report.mandate
    SectionCard(stringResource(R.string.mandate_mandate_heading), Modifier.testTag(MandateTags.MANDATE)) {
        SubHeading(stringResource(R.string.mandate_mandate_hosts, mandate.port))
        mandate.hosts.forEach { Line(it, mono = true) }
        SubHeading(stringResource(R.string.mandate_mandate_probe_heading))
        state.probes.forEach {
            Line(
                stringResource(if (it.allowed) R.string.mandate_probe_allowed else R.string.mandate_probe_refused, it.url),
                mono = true,
            )
        }
        SubHeading(stringResource(R.string.mandate_mandate_allowed))
        mandate.entries.forEach { Line("${it.id}: ${stringResource(reasonRes(it.reason))}", mono = true) }
        SubHeading(stringResource(R.string.mandate_mandate_managed))
        val unset = stringResource(R.string.mandate_managed_unset)
        mandate.managedKeys.forEach { key ->
            val value = when (key) {
                ManagedConfig.KEY_START_URL -> state.config.startUrl
                ManagedConfig.KEY_DEVICE_LABEL -> state.config.deviceLabel ?: unset
                else -> unset
            }
            Line(stringResource(R.string.mandate_managed_value, key, value), mono = true)
        }
        if (state.config.startUrlRefused) Line(stringResource(R.string.mandate_managed_refused, ManagedConfig.KEY_START_URL))
        Line(stringResource(R.string.mandate_mandate_data))
    }
}

@Composable
private fun GapSection(report: MandateReport) {
    SectionCard(stringResource(R.string.mandate_gap_heading), Modifier.testTag(MandateTags.GAP)) {
        if (report.gapIsEmpty) {
            Line(stringResource(R.string.mandate_gap_none))
        } else {
            Line(stringResource(R.string.mandate_gap_some, report.gap.size))
            report.gap.forEach { item ->
                Line(reachText(item), mono = true, tag = MandateTags.gapItem(item.key), error = true)
            }
        }
    }
}

@Composable
private fun reachText(item: ReachItem): String = when (item.kind) {
    ReachKind.PERMISSION -> permissionText(item)
    ReachKind.COMPONENT -> componentText(item)
    ReachKind.NETWORK ->
        if (item.id.startsWith("connects:")) stringResource(R.string.mandate_connects, item.id.removePrefix("connects:"))
        else stringResource(R.string.mandate_cleartext_on)
    ReachKind.STORAGE -> storageText(item)
}

@Composable
private fun permissionText(item: ReachItem): String =
    stringResource(if (item.granted) R.string.mandate_perm_granted else R.string.mandate_perm_requested, item.id)

@Composable
private fun componentText(item: ReachItem): String {
    val type = stringResource(
        when (item.componentType) {
            ComponentType.SERVICE -> R.string.mandate_component_service
            ComponentType.RECEIVER -> R.string.mandate_component_receiver
            ComponentType.PROVIDER -> R.string.mandate_component_provider
            else -> R.string.mandate_component_activity
        },
    )
    val guard = item.guardedBy?.let { stringResource(R.string.mandate_guard_by, it) } ?: stringResource(R.string.mandate_guard_none)
    return stringResource(R.string.mandate_component_line, type, "${item.id}, $guard")
}

@Composable
private fun storageText(item: ReachItem): String = when {
    item.id == "backup" -> stringResource(R.string.mandate_backup_on)
    item.id.contains('/') -> stringResource(R.string.mandate_storage_file, item.id)
    else -> stringResource(R.string.mandate_storage_dir, item.id, item.count)
}

private fun reasonRes(reason: Reason): Int = when (reason) {
    Reason.OPEN_ALLOWED_PAGES -> R.string.mandate_reason_open_pages
    Reason.OFFLINE_HINT -> R.string.mandate_reason_offline_hint
    Reason.LAUNCHER -> R.string.mandate_reason_launcher
    Reason.INTERNAL_GUARD -> R.string.mandate_reason_internal_guard
    Reason.STAND_SETTINGS -> R.string.mandate_reason_stand_settings
    Reason.SITE_STORAGE -> R.string.mandate_reason_site_storage
    Reason.SITE_CACHE -> R.string.mandate_reason_site_cache
    Reason.SITE_VIEW -> R.string.mandate_reason_site_view
}

@Composable
private fun Heading(text: String, big: Boolean = false) {
    Text(
        text,
        style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Line(text: String, mono: Boolean = false, tag: String? = null, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = if (mono) FontFamily.Monospace else null,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
    )
}
