package ai.riskmandate.app.features.releases

import ai.riskmandate.app.BuildConfig
import ai.riskmandate.app.Browser
import ai.riskmandate.app.Links
import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The image source for the screen below it; the app's assets unless a test provides a fixture. */
internal val LocalArticleImages = androidx.compose.runtime.staticCompositionLocalOf<ArticleImages?> { null }

/** Test tags (architecture rule: `<id>_<thing>`). */
object ReleasesTags {
    const val LIST = "releases_list"
    const val READER = "releases_reader"
    const val EMPTY = "releases_empty"
    const val ALL = "releases_all"
    const val SAMPLE = "releases_sample"
    const val IMAGE = "releases_image"
    fun item(dir: String) = "releases_item_$dir"
}

/**
 * Feature F, "What's new": one article per release from `assets/releases/` (format and decisions: README.md).
 * Holds no state: everything lives in the composition. Uses only Compose locals, never the Activity.
 */
object ReleasesFeature : Feature {
    override val id = "releases"
    override val title = R.string.feature_releases_title
    override val enabled = true
    override val icon: ImageVector get() = Icons.Filled.Info
    override val kicker = R.string.feature_releases_kicker
    override val summary = R.string.feature_releases_summary

    /** "New" on the home button while this version's notes are unseen; read off the main thread. */
    @Composable
    @StringRes
    override fun homeBadge(): Int? {
        val appContext = LocalContext.current.applicationContext
        val unseen by produceState(false) {
            value = withContext(Dispatchers.IO) { ReleasesNotice.shouldOffer(appContext, BuildConfig.VERSION_CODE) }
        }
        return if (unseen) R.string.feature_releases_badge else null
    }

    @Composable
    override fun Content(context: FeatureContext) {
        // The composition's own context (the Activity) opens links, so a Custom Tab belongs to the app's task;
        // the application context only for assets and prefs.
        val hostContext = LocalContext.current
        val appContext = hostContext.applicationContext
        val language = LocalConfiguration.current.locales[0].language
        var articles by remember { mutableStateOf<List<Article>?>(null) }
        LaunchedEffect(language) { articles = withContext(Dispatchers.IO) { ReleaseStore.load(appContext, language) } }
        // Opening the screen is "seeing" this version's notes: the home screen stops offering them.
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ReleasesNotice.markSeen(appContext, BuildConfig.VERSION_CODE) } }
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        ReleasesScreen(
            articles = articles,
            selectedDir = selected,
            onSelect = { selected = it },
            onLink = { url -> Browser.open(hostContext, url) },
        )
    }
}

private val TwoPaneMinWidth = 720.dp
private val ListPaneWidth = 340.dp
private val ReaderMaxWidth = 720.dp
private val MinTouch = 48.dp

/**
 * The screen without the loading: a list, a reader, or both side by side (landscape and at least 720dp wide).
 * [articles] null = still loading. [selectedDir] null = the list (phone) or the newest article (two panes).
 * Links go to [onLink] only when `Links.isAllowed` says so; any other link is shown as plain text.
 */
@Composable
fun ReleasesScreen(
    articles: List<Article>?,
    selectedDir: String?,
    onSelect: (String?) -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    images: ArticleImages? = null,
) {
    val assetImages = AssetArticleImages(LocalContext.current.applicationContext.assets)
    androidx.compose.runtime.CompositionLocalProvider(LocalArticleImages provides (images ?: assetImages)) {
        ReleasesScreenBody(articles, selectedDir, onSelect, onLink, modifier)
    }
}

@Composable
private fun ReleasesScreenBody(
    articles: List<Article>?,
    selectedDir: String?,
    onSelect: (String?) -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier,
) {
    if (articles == null) return
    if (articles.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.feature_releases_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ReleasesTags.EMPTY),
            )
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TwoPaneMinWidth && maxWidth > maxHeight
        val current = articles.firstOrNull { it.dir == selectedDir }
        if (twoPane) {
            val shown = current ?: articles.first()
            Row(Modifier.fillMaxSize()) {
                ArticleList(articles, shown.dir, onSelect = { onSelect(it) }, modifier = Modifier.width(ListPaneWidth))
                VerticalDivider()
                ArticleReader(shown, onLink, Modifier.weight(1f))
            }
        } else if (current == null) {
            ArticleList(articles, null, onSelect = { onSelect(it) }, modifier = Modifier.fillMaxSize())
        } else {
            // Back steps from the reader to the list; the host's handler then takes the user home.
            BackHandler { onSelect(null) }
            Column(Modifier.fillMaxSize()) {
                TextButton(
                    onClick = { onSelect(null) },
                    modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = MinTouch).testTag(ReleasesTags.ALL),
                ) { Text(stringResource(R.string.feature_releases_all)) }
                ArticleReader(current, onLink, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ArticleList(articles: List<Article>, highlighted: String?, onSelect: (String) -> Unit, modifier: Modifier) {
    LazyColumn(modifier.testTag(ReleasesTags.LIST), contentPadding = PaddingValues(8.dp)) {
        items(articles, key = { it.dir }) { a -> ArticleRow(a, a.dir == highlighted) { onSelect(a.dir) } }
    }
}

@Composable
private fun ArticleRow(a: Article, highlighted: Boolean, onClick: () -> Unit) {
    val bg = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = MinTouch)
            .padding(12.dp)
            .testTag(ReleasesTags.item(a.dir)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        a.firstImage?.let { img ->
            ArticleImage(a.dir, img.path, thumbnail = true, contentDescription = null, modifier = Modifier.size(width = 72.dp, height = 96.dp).clip(RoundedCornerShape(8.dp)))
        }
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            DateLine(a)
            if (a.summary.isNotBlank()) {
                Text(a.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
            }
        }
    }
}

@Composable
private fun DateLine(a: Article) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(a.date, locale) { a.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
    val text = if (a.sample) "$date · ${stringResource(R.string.feature_releases_sample_badge)}" else date
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ArticleReader(a: Article, onLink: (String) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier.fillMaxSize().testTag(ReleasesTags.READER),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Column(Modifier.widthIn(max = ReaderMaxWidth).fillMaxWidth()) {
                Text(a.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
                DateLine(a)
                if (a.sample) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.feature_releases_sample_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(12.dp)
                            .testTag(ReleasesTags.SAMPLE),
                    )
                }
            }
        }
        items(a.blocks.size, key = { "b$it" }) { i ->
            Column(Modifier.widthIn(max = ReaderMaxWidth).fillMaxWidth()) { BlockView(a, a.blocks[i], onLink) }
        }
    }
}

@Composable
private fun BlockView(a: Article, block: Block, onLink: (String) -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    when (block) {
        is Block.Heading -> Text(
            styled(block.spans, onLink),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
            color = onSurface,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        is Block.Paragraph -> Text(styled(block.spans, onLink), style = MaterialTheme.typography.bodyLarge, color = onSurface)
        is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { i, spans ->
                Row {
                    Text(if (block.ordered) "${i + 1}." else "•", style = MaterialTheme.typography.bodyLarge, color = onSurface, modifier = Modifier.widthIn(min = 24.dp))
                    Text(styled(spans, onLink), style = MaterialTheme.typography.bodyLarge, color = onSurface, modifier = Modifier.weight(1f))
                }
            }
        }
        is Block.Image -> Column {
            val fallback = stringResource(R.string.feature_releases_image_fallback)
            ArticleImage(a.dir, block.path, thumbnail = false, contentDescription = block.caption.ifBlank { fallback }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
            if (block.caption.isNotBlank()) {
                Text(block.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Inline spans to an AnnotatedString. A link is clickable only when `Links.isAllowed`; otherwise its text is plain. */
@Composable
private fun styled(spans: List<Span>, onLink: (String) -> Unit): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        for (s in spans) when (s) {
            is Span.Text -> withStyle(
                SpanStyle(
                    fontWeight = if (s.bold) FontWeight.Bold else null,
                    fontStyle = if (s.italic) FontStyle.Italic else null,
                    fontFamily = if (s.code) FontFamily.Monospace else null,
                ),
            ) { append(s.text) }
            is Span.Link ->
                if (Links.isAllowed(s.url)) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = s.url,
                            styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                            linkInteractionListener = { onLink(s.url) },
                        ),
                    ) { append(s.text) }
                } else {
                    append(s.text)
                }
        }
    }
}

/** An image from the article's folder, decoded off the main thread and scaled. Nothing is drawn if it is missing. */
@Composable
private fun ArticleImage(dir: String, path: String, thumbnail: Boolean, contentDescription: String?, modifier: Modifier) {
    val images = LocalArticleImages.current
    var bitmap by remember(dir, path, thumbnail) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(dir, path, thumbnail) {
        bitmap = withContext(Dispatchers.IO) { images?.let { ReleaseStore.image(it, dir, path, if (thumbnail) 240 else 1400)?.asImageBitmap() } }
    }
    val bmp = bitmap ?: return
    Image(
        bitmap = bmp,
        contentDescription = contentDescription,
        contentScale = if (thumbnail) ContentScale.Crop else ContentScale.FillWidth,
        alignment = Alignment.TopCenter,
        modifier = modifier.testTag(ReleasesTags.IMAGE),
    )
}
