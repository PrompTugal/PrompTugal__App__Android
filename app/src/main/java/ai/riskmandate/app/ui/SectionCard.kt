package ai.riskmandate.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A titled block: the first version of [TitledCard], kept so existing screens keep compiling. New code
 * uses [TitledCard] (it adds a kicker and an accessory). [large] makes the title 36sp bold (the Lab's lane
 * headers).
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) = TitledCard(title, modifier, large = large, content = content)
