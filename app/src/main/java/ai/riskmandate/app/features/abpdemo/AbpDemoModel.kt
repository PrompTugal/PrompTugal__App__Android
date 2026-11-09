package ai.riskmandate.app.features.abpdemo

/**
 * Pure Kotlin (no Android types), so the order of the steps and the auto-play are tested on the JVM.
 * The words live in res/values/feature_abpdemo.xml; the screen maps each [Layer] and [Strength] to them.
 */

/** The six layers, outermost first, in the order the demo walks them. */
enum class Layer { BROWSER, MODEL, HOOK, PROXY, HARNESS, DEVICE }

/**
 * How strongly a layer holds a rule, weakest first. The five words come from the task brief; the
 * riskmandate.ai wording for the first three is "told", "checked", "held" (see docs in the status page).
 */
enum class Strength { GUIDANCE, SOFT, FIRM, STRONG, FLOOR }

/** One screen of the walk-through. [layer] is null for the opening step, which only sets the scene. */
data class DemoStep(val index: Int, val layer: Layer?) {
    /** The strength of the layer that fires at this step, null for the opening step. */
    val strength: Strength? get() = layer?.let { STRENGTH_OF.getValue(it) }
}

val STRENGTH_OF: Map<Layer, Strength> = mapOf(
    Layer.BROWSER to Strength.GUIDANCE,
    Layer.MODEL to Strength.SOFT,
    Layer.HOOK to Strength.FIRM,
    Layer.PROXY to Strength.STRONG,
    Layer.HARNESS to Strength.STRONG,
    Layer.DEVICE to Strength.FLOOR,
)

/** Step 0 is the scenario; steps 1 to 6 are the layers, in [Layer] order. */
val STEPS: List<DemoStep> =
    listOf(DemoStep(0, null)) + Layer.entries.mapIndexed { i, layer -> DemoStep(i + 1, layer) }

/** Time each step stays on screen in auto-play: long enough to read from two metres. */
const val AUTO_PLAY_STEP_MS = 7_000L

/**
 * The position in the walk-through and whether auto-play is on. Immutable: every move returns a new value.
 * Next and Back stop at the ends; auto-play ([tick]) wraps from the last step to the first, so the stand
 * loops without anyone touching it.
 */
data class DemoPlayer(val index: Int = 0, val autoPlay: Boolean = false) {
    init { require(index in STEPS.indices) { "step $index out of range" } }

    val step: DemoStep get() = STEPS[index]
    val canGoBack: Boolean get() = index > 0
    val canGoNext: Boolean get() = index < STEPS.lastIndex

    fun next() = if (canGoNext) copy(index = index + 1) else this
    fun back() = if (canGoBack) copy(index = index - 1) else this

    /** A person pressing Next or Back takes over from auto-play. */
    fun nextByHand() = next().copy(autoPlay = false)
    fun backByHand() = back().copy(autoPlay = false)

    /** One auto-play interval has passed. Does nothing unless auto-play is on. */
    fun tick() = if (!autoPlay) this else copy(index = if (canGoNext) index + 1 else 0)

    fun toggleAutoPlay() = copy(autoPlay = !autoPlay)

    /** The layers that have already fired (lit and done) at this step. */
    fun layersDone(): List<Layer> = Layer.entries.filter { l -> STEPS.first { it.layer == l }.index < index }
}
