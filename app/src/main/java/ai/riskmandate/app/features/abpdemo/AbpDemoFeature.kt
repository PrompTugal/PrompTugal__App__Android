package ai.riskmandate.app.features.abpdemo

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Feature B: a native, labelled demonstration of one ABP stopping a planted instruction at six layers. */
object AbpDemoFeature : Feature {
    override val id = "abpdemo"
    override val title = R.string.feature_abpdemo_title
    override val enabled = true

    @Composable
    override fun Content(context: FeatureContext) = AbpDemoScreen(onBack = context.onBack)
}

object AbpDemoTags {
    const val BANNER = "abpdemo_banner"
    const val BACK = "abpdemo_back"
    const val NEXT = "abpdemo_next"
    const val AUTOPLAY = "abpdemo_autoplay"
    const val HOME = "abpdemo_home"
    const val DETAIL = "abpdemo_detail"
    const val SCENARIO = "abpdemo_scenario"
    const val STEP = "abpdemo_step"
    fun layer(l: Layer) = "abpdemo_layer_${l.name.lowercase()}"
}

private data class LayerText(@StringRes val name: Int, @StringRes val does: Int, @StringRes val fires: Int, @StringRes val note: Int)

private fun Layer.text() = when (this) {
    Layer.BROWSER -> LayerText(R.string.abpdemo_layer_browser, R.string.abpdemo_does_browser, R.string.abpdemo_fires_browser, R.string.abpdemo_note_browser)
    Layer.MODEL -> LayerText(R.string.abpdemo_layer_model, R.string.abpdemo_does_model, R.string.abpdemo_fires_model, R.string.abpdemo_note_model)
    Layer.HOOK -> LayerText(R.string.abpdemo_layer_hook, R.string.abpdemo_does_hook, R.string.abpdemo_fires_hook, R.string.abpdemo_note_hook)
    Layer.PROXY -> LayerText(R.string.abpdemo_layer_proxy, R.string.abpdemo_does_proxy, R.string.abpdemo_fires_proxy, R.string.abpdemo_note_proxy)
    Layer.HARNESS -> LayerText(R.string.abpdemo_layer_harness, R.string.abpdemo_does_harness, R.string.abpdemo_fires_harness, R.string.abpdemo_note_harness)
    Layer.DEVICE -> LayerText(R.string.abpdemo_layer_device, R.string.abpdemo_does_device, R.string.abpdemo_fires_device, R.string.abpdemo_note_device)
}

@StringRes
private fun Strength.label() = when (this) {
    Strength.GUIDANCE -> R.string.abpdemo_strength_guidance
    Strength.SOFT -> R.string.abpdemo_strength_soft
    Strength.FIRM -> R.string.abpdemo_strength_firm
    Strength.STRONG -> R.string.abpdemo_strength_strong
    Strength.FLOOR -> R.string.abpdemo_strength_floor
}

@Composable
fun AbpDemoScreen(onBack: () -> Unit, initial: DemoPlayer = DemoPlayer()) {
    // Saved, so turning the tablet round does not restart the walk-through.
    var index by rememberSaveable { mutableIntStateOf(initial.index) }
    var auto by rememberSaveable { mutableStateOf(initial.autoPlay) }
    val player = DemoPlayer(index, auto)
    fun set(p: DemoPlayer) { index = p.index; auto = p.autoPlay }

    // Auto-play: one tick per interval; restarts whenever the step or the mode changes.
    LaunchedEffect(index, auto) {
        if (auto) {
            delay(AUTO_PLAY_STEP_MS)
            set(DemoPlayer(index, auto).tick())
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize()) {
        Banner()
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Diagram(player, Modifier.weight(0.42f))
            Detail(player, Modifier.weight(0.58f))
        }
        Controls(player, onBack, ::set)
    }
    }
}

/** The permanent label. Always on screen, never dismissable, at the top of every step. */
@Composable
private fun Banner() {
    Surface(color = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) {
        Text(
            stringResource(R.string.abpdemo_banner),
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).testTag(AbpDemoTags.BANNER),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Diagram(player: DemoPlayer, modifier: Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.feature_abpdemo_title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.abpdemo_subtitle), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val done = player.layersDone()
        Layer.entries.forEachIndexed { i, layer ->
            val state = when {
                player.step.layer == layer -> LayerState.NOW
                layer in done -> LayerState.DONE
                else -> LayerState.WAITING
            }
            LayerRow(i + 1, layer, state, Modifier.weight(1f))
        }
    }
}

private enum class LayerState { DONE, NOW, WAITING }

/** One row of the six-layer diagram. Lit when it is the current step; the state is also written out, not only coloured. */
@Composable
private fun LayerRow(number: Int, layer: Layer, state: LayerState, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (state) {
            LayerState.NOW -> scheme.primaryContainer
            LayerState.DONE -> scheme.surfaceVariantOrSurface()
            LayerState.WAITING -> scheme.surface
        },
        tween(400),
    )
    val stateText = stringResource(
        when (state) {
            LayerState.DONE -> R.string.abpdemo_layer_state_done
            LayerState.NOW -> R.string.abpdemo_layer_state_now
            LayerState.WAITING -> R.string.abpdemo_layer_state_waiting
        },
    )
    val name = stringResource(layer.text().name)
    val rowCd = stringResource(R.string.abpdemo_layer_cd, number, name, stateText)
    Surface(
        modifier.fillMaxWidth().testTag(AbpDemoTags.layer(layer)).semantics(mergeDescendants = true) {
            contentDescription = rowCd
        },
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(if (state == LayerState.NOW) 4.dp else 1.dp, if (state == LayerState.NOW) scheme.primary else scheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = scheme.primary, modifier = Modifier.width(40.dp))
            Text(
                name, Modifier.weight(1f), fontSize = 24.sp,
                fontWeight = if (state == LayerState.NOW) FontWeight.Bold else FontWeight.Normal,
                color = scheme.onSurface,
            )
            Text(stateText, fontSize = 18.sp, color = scheme.onSurfaceVariant)
        }
    }
}

// Material3's surfaceVariant is not set in the brand scheme; fall back on the pale primary container tone.
private fun androidx.compose.material3.ColorScheme.surfaceVariantOrSurface() = surfaceVariant

@Composable
private fun Detail(player: DemoPlayer, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(AbpDemoTags.DETAIL),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val layer = player.step.layer
        if (layer == null) Scenario() else LayerDetail(layer, player.step.strength!!)
    }
}

@Composable
private fun Scenario() {
    Column(Modifier.testTag(AbpDemoTags.SCENARIO), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading(R.string.abpdemo_scenario_title)
        Text(stringResource(R.string.abpdemo_scenario_body), fontSize = 28.sp, lineHeight = 38.sp)
        Label(R.string.abpdemo_rule_label)
        RuleChip()
        Text(stringResource(R.string.abpdemo_scene_hint), fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.abpdemo_sources), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RuleChip() {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            stringResource(R.string.abpdemo_rule_text),
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun LayerDetail(layer: Layer, strength: Strength) {
    val t = layer.text()
    Heading(t.name)
    Label(R.string.abpdemo_fires_label)
    Text(stringResource(t.does), fontSize = 26.sp, lineHeight = 36.sp)
    Label(R.string.abpdemo_rule_fires_label)
    RuleChip()
    Text(stringResource(t.fires), fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
    Label(R.string.abpdemo_strength_label)
    StrengthMeter(strength)
    Text(stringResource(t.note), fontSize = 22.sp, lineHeight = 30.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Five notches, filled up to the strength, with the word beside it: never colour alone. */
@Composable
private fun StrengthMeter(strength: Strength) {
    val total = Strength.entries.size
    val filled = strength.ordinal + 1
    val word = stringResource(strength.label())
    val cd = stringResource(R.string.abpdemo_strength_cd, word, filled, total)
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val on = i < filled
            Box(
                Modifier.padding(end = 6.dp).size(width = 44.dp, height = 22.dp)
                    .background(
                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(5.dp),
                    ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(word, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Heading(@StringRes res: Int) = Text(
    stringResource(res), Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
)

@Composable
private fun Label(@StringRes res: Int) = Text(
    stringResource(res), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
)

@Composable
private fun Controls(player: DemoPlayer, onBack: () -> Unit, set: (DemoPlayer) -> Unit) {
    val big = Modifier.height(64.dp)
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack, modifier = big.testTag(AbpDemoTags.HOME)) {
                Text(stringResource(R.string.abpdemo_home), fontSize = 22.sp)
            }
            Text(
                stringResource(R.string.abpdemo_step_of, player.index + 1, STEPS.size),
                Modifier.weight(1f).testTag(AbpDemoTags.STEP),
                fontSize = 22.sp,
            )
            OutlinedButton(onClick = { set(player.backByHand()) }, enabled = player.canGoBack, modifier = big.testTag(AbpDemoTags.BACK)) {
                Text(stringResource(R.string.abpdemo_back), fontSize = 24.sp)
            }
            Button(onClick = { set(player.nextByHand()) }, enabled = player.canGoNext, modifier = big.testTag(AbpDemoTags.NEXT)) {
                Text(stringResource(R.string.abpdemo_next), fontSize = 24.sp)
            }
            Button(
                onClick = { set(player.toggleAutoPlay()) },
                modifier = big.testTag(AbpDemoTags.AUTOPLAY),
                colors = if (player.autoPlay) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) else ButtonDefaults.buttonColors(),
            ) {
                Text(stringResource(if (player.autoPlay) R.string.abpdemo_autoplay_stop else R.string.abpdemo_autoplay_start), fontSize = 24.sp)
            }
        }
    }
}
