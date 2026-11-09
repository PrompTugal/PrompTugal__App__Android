package ai.riskmandate.app

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** One destination in the navigation: a built-in screen or a summit feature. */
data class NavItem(
    val route: String,
    @StringRes val title: Int,
    val icon: ImageVector,
    /** A short label shown as a pill on the item ("New"), from Feature.homeBadge(). */
    @StringRes val badge: Int? = null,
)

/** A titled group of destinations (the title is null for an untitled group). */
data class NavSection(@StringRes val title: Int?, val items: List<NavItem>)

object ShellTags {
    const val SIDEBAR = "shell_sidebar"
    const val DRAWER_SHEET = "shell_drawer_sheet"
    const val MENU = "shell_menu"
    const val TOP_TITLE = "shell_top_title"
    fun item(route: String) = "nav_$route"
    fun pill(route: String) = "nav_pill_$route"
}

/** Wide enough for a permanent sidebar: 10" tablets in portrait, 7" and 10" tablets in landscape. */
val SidebarMinWidth = 720.dp
private val SidebarWidth = 280.dp

/**
 * The app's frame. Wide windows get a permanent sidebar; narrow ones a top bar with a menu button and
 * a modal drawer holding the same destinations. [content] is the selected destination.
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
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= SidebarMinWidth) {
            BackHandler(enabled = !atHome, onBack = onBackHome)
            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    sections, selected, onSelect,
                    modifier = Modifier
                        .testTag(ShellTags.SIDEBAR)
                        .width(SidebarWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom),
                        ),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxHeight().consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))) {
                    content()
                }
            }
        } else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            BackHandler(enabled = !atHome, onBack = onBackHome)
            BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(Modifier.testTag(ShellTags.DRAWER_SHEET)) {
                        Sidebar(
                            sections, selected,
                            onSelect = { route -> scope.launch { drawer.close() }; onSelect(route) },
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
private fun TopBar(@StringRes title: Int, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu, modifier = Modifier.heightIn(min = 48.dp).testTag(ShellTags.MENU)) {
            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.nav_menu))
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp).testTag(ShellTags.TOP_TITLE).semantics { heading() },
        )
    }
}

/** The brand block and the sectioned destinations. Scrolls, so 200% font scale never cuts it off. */
@Composable
private fun Sidebar(
    sections: List<NavSection>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
        for (section in sections) {
            if (section.items.isEmpty()) continue
            if (section.title != null) {
                Text(
                    stringResource(section.title),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
            for (item in section.items) {
                NavigationDrawerItem(
                    label = { Text(stringResource(item.title)) },
                    selected = item.route == selected,
                    onClick = { onSelect(item.route) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    badge = item.badge?.let { b -> { Pill(stringResource(b), Modifier.testTag(ShellTags.pill(item.route))) } },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = scheme.primaryContainer,
                        selectedTextColor = scheme.onPrimaryContainer,
                        selectedIconColor = scheme.onPrimaryContainer,
                        unselectedTextColor = scheme.onSurface,
                        unselectedIconColor = scheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ShellTags.item(item.route)),
                )
            }
        }
    }
}

/** A small rounded label: the "count pill" on a sidebar item. */
@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
