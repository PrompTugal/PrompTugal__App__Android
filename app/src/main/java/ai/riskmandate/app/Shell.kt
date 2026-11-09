package ai.riskmandate.app

import ai.riskmandate.app.ui.Kicker
import ai.riskmandate.app.ui.UiTokens
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * One destination in the navigation: a built-in screen or a summit feature. The icon is a line drawable
 * ([iconRes]) or, when a feature supplies its own, a [vector] (Feature.icon) which wins.
 */
data class NavItem(
    val route: String,
    @StringRes val title: Int,
    @DrawableRes val iconRes: Int,
    /** A short label shown as a pill on the item ("New"), from Feature.homeBadge(). */
    @StringRes val badge: Int? = null,
    val vector: ImageVector? = null,
)

/** A titled group of destinations (the title is null for an untitled group). */
data class NavSection(@StringRes val title: Int?, val items: List<NavItem>)

object ShellTags {
    const val SIDEBAR = "shell_sidebar"
    const val DRAWER_SHEET = "shell_drawer_sheet"
    const val MENU = "shell_menu"
    const val TOP_TITLE = "shell_top_title"
    const val COLLAPSE = "shell_collapse"
    const val HOME = "shell_home"
    fun item(route: String) = "nav_$route"
    fun pill(route: String) = "nav_pill_$route"
}

/** Wide enough for a permanent sidebar: 840dp, the same width the screens use for two columns. */
val SidebarMinWidth = UiTokens.WideMinWidth
private val SidebarWidth = 300.dp
private val RailWidth = 72.dp
private val PanelInset = 12.dp

/**
 * The app's frame (docs/design/android-theme.md, "Shell"). Wide windows get a permanent sidebar panel that
 * can fold to an icon rail ([sidebarCollapsed]); narrower ones a top bar with a menu button and a modal
 * drawer holding the same panel. [content] is the selected destination.
 *
 * Back: from any destination except home, Back goes home ([atHome], [onBackHome]); with the drawer open,
 * Back closes the drawer first (that handler is registered last, so it wins).
 * Insets: the shell applies them to its own bars and consumes them for [content], whose screens keep
 * applying their own (consumed insets are not applied twice).
 */
@Composable
fun AppShell(
    sections: List<NavSection>,
    selected: String,
    @StringRes currentTitle: Int,
    atHome: Boolean,
    onSelect: (String) -> Unit,
    onBackHome: () -> Unit,
    brandLine: String = "",
    sidebarCollapsed: Boolean = false,
    onSidebarCollapsedChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BackHandler(enabled = !atHome, onBack = onBackHome)
        if (maxWidth >= SidebarMinWidth) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .width((if (sidebarCollapsed) RailWidth else SidebarWidth) + PanelInset)
                        .fillMaxHeight()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom),
                        )
                        .padding(start = PanelInset, top = PanelInset, bottom = PanelInset),
                ) {
                    SidebarPanel(
                        sections, selected, onSelect, brandLine, sidebarCollapsed,
                        onToggleCollapsed = { onSidebarCollapsedChange(!sidebarCollapsed) },
                        modifier = Modifier.testTag(ShellTags.SIDEBAR).fillMaxHeight(),
                    )
                }
                Box(Modifier.weight(1f).fillMaxHeight().consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))) {
                    CompositionLocalProvider(LocalSidebarVisible provides true) { content() }
                }
            }
        } else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(Modifier.testTag(ShellTags.DRAWER_SHEET), drawerContainerColor = Pt.colors.card) {
                        SidebarContent(
                            sections, selected,
                            onSelect = { route -> scope.launch { drawer.close() }; onSelect(route) },
                            brandLine = brandLine, collapsed = false, onToggleCollapsed = null,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                },
            ) {
                val edges = WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                Column(Modifier.fillMaxSize()) {
                    TopBar(
                        title = currentTitle,
                        onMenu = { scope.launch { drawer.open() } },
                        onHome = if (atHome) null else onBackHome,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(edges)),
                    )
                    Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.safeDrawing.only(edges))) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(@StringRes title: Int, onMenu: () -> Unit, onHome: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu, modifier = Modifier.heightIn(min = 48.dp).testTag(ShellTags.MENU)) {
            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.nav_menu), tint = Pt.colors.ink)
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = Pt.colors.ink,
            modifier = Modifier.padding(start = 4.dp).weight(1f).testTag(ShellTags.TOP_TITLE).semantics { heading() },
        )
        // A visible way home on every screen but Home itself (stand mode may pin the back gesture off).
        if (onHome != null) {
            IconButton(onClick = onHome, modifier = Modifier.heightIn(min = 48.dp).testTag(ShellTags.HOME)) {
                Icon(painterResource(R.drawable.ic_nav_home), contentDescription = stringResource(R.string.nav_home_action), tint = Pt.colors.red)
            }
        }
    }
}

/** The tablet sidebar: a card-coloured panel with a hairline and 14 dp corners. */
@Composable
private fun SidebarPanel(
    sections: List<NavSection>,
    selected: String,
    onSelect: (String) -> Unit,
    brandLine: String,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(if (collapsed) RailWidth else SidebarWidth),
        shape = RoundedCornerShape(UiTokens.CardRadius),
        color = Pt.colors.card,
        border = BorderStroke(1.dp, Pt.colors.rule.copy(alpha = 0.7f)),
    ) {
        SidebarContent(sections, selected, onSelect, brandLine, collapsed, onToggleCollapsed, Modifier.fillMaxHeight())
    }
}

/**
 * The brand block and the sectioned destinations, scrolling on its own so 200% font scale never cuts it
 * off. A navigation landmark for TalkBack. [collapsed] shows icons only; [onToggleCollapsed] null hides
 * the fold button (the phone drawer).
 */
@Composable
private fun SidebarContent(
    sections: List<NavSection>,
    selected: String,
    onSelect: (String) -> Unit,
    brandLine: String,
    collapsed: Boolean,
    onToggleCollapsed: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (collapsed) 8.dp else 12.dp, vertical = 12.dp)
            .semantics { isTraversalGroup = true },
        horizontalAlignment = if (collapsed) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (onToggleCollapsed != null) {
            val label = stringResource(if (collapsed) R.string.nav_expand else R.string.nav_collapse)
            IconButton(
                onClick = onToggleCollapsed,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ShellTags.COLLAPSE).align(if (collapsed) Alignment.CenterHorizontally else Alignment.End),
            ) { Icon(painterResource(R.drawable.ic_nav_panel), contentDescription = label, tint = Pt.colors.muted) }
        }
        BrandBlock(brandLine, collapsed)
        Spacer(Modifier.height(8.dp))
        for (section in sections) {
            if (section.items.isEmpty()) continue
            if (section.title != null && !collapsed) {
                Text(
                    stringResource(section.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = Pt.colors.muted,
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                )
            } else {
                Spacer(Modifier.height(if (collapsed) 8.dp else 4.dp))
            }
            for (item in section.items) NavRow(item, item.route == selected, collapsed) { onSelect(item.route) }
        }
    }
}

@Composable
private fun BrandBlock(brandLine: String, collapsed: Boolean) {
    val mark = @Composable { size: Int ->
        Box(
            Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(Pt.colors.ink),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.shell_brand_mark),
                color = MaterialTheme.colorScheme.background,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.4f).sp,
            )
        }
    }
    if (collapsed) {
        mark(40)
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            mark(56)
            Column {
                Kicker(stringResource(R.string.app_name))
                if (brandLine.isNotBlank()) Text(brandLine, style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
            }
        }
    }
}

/** A 48 dp+ destination row: 24 dp line icon, label, optional pill. Selected: red at 12%, label semibold. */
@Composable
private fun NavRow(item: NavItem, selected: Boolean, collapsed: Boolean, onClick: () -> Unit) {
    val red = Pt.colors.red
    val tint = if (selected) red else Pt.colors.ink
    val title = stringResource(item.title)
    val badge = item.badge?.let { stringResource(it) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) red.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(ShellTags.item(item.route))
            .semantics(mergeDescendants = true) {
                if (collapsed) contentDescription = if (badge != null) "$title, $badge" else title
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(12.dp),
    ) {
        if (item.vector != null) Icon(item.vector, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        else Icon(painterResource(item.iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        if (!collapsed) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) BadgePill(badge, Modifier.testTag(ShellTags.pill(item.route)))
        }
    }
}

/** The text badge ("New") as a capsule: a CountPill needs a number, a feature badge is a word. */
@Composable
internal fun BadgePill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Pt.colors.red,
        modifier = modifier
            .background(Pt.colors.red.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
